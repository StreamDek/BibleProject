package app.bible.pipeline.art

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import javax.imageio.ImageIO

/** One generated candidate: PNG or JPEG bytes and the seed used, when the provider reports one. */
class Generated(val bytes: ByteArray, val seed: Long?)

/**
 * The provider adapter (proposal §6.2 step 3): the rest of the pipeline does not know which model made an image,
 * so the model can be changed without touching anything else. Keys come from environment variables only.
 */
interface ImageProvider {
    /** Stable name recorded with every candidate, e.g. "fal:fal-ai/flux/dev". Part of the brief hash. */
    val model: String
    fun generate(prompt: String, count: Int, seed: Long): List<Generated>

    companion object {
        fun create(name: String, model: String?, env: Map<String, String> = System.getenv()): ImageProvider = when (name) {
            "fal" -> FalProvider(env["FAL_KEY"] ?: missing("FAL_KEY"), model ?: "fal-ai/flux/dev")
            "openai" -> OpenAiProvider(
                env["OPENAI_API_KEY"] ?: missing("OPENAI_API_KEY"),
                model ?: "gpt-image-1",
                quality = env["OPENAI_IMAGE_QUALITY"] ?: "medium",
            )
            "dry-run" -> DryRunProvider()
            else -> throw IllegalArgumentException("Unknown provider \"$name\" (fal, openai, dry-run)")
        }

        private fun missing(v: String): Nothing =
            throw IllegalStateException("Set $v in the environment. Keys are never stored in the repository.")
    }
}

private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
private val json = Json { ignoreUnknownKeys = true }

private fun postJson(url: String, auth: String, body: JsonObject): JsonObject {
    val req = HttpRequest.newBuilder(URI(url))
        .timeout(Duration.ofMinutes(5))
        .header("Authorization", auth)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build()
    val res = http.send(req, HttpResponse.BodyHandlers.ofString())
    if (res.statusCode() !in 200..299) throw IllegalStateException("$url returned ${res.statusCode()}: ${res.body().take(500)}")
    return json.parseToJsonElement(res.body()).jsonObject
}

private fun download(url: String): ByteArray {
    val res = http.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(2)).build(), HttpResponse.BodyHandlers.ofByteArray())
    if (res.statusCode() !in 200..299) throw IllegalStateException("Download failed (${res.statusCode()}): $url")
    return res.body()
}

/** FLUX models hosted on fal.ai (https://fal.ai/models/fal-ai/flux/dev/api). Portrait 9:16. */
class FalProvider(private val key: String, private val endpoint: String) : ImageProvider {
    override val model = "fal:$endpoint"

    override fun generate(prompt: String, count: Int, seed: Long): List<Generated> {
        val res = postJson(
            "https://fal.run/$endpoint", "Key $key",
            buildJsonObject {
                put("prompt", prompt)
                put("image_size", "portrait_16_9")
                put("num_images", count)
                put("seed", seed)
                put("output_format", "png")
                put("enable_safety_checker", true)
            },
        )
        val usedSeed = res["seed"]?.jsonPrimitive?.longOrNull ?: seed
        return res.getValue("images").jsonArray.mapIndexed { i, img ->
            Generated(download(img.jsonObject.getValue("url").jsonPrimitive.content), usedSeed + i)
        }
    }
}

/** OpenAI image models (https://platform.openai.com/docs/api-reference/images). Portrait 1024×1536. */
class OpenAiProvider(private val key: String, private val modelName: String, private val quality: String = "medium") : ImageProvider {
    init {
        require(quality in setOf("low", "medium", "high")) { "OPENAI_IMAGE_QUALITY must be low, medium or high" }
    }

    /** Quality is part of the name, so changing it counts as a new brief hash and new candidates. */
    override val model = "openai:$modelName:$quality"

    override fun generate(prompt: String, count: Int, seed: Long): List<Generated> {
        val res = postJson(
            "https://api.openai.com/v1/images/generations", "Bearer $key",
            buildJsonObject {
                put("model", modelName)
                put("prompt", prompt)
                put("size", "1024x1536")
                // Set explicitly: the API's default ("auto") may choose high quality, which costs several times more.
                put("quality", quality)
                put("n", count)
            },
        )
        return res.getValue("data").jsonArray.map { d ->
            val o = d.jsonObject
            val bytes = o["b64_json"]?.jsonPrimitive?.content?.let { Base64.getDecoder().decode(it) }
                ?: download(o.getValue("url").jsonPrimitive.content)
            Generated(bytes, null)
        }
    }
}

/**
 * Makes plain gradient placeholders without calling any service, so the whole pipeline (plan, review, publish)
 * can be tried for free. The publisher refuses to publish these unless told to (for local tests only).
 */
class DryRunProvider : ImageProvider {
    override val model = "dry-run"

    override fun generate(prompt: String, count: Int, seed: Long): List<Generated> = List(count) { i ->
        val rnd = java.util.Random(seed + i)
        val img = BufferedImage(576, 1024, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = GradientPaint(0f, 0f, Color(rnd.nextInt(0xFFFFFF)), 0f, 1024f, Color(rnd.nextInt(0x404040)))
        g.fillRect(0, 0, 576, 1024)
        g.color = Color(255, 240, 200, 160)
        g.fillOval(200 + rnd.nextInt(100), 220 + rnd.nextInt(100), 120, 120)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        Generated(out.toByteArray(), seed + i)
    }
}

