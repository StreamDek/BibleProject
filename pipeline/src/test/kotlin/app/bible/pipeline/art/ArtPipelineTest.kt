package app.bible.pipeline.art

import app.bible.stories.ArtManifest
import app.bible.stories.Blurhash
import app.bible.stories.Depiction
import app.bible.stories.StoryCatalogue
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtPipelineTest {
    private val catalogue = StoryCatalogue.decode(File("composeApp/src/commonMain/composeResources/files/stories.json").readText())
    private val overrides = BriefPlanner.readOverrides(File("content/art/overrides.tsv"))
    private val briefs = BriefPlanner.plan(catalogue, overrides)

    @Test fun sigV4MatchesAwsExample() {
        // "Example: GET Object" from the Amazon S3 Signature Version 4 documentation.
        val emptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val h = SigV4.sign(
            "GET", "/test.txt", "",
            mapOf("host" to "examplebucket.s3.amazonaws.com", "range" to "bytes=0-9", "x-amz-content-sha256" to emptyHash),
            emptyHash, "us-east-1", "s3", ZonedDateTime.of(2013, 5, 24, 0, 0, 0, 0, ZoneOffset.UTC),
            "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        )
        assertTrue(h.getValue("authorization").endsWith("Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"), h["authorization"])
    }

    @Test fun everyStoryGetsOneBriefThatFollowsThePolicy() {
        assertEquals(catalogue.stories.size, briefs.size)
        assertEquals(briefs.size, briefs.map { it.slug }.toSet().size)
        for (b in briefs) {
            val p = b.prompt()
            assertTrue("any text, letters, writing" in p, b.slug)
            assertTrue("not European" in p || !b.people, b.slug)
            assertFalse('"' in b.scene && b.depiction != Depiction.Symbolic, "${b.slug}: quotes in a scene get drawn as lettering")
            if (b.divine == DivinePresence.LightSilhouette) assertTrue("no face" in p, b.slug)
            if (!b.people) assertEquals(DivinePresence.None, b.divine, b.slug)
        }
        val byslug = briefs.associateBy { it.slug }
        assertEquals(DivinePresence.LightSilhouette, byslug.getValue("feeding-five-thousand").divine)
        assertEquals(Depiction.Symbolic, byslug.getValue("prodigal-son").depiction)
        assertEquals(Depiction.Historical, byslug.getValue("armor-of-god").depiction)
        assertTrue(briefs.filter { it.depiction == Depiction.Atmospheric }.all { !it.people }, "atmospheric images show no people")
    }

    @Test fun briefsFileIsUpToDate() {
        val file = File("content/art/briefs.jsonl")
        assertEquals(ArtBrief.writeJsonl(briefs), file.readText(), "Run ./gradlew :pipeline:artPlan and commit content/art/briefs.jsonl")
    }

    @Test fun unknownOverrideIsAnError() {
        assertFailsWith<BriefPlanner.PlanException> { BriefPlanner.plan(catalogue, mapOf("no-such-story" to mapOf("scene" to "x"))) }
    }

    @Test fun generateReviewPublishOnce() {
        val dir = Files.createTempDirectory("art").toFile()
        val briefsFile = File(dir, "briefs.jsonl").apply { writeText(ArtBrief.writeJsonl(briefs.take(3))) }
        val review = File(dir, "review.tsv")
        val manifest = File(dir, "manifest.json")
        val work = File(dir, "work")
        fun wf() = ArtWorkflow(briefsFile, review, manifest, work)
        val provider = DryRunProvider()

        assertEquals(2, wf().generate(provider, limit = 2, only = emptySet()) {})
        assertEquals(1, wf().generate(provider, limit = 10, only = emptySet()) {}) // only the third is left
        assertEquals(0, wf().generate(provider, limit = 10, only = emptySet()) {}) // all waiting for review

        val pending = wf().pending()
        assertEquals(3, pending.size)
        assertTrue(wf().writeReviewPage().readText().contains(pending[0].first.title.replace("'", "'")))
        val (b0, s0) = pending[0]
        val (b1, s1) = pending[1]
        val decisions = File(dir, "decisions.tsv").apply {
            writeText(
                "${b0.slug}\t${s0.briefHash}\t${s0.round}\tapprove\tc2.png\t\t\t\n" +
                    "${b1.slug}\t${s1.briefHash}\t${s1.round}\tregenerate\t\t\t\tfaces too modern\n",
            )
        }
        assertEquals(2, wf().applyDecisions(decisions, "Tester"))
        assertTrue(review.readText().contains("Tester"))

        // Regenerate asks for a new round of the same brief; the approved story is never generated again.
        assertEquals(1, wf().generate(provider, limit = 10, only = emptySet()) {})

        assertFailsWith<IllegalStateException> { wf().publish(LocalTarget(File(dir, "out")), "", allowDryRun = false) {} }
        val out = File(dir, "out")
        val r = wf().publish(LocalTarget(out), "https://cdn.example.com", allowDryRun = true) {}
        assertEquals(listOf(b0.slug), r.published)
        assertEquals(3, r.uploaded)
        val img = r.manifest.images.getValue(b0.slug)
        assertTrue(Blurhash.isValid(img.blurhash))
        for (w in ArtWorkflow.WIDTHS) {
            val f = File(out, "img/${img.hash}.$w.jpg")
            assertTrue(f.exists())
            val read = javax.imageio.ImageIO.read(f)
            assertEquals(w, read.width)
            assertEquals(w * 16 / 9, read.height) // dry-run images are 9:16 and keep their shape
        }
        manifest.writeText(r.manifest.encode())
        val again = wf().publish(LocalTarget(out), "https://cdn.example.com", allowDryRun = true) {}
        assertEquals(0, again.uploaded)
        assertEquals(r.manifest, ArtManifest.decode(manifest.readText()))
        dir.deleteRecursively()
    }

    @Test fun editorImageIsApprovedAndBundled() {
        val dir = Files.createTempDirectory("art").toFile()
        val briefsFile = File(dir, "briefs.jsonl").apply { writeText(ArtBrief.writeJsonl(briefs.take(2))) }
        val review = File(dir, "review.tsv")
        val manifest = File(dir, "manifest.json")
        val work = File(dir, "work")
        fun wf() = ArtWorkflow(briefsFile, review, manifest, work)
        // A 3:4 image, like the creation test image.
        val img = java.awt.image.BufferedImage(600, 800, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val png = File(dir, "test.png").also { javax.imageio.ImageIO.write(img, "png", it) }
        val slug = briefs[0].slug
        wf().importApproved(slug, png, "Henry", "test image")
        assertTrue(review.readText().contains("\tapprove\tc1.png\tHenry\t"))
        assertEquals(0, wf().generate(DryRunProvider(), 10, setOf(slug)) {}, "an approved story is not generated")
        val res = File(dir, "files")
        val r = wf().publish(LocalTarget(res), "", allowDryRun = false, bundle = true) {}
        val art = r.manifest.images.getValue(slug)
        assertTrue(art.bundled)
        assertEquals(listOf(720), art.widths)
        val out = javax.imageio.ImageIO.read(File(res, "art/${art.hash}.720.jpg"))
        assertEquals(960, out.height, "keeps its 3:4 shape")
        assertEquals(art, r.manifest.available(slug))
        assertFalse(File(res, "manifest").exists(), "bundling writes no CDN manifest")
        dir.deleteRecursively()
    }
}
