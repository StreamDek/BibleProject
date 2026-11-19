package app.bible.pipeline.art

import app.bible.stories.ArtImage
import app.bible.stories.ArtManifest
import app.bible.stories.Blurhash
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * The story-art workflow (proposal §6.2), run by an editor with Gradle tasks:
 *
 *  1. plan      briefs.jsonl from the story catalogue and overrides.tsv (checked into git)
 *  2. generate  4 candidates per brief, once. Skips stories that already have an approved image or candidates
 *               for the same brief, so nothing is ever paid for twice
 *  3. review    review.html in the work folder; the editor approves one candidate, rejects, or asks to regenerate.
 *               Decisions are added to content/art/review.tsv (checked into git: who approved what, and when)
 *  4. publish   approved images → 360/720/1080 wide JPEGs + blurhash, content-addressed, uploaded once to R2;
 *               writes the manifest the app reads
 *
 * Candidates live in the work folder (art-work/, not in git) until they are published.
 */
class ArtWorkflow(
    private val briefsFile: File,
    private val reviewFile: File,
    private val manifestFile: File,
    private val work: File,
) {
    val briefs: List<ArtBrief> by lazy { ArtBrief.readJsonl(briefsFile.readText()) }
    val decisions: List<Decision> by lazy { Decision.read(reviewFile) }
    val manifest: ArtManifest by lazy {
        if (manifestFile.exists()) ArtManifest.decode(manifestFile.readText()) else ArtManifest()
    }

    // ---- generate ----------------------------------------------------------------------------------------

    /** Briefs that need candidates: no published image, nothing awaiting review, and not approved already. */
    fun toGenerate(model: String, only: Set<String> = emptySet()): List<Pair<ArtBrief, Int>> = briefs
        .filter { only.isEmpty() || it.slug in only }
        .filter { it.slug !in manifest.images || latest(it.slug)?.action == Action.Regenerate }
        .filter { latest(it.slug)?.action != Action.Approve }
        // A rejected brief waits for an editor to change it in overrides.tsv; regenerating it unchanged would waste money.
        .filter { b -> latest(b.slug)?.let { it.action == Action.Reject && it.briefHash == b.hash(model) } != true }
        .mapNotNull { b ->
            val hash = b.hash(model)
            val round = 1 + decisions.count { it.slug == b.slug && it.briefHash == hash && it.action != Action.Approve }
            if (candidateDir(b.slug, hash, round).let { File(it, META).exists() }) null else b to round
        }

    fun generate(provider: ImageProvider, limit: Int, only: Set<String>, count: Int = 4, log: (String) -> Unit = ::println): Int {
        val todo = toGenerate(provider.model, only).take(limit)
        log("${todo.size} stories to generate with ${provider.model} (limit $limit, $count candidates each)")
        for ((b, round) in todo) {
            val hash = b.hash(provider.model)
            val dir = candidateDir(b.slug, hash, round).apply { mkdirs() }
            val seed = (hash.take(8).toLong(16) + round * 1000L)
            val out = provider.generate(b.prompt(), count, seed)
            val files = out.mapIndexed { i, g ->
                val name = "c${i + 1}.${if (isPng(g.bytes)) "png" else "jpg"}"
                File(dir, name).writeBytes(g.bytes)
                CandidateFile(name, g.seed)
            }
            val meta = CandidateSet(b.slug, hash, round, provider.model, b.prompt(), Instant.now().toString(), files)
            File(dir, META).writeText(json.encodeToString(CandidateSet.serializer(), meta))
            log("  ${b.slug}: ${files.size} candidates → ${dir.relativeTo(work)}")
        }
        return todo.size
    }

    // ---- review ------------------------------------------------------------------------------------------

    /** Candidate sets waiting for a decision, newest round per story. */
    fun pending(): List<Pair<ArtBrief, CandidateSet>> = briefs.mapNotNull { b ->
        val sets = File(work, "candidates/${b.slug}").listFiles()?.mapNotNull { d ->
            File(d, META).takeIf { it.exists() }?.let { json.decodeFromString(CandidateSet.serializer(), it.readText()) }
        }.orEmpty()
        val newest = sets.maxByOrNull { it.created } ?: return@mapNotNull null
        val decided = decisions.any { it.slug == b.slug && it.briefHash == newest.briefHash && it.round == newest.round }
        if (decided) null else b to newest
    }

    fun writeReviewPage(): File {
        val page = File(work, "review.html")
        page.writeText(ReviewPage.html(pending()))
        return page
    }

    /** Adds an editor's downloaded decisions to review.tsv after checking each one. */
    fun applyDecisions(file: File, reviewer: String): Int {
        val pend = pending().associate { it.first.slug to it.second }
        val new = Decision.read(file).map { d ->
            val set = pend[d.slug] ?: throw IllegalArgumentException("${d.slug}: no candidates are waiting for review")
            require(d.briefHash == set.briefHash && d.round == set.round) { "${d.slug}: decision is for an older candidate set" }
            if (d.action == Action.Approve) require(set.files.any { it.name == d.candidate }) { "${d.slug}: unknown candidate ${d.candidate}" }
            if (d.action != Action.Approve) require(d.note.isNotBlank()) { "${d.slug}: give a reason when rejecting" }
            d.copy(reviewer = d.reviewer.ifBlank { reviewer }, at = d.at.ifBlank { Instant.now().toString() })
        }
        new.groupBy { it.slug }.filter { it.value.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
            throw IllegalArgumentException("More than one decision for $it")
        }
        if (new.isNotEmpty()) {
            if (!reviewFile.exists()) reviewFile.writeText(Decision.HEADER)
            reviewFile.appendText(new.joinToString("") { it.line() + "\n" })
        }
        return new.size
    }

    // ---- publish -----------------------------------------------------------------------------------------

    class PublishResult(val manifest: ArtManifest, val published: List<String>, val uploaded: Int)

    fun publish(target: PublishTarget, base: String, allowDryRun: Boolean, log: (String) -> Unit = ::println): PublishResult {
        val bySlug = briefs.associateBy { it.slug }
        val images = LinkedHashMap(manifest.images.filterKeys { it in bySlug })
        val published = ArrayList<String>()
        var uploaded = 0
        for (b in briefs) {
            val d = latest(b.slug)?.takeIf { it.action == Action.Approve } ?: continue
            val dir = candidateDir(b.slug, d.briefHash, d.round)
            val meta = File(dir, META).takeIf { it.exists() }?.let { json.decodeFromString(CandidateSet.serializer(), it.readText()) }
            val source = File(dir, d.candidate)
            if (meta == null || !source.exists()) {
                if (b.slug !in images) log("  ${b.slug}: approved candidate ${dir.name}/${d.candidate} is not in this work folder; skipped")
                continue
            }
            if (meta.model == "dry-run" && !allowDryRun) throw IllegalStateException("${b.slug}: refusing to publish a dry-run placeholder")
            val original = source.readBytes()
            val hash = sha256Hex(original)
            if (images[b.slug]?.hash == hash) continue
            val img = ImageIO.read(ByteArrayInputStream(original)) ?: throw IllegalStateException("${b.slug}: cannot read ${source.name}")
            val portrait = cropTo(img, 9, 16)
            for (w in WIDTHS) {
                val key = "img/$hash.$w.jpg"
                if (target.exists(key)) continue
                target.put(key, jpeg(scale(portrait, w, w * 16 / 9)), "image/jpeg", IMMUTABLE)
                uploaded++
            }
            val small = scale(portrait, 27, 48)
            val px = small.getRGB(0, 0, small.width, small.height, null, 0, small.width)
            images[b.slug] = ArtImage(hash, WIDTHS, Blurhash.encode(px, small.width, small.height, 3, 4), b.depiction, b.people)
            published += b.slug
            log("  ${b.slug}: published ${hash.take(12)}")
        }
        val m = ArtManifest(base = base, images = images)
        val text = m.encode()
        target.put("manifest/art-${sha256Hex(text).take(16)}.json", text.toByteArray(), "application/json", IMMUTABLE)
        target.put("manifest/art.json", text.toByteArray(), "application/json", "public, max-age=300")
        return PublishResult(m, published, uploaded)
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private fun latest(slug: String) = decisions.lastOrNull { it.slug == slug }

    private fun candidateDir(slug: String, hash: String, round: Int) = File(work, "candidates/$slug/$hash-r$round")

    private fun isPng(b: ByteArray) = b.size > 4 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()

    companion object {
        val WIDTHS = listOf(360, 720, 1080)
        const val META = "candidates.json"
        const val IMMUTABLE = "public, max-age=31536000, immutable"
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        /** Centre crop to the given aspect ratio. */
        fun cropTo(img: BufferedImage, aw: Int, ah: Int): BufferedImage {
            val target = aw.toDouble() / ah
            val actual = img.width.toDouble() / img.height
            return when {
                kotlin.math.abs(actual - target) < 0.01 -> img
                actual > target -> { val w = (img.height * target).toInt(); img.getSubimage((img.width - w) / 2, 0, w, img.height) }
                else -> { val h = (img.width / target).toInt(); img.getSubimage(0, (img.height - h) / 2, img.width, h) }
            }
        }

        /** High-quality downscale in halving steps, then a final bicubic step. */
        fun scale(src: BufferedImage, w: Int, h: Int): BufferedImage {
            var cur = toRgb(src)
            while (cur.width / 2 >= w && cur.height / 2 >= h) cur = draw(cur, cur.width / 2, cur.height / 2)
            return if (cur.width == w && cur.height == h) cur else draw(cur, w, h)
        }

        private fun toRgb(src: BufferedImage): BufferedImage =
            if (src.type == BufferedImage.TYPE_INT_RGB) src else draw(src, src.width, src.height)

        private fun draw(src: BufferedImage, w: Int, h: Int): BufferedImage {
            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(src, 0, 0, w, h, null)
            g.dispose()
            return out
        }

        fun jpeg(img: BufferedImage, quality: Float = 0.84f): ByteArray {
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            val out = ByteArrayOutputStream()
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                val p = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = quality }
                writer.write(null, IIOImage(img, null, null), p)
            }
            writer.dispose()
            return out.toByteArray()
        }
    }
}

