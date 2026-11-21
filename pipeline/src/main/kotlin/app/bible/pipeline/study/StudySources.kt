package app.bible.pipeline.study

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

/**
 * One upstream file the word-study pack is built from, pinned to a commit and a SHA-256 hash. Upstream files are
 * not copied into this repository (STEP Bible asks that copies of its files are not reposted); the pipeline
 * downloads them into a cache and refuses any file whose hash differs from the pinned one.
 *
 * [registryId] names the entry in composeResources/files/attributions.json that credits this source.
 */
data class StudySource(
    val key: String,
    val registryId: String,
    val url: String,
    val sha256: String,
    val version: String,
) {
    val fileName: String get() = key + "." + url.substringAfterLast('.').substringBefore('?')
}

object StudySources {
    private const val STRONGS = "0acd2f251c2d35ff8db2dece4e0593979d3ac223"
    private const val STEP = "5de0a47f35f0fc13dfe1430d104e5db8a4285f9c"
    private const val CROSSWIRE = "d490be7e34762deb2c76cb2c1306d4808e27890d"

    private fun step(path: String) =
        "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/$STEP/" + path.replace(" ", "%20")

    val strongsHebrew = StudySource(
        key = "strongs-hebrew",
        registryId = "strongs-hebrew",
        url = "https://raw.githubusercontent.com/openscriptures/strongs/$STRONGS/hebrew/StrongHebrewG.xml",
        sha256 = "1f9659ea208f4c498843a0280dacb1448627c33ca77712642d8705793ab66061",
        version = "openscriptures/strongs@${STRONGS.take(7)}",
    )
    val strongsGreek = StudySource(
        key = "strongs-greek",
        registryId = "strongs-greek",
        url = "https://raw.githubusercontent.com/openscriptures/strongs/$STRONGS/greek/StrongsGreekDictionaryXML_1.4/strongsgreek.xml",
        sha256 = "df928f01b37632f8af9f16289ce58d10b958014cb5dbd1e1ea715a8d311a0625",
        version = "1.4 (openscriptures/strongs@${STRONGS.take(7)})",
    )
    val tbesh = StudySource(
        key = "step-tbesh",
        registryId = "step-tbesh",
        url = step("Lexicons/TBESH - Translators Brief lexicon of Extended Strongs for Hebrew - STEPBible.org CC BY.txt"),
        sha256 = "848eef2ac16753a43694897dbb9e8f266725e7cc9da4809f367ee5b990f05dbb",
        version = "STEPBible-Data@${STEP.take(7)}",
    )
    val tbesg = StudySource(
        key = "step-tbesg",
        registryId = "step-tbesg",
        url = step("Lexicons/TBESG - Translators Brief lexicon of Extended Strongs for Greek - STEPBible.org CC BY.txt"),
        sha256 = "734a5b64a1152f67604bf09d32f15c0ba537151961752fcf168087b41b4d93ea",
        version = "STEPBible-Data@${STEP.take(7)}",
    )
    val crosswireKjv = StudySource(
        key = "crosswire-kjv",
        registryId = "crosswire-kjv",
        url = "https://gitlab.com/crosswire-bible-society/kjv/-/raw/$CROSSWIRE/kjv.osis.xml",
        sha256 = "4bcbfe45722fca396adbeac9c81025bbb173cb1e30296da90864f0400cb0e3f5",
        version = "KJV module 3.1 (crosswire-bible-society/kjv@${CROSSWIRE.take(7)})",
    )

    private fun text(key: String, registry: String, path: String, sha: String) = StudySource(
        key = key, registryId = registry, url = step(path), sha256 = sha, version = "STEPBible-Data@${STEP.take(7)}",
    )

    /** STEP Bible's Hebrew Old Testament (TAHOT: Leningrad Codex with STEP's translation, tags and grammar), in 4 files. */
    val tahot = listOf(
        text("step-tahot-1", "step-tahot", "Translators Amalgamated OT+NT/TAHOT Gen-Deu - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt", "7763ef367bb0e6e1c1a95fb9b652e1d611b2481827611433e82abbf229216d70"),
        text("step-tahot-2", "step-tahot", "Translators Amalgamated OT+NT/TAHOT Jos-Est - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt", "b79add01a8c95673e31ec3ab8ad86183d2312cd96421ddf21f3d1f74377526a7"),
        text("step-tahot-3", "step-tahot", "Translators Amalgamated OT+NT/TAHOT Job-Sng - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt", "e0416880bb78f46715bb17ce526b623c3051da0ef83f7bf83beaad350e6272e8"),
        text("step-tahot-4", "step-tahot", "Translators Amalgamated OT+NT/TAHOT Isa-Mal - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt", "d5a609f5a4b9f7757a2615e55877b07f9f6931a8afccef012c0b4bf662caf540"),
    )

