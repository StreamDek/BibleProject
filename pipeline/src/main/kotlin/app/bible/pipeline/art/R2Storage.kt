package app.bible.pipeline.art

import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Where published files go: Cloudflare R2 in production, or a local folder to try things out. */
interface PublishTarget {
    fun exists(key: String): Boolean
    fun put(key: String, bytes: ByteArray, contentType: String, cacheControl: String)
    val description: String
}

class LocalTarget(private val root: File) : PublishTarget {
    override val description = "folder ${root.absolutePath}"
    override fun exists(key: String) = File(root, key).exists()
    override fun put(key: String, bytes: ByteArray, contentType: String, cacheControl: String) {
        File(root, key).apply { parentFile.mkdirs() }.writeBytes(bytes)
    }
}

/**
 * Cloudflare R2 through its S3-compatible API, signed with AWS Signature Version 4. Credentials come from the
 * environment: R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY and R2_BUCKET.
 */
class R2Target(
    private val accountId: String,
    private val bucket: String,
    private val accessKey: String,
    private val secretKey: String,
) : PublishTarget {
    private val host = "$accountId.r2.cloudflarestorage.com"
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    override val description = "R2 bucket $bucket"

    override fun exists(key: String): Boolean {
        val res = send("HEAD", key, ByteArray(0), emptyMap())
        return when (res.statusCode()) {
            200 -> true
            404 -> false
            else -> throw IllegalStateException("R2 HEAD $key returned ${res.statusCode()}")
        }
    }

    override fun put(key: String, bytes: ByteArray, contentType: String, cacheControl: String) {
        val res = send("PUT", key, bytes, mapOf("content-type" to contentType, "cache-control" to cacheControl))
        if (res.statusCode() !in 200..299) throw IllegalStateException("R2 PUT $key returned ${res.statusCode()}: ${String(res.body()).take(300)}")
    }

    private fun send(method: String, key: String, body: ByteArray, extra: Map<String, String>): HttpResponse<ByteArray> {
        val path = "/$bucket/" + key.split('/').joinToString("/") { SigV4.uriEncode(it) }
        val payloadHash = sha256Hex(body)
        val headers = extra + mapOf("host" to host, "x-amz-content-sha256" to payloadHash)
        val signed = SigV4.sign(method, path, "", headers, payloadHash, "auto", "s3", ZonedDateTime.now(ZoneOffset.UTC), accessKey, secretKey)
        val b = HttpRequest.newBuilder(URI("https://$host$path")).timeout(Duration.ofMinutes(2))
        signed.filterKeys { it != "host" }.forEach { (k, v) -> b.header(k, v) }
        b.method(method, if (body.isEmpty()) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofByteArray(body))
        return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): R2Target {
            fun need(k: String) = env[k]?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Set $k in the environment to publish to R2 (or pass a local folder).")
            return R2Target(need("R2_ACCOUNT_ID"), need("R2_BUCKET"), need("R2_ACCESS_KEY_ID"), need("R2_SECRET_ACCESS_KEY"))
        }
    }
}

/** AWS Signature Version 4 (https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv.html). */
object SigV4 {
    /**
     * Returns [headers] (lower-case names) plus x-amz-date and authorization. [path] must already be URI-encoded;
     * [query] is the canonical query string ("" for none).
     */
    fun sign(
        method: String, path: String, query: String, headers: Map<String, String>, payloadHash: String,
        region: String, service: String, now: ZonedDateTime, accessKey: String, secretKey: String,
    ): Map<String, String> {
        val amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val day = amzDate.take(8)
        val all = (headers + ("x-amz-date" to amzDate)).mapKeys { it.key.lowercase() }.toSortedMap()
        val signedHeaders = all.keys.joinToString(";")
        val canonical = listOf(
            method, path, query,
            all.entries.joinToString("") { "${it.key}:${it.value.trim()}\n" },
            signedHeaders, payloadHash,
        ).joinToString("\n")
        val scope = "$day/$region/$service/aws4_request"
        val toSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonical)}"
        var k = hmac("AWS4$secretKey".toByteArray(), day)
        k = hmac(k, region); k = hmac(k, service); k = hmac(k, "aws4_request")
        val signature = hmac(k, toSign).joinToString("") { "%02x".format(it) }
        return all + ("authorization" to "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature")
    }

    fun uriEncode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data.toByteArray())
}