@Serializable
data class CandidateFile(val name: String, val seed: Long? = null)

/** What was generated, from which prompt and model: kept beside the images for reproducibility. */
@Serializable
data class CandidateSet(
    val slug: String,
    val briefHash: String,
    val round: Int,
    val model: String,
    val prompt: String,
    val created: String,
    val files: List<CandidateFile>,
)

enum class Action(val word: String) { Approve("approve"), Reject("reject"), Regenerate("regenerate") }

/** One line of content/art/review.tsv: slug, brief hash, round, action, candidate, reviewer, time, note. */
data class Decision(
    val slug: String,
    val briefHash: String,
    val round: Int,
    val action: Action,
    val candidate: String,
    val reviewer: String,
    val at: String,
    val note: String,
) {
    fun line() = listOf(slug, briefHash, round.toString(), action.word, candidate, reviewer, at, note.replace('\t', ' ').replace('\n', ' '))
        .joinToString("\t")

    companion object {
        const val HEADER = "# Story art review log (append only). Columns: slug, brief_hash, round, action, candidate, reviewer, time, note\n" +
            "# action: approve (candidate is published), reject (the brief needs editing in overrides.tsv), regenerate (same brief, new seeds)\n"

        fun read(file: File): List<Decision> {
            if (!file.exists()) return emptyList()
            return file.readLines().mapIndexedNotNull { n, raw ->
                if (raw.isBlank() || raw.startsWith("#")) return@mapIndexedNotNull null
                val f = raw.split('\t')
                require(f.size >= 4) { "${file.name} line ${n + 1}: expected at least slug, brief_hash, round, action" }
                val action = Action.entries.firstOrNull { it.word == f[3].trim() }
                    ?: throw IllegalArgumentException("${file.name} line ${n + 1}: unknown action ${f[3]}")
                Decision(f[0].trim(), f[1].trim(), f[2].trim().toInt(), action, f.getOrElse(4) { "" }.trim(),
                    f.getOrElse(5) { "" }.trim(), f.getOrElse(6) { "" }.trim(), f.getOrElse(7) { "" }.trim())
            }
        }
    }
}