    /** STEP Bible's Greek New Testament (TAGNT: every word of the main editions, marked by edition), in 2 files. */
    val tagnt = listOf(
        text("step-tagnt-1", "step-tagnt", "Translators Amalgamated OT+NT/TAGNT Mat-Jhn - Translators Amalgamated Greek NT - STEPBible.org CC-BY.txt", "d4c0c820fdf9f2b450c9f96ce6103d6875028affd2e44094d753f1329ca1fd85"),
        text("step-tagnt-2", "step-tagnt", "Translators Amalgamated OT+NT/TAGNT Act-Rev - Translators Amalgamated Greek NT - STEPBible.org CC-BY.txt", "122040eeeaeba50d6abbf67b39cacaff687211bda69b080e5d53927c830d9968"),
    )

    /** STEP Bible's expansions of the Hebrew and Greek grammar codes into plain explanations. */
    val tehmc = text("step-tehmc", "step-morphology", "Morphology codes/TEHMC - Translators Expansion of Hebrew Morphology Codes - STEPBible.org CC BY.txt", "fe37e62b97e531410159e5f1b1ac8a69db4512c3e8407c03bae38639bd09f97f")
    val tegmc = text("step-tegmc", "step-morphology", "Morphology codes/TEGMC - Translators Expansion of Greek Morphhology Codes - STEPBible.org CC BY.txt", "9e8443fd4759f112253d7a1858c0687a8cf833f4d537e8fda97bfff74ea4c168")

    /** STEP Bible's people, places and other proper names, with family links and every reference. */
    val tipnr = text("step-tipnr", "step-tipnr", "Proper Nouns/TIPNR - Translators Individualised Proper Names with all References - STEPBible.org CC BY.txt", "d595b9163d32a400bb0ef96b27a6de58335a63da840b82531fe9f5e1d16a318c")

    /**
     * OpenBible.info's cross-references (CC BY), dated 2024-11-04. openbible.info itself is not reachable from every
     * build machine, so the unchanged file is read from scrollmapper/bible_databases at a pinned commit; the
     * hash pins the exact content either way.
     */
    val crossRefs = StudySource(
        key = "openbible-xrefs",
        registryId = "openbible-xrefs",
        url = "https://raw.githubusercontent.com/scrollmapper/bible_databases/59b18c5af5845728c275d5b7178ad0d024172b1f/sources/extras/cross_references.txt",
        sha256 = "452b263f657d3e721d20341b6b82f1196fc98882d685bd4ef9050ae17f033f82",
        version = "openbible.info cross-references 2024-11-04",
    )

    val all = listOf(strongsHebrew, strongsGreek, tbesh, tbesg, crosswireKjv) + tahot + tagnt + listOf(tehmc, tegmc, tipnr, crossRefs)

    /** Returns the cached file for [source], downloading it first if needed. Fails on any hash mismatch. */
    fun fetch(source: StudySource, cacheDir: File): File {
        cacheDir.mkdirs()
        val file = File(cacheDir, source.fileName)
        if (file.exists() && sha256(file) == source.sha256) return file
        println("Downloading ${source.key} from ${source.url}")
        val client = HttpClient.newBuilder().proxy(java.net.ProxySelector.getDefault()).followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()
        val tmp = File(cacheDir, source.fileName + ".part")
        var lastError: Exception? = null
        for (attempt in 1..4) {
            try {
                val response = client.send(
                    HttpRequest.newBuilder(URI(source.url)).timeout(Duration.ofMinutes(5)).header("User-Agent", "bible-app-pipeline").build(),
                    HttpResponse.BodyHandlers.ofFile(tmp.toPath()),
                )
                check(response.statusCode() == 200) { "HTTP ${response.statusCode()} for ${source.url}" }
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(2_000L * attempt)
            }
        }
        lastError?.let { throw it }
        val actual = sha256(tmp)
        check(actual == source.sha256) {
            tmp.delete()
            "${source.key}: SHA-256 is $actual, expected ${source.sha256}. The upstream file changed: review it, then update StudySources."
        }
        tmp.renameTo(file)
        return file
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
