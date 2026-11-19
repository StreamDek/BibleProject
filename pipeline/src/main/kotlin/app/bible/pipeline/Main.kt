package app.bible.pipeline

import app.bible.pipeline.art.ArtBrief
import app.bible.pipeline.art.ArtWorkflow
import app.bible.pipeline.art.BriefPlanner
import app.bible.pipeline.art.ImageProvider
import app.bible.pipeline.art.LocalTarget
import app.bible.pipeline.art.R2Target
import app.bible.stories.StoryCatalogue
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "extract-paragraphs" -> {
            require(args.size == 3) { "usage: extract-paragraphs <bible.usfx.xml> <out.txt>" }
            val ids = ParagraphExtractor.extract(File(args[1]))
            ParagraphExtractor.write(ids, File(args[2]), source = File(args[1]).name)
            println("Wrote ${ids.size} paragraph starts to ${args[2]}")
        }
        "import-kjv" -> {
            require(args.size >= 3) { "usage: import-kjv <osis.xml> <out.db> [units.tsv] [editorial-dir]" }
            val editorial = args.getOrNull(4)?.let { File(it) }
            val verses = OsisImporter.parse(
                File(args[1]),
                extraParagraphs = editorial?.resolve("paragraphs.txt")?.takeIf { it.exists() }
                    ?.let { EditorialBreaks.readParagraphs(it) } ?: emptySet(),
                sections = editorial?.resolve("sections.tsv")?.takeIf { it.exists() }
                    ?.let { EditorialBreaks.readSections(it) } ?: emptyMap(),
            )
            val report = BibleDbWriter.write(verses, File(args[2]), translation = Translation.KJV)
            println(report)
            val db = File(args[2])
            BibleDbWriter.writeStamp(db, File(db.parentFile, db.nameWithoutExtension + ".stamp"))
            args.getOrNull(3)?.let { UnitsFixture.write(verses, File(it)); println("Wrote fixture $it") }
        }
        "build-stories" -> {
            require(args.size == 5) { "usage: build-stories <catalogue.csv> <curation.tsv> <editorial.tsv> <out.json>" }
            val catalogue = StoriesBuilder.build(File(args[1]), File(args[2]), File(args[3]))
            File(args[4]).writeText(catalogue.encode())
            val s = catalogue.stories
            println(
                "Wrote ${s.size} stories to ${args[4]}: ${s.count { it.hasParallels }} with parallel accounts, " +
                    "${s.count { it.steps.size > 1 }} in several steps, ${s.count { it.intro != null }} introductions, " +
                    "${s.count { it.summary != null }} summaries",
            )
        }
        "art-plan" -> {
            require(args.size == 4) { "usage: art-plan <stories.json> <overrides.tsv> <briefs.jsonl>" }
            val catalogue = StoryCatalogue.decode(File(args[1]).readText())
            val briefs = BriefPlanner.plan(catalogue, BriefPlanner.readOverrides(File(args[2])))
            File(args[3]).apply { parentFile.mkdirs() }.writeText(ArtBrief.writeJsonl(briefs))
            println("Wrote ${briefs.size} briefs to ${args[3]}: " + briefs.groupingBy { it.depiction.label }.eachCount())
        }
        "art-generate" -> {
            require(args.size == 9) { "usage: art-generate <briefs> <review.tsv> <manifest.json> <work-dir> <provider> <model|-> <limit> <slugs|->" }
            val provider = ImageProvider.create(args[5], args[6].takeIf { it != "-" })
            val only = args[8].takeIf { it != "-" }?.split(',')?.map { it.trim() }?.toSet() ?: emptySet()
            workflow(args).generate(provider, args[7].toInt(), only)
        }
        "art-review" -> {
            require(args.size == 5) { "usage: art-review <briefs> <review.tsv> <manifest.json> <work-dir>" }
            val wf = workflow(args)
            val page = wf.writeReviewPage()
            println("${wf.pending().size} stories waiting for review. Open ${page.absolutePath}")
        }
        "art-apply-review" -> {
            require(args.size == 7) { "usage: art-apply-review <briefs> <review.tsv> <manifest.json> <work-dir> <decisions.tsv> <reviewer>" }
            val n = workflow(args).applyDecisions(File(args[5]), args[6])
            println("Added $n decisions to ${args[2]}")
        }
        "art-publish" -> {
            require(args.size == 9) { "usage: art-publish <briefs> <review.tsv> <manifest.json> <work-dir> <app-art.json> <cdn-base|-> <local-dir|r2> <allow-dry-run>" }
            val target = if (args[7] == "r2") R2Target.fromEnv() else LocalTarget(File(args[7]))
            val wf = workflow(args)
            val base = args[6].takeIf { it != "-" } ?: wf.manifest.base
            println("Publishing to ${target.description}")
            val r = wf.publish(target, base, args[8] == "true")
            val text = r.manifest.encode()
            File(args[3]).apply { parentFile.mkdirs() }.writeText(text)
            File(args[5]).writeText(text)
            println("Published ${r.published.size} new images (${r.uploaded} files uploaded); ${r.manifest.images.size} stories have art")
        }
        else -> {
            System.err.println("Commands: import-kjv <osis.xml> <out.db> [units.tsv] [editorial-dir], extract-paragraphs <usfx.xml> <out.txt>, build-stories <catalogue.csv> <curation.tsv> <editorial.tsv> <out.json>, art-plan, art-generate, art-review, art-apply-review, art-publish (see pipeline/build.gradle.kts)")
            exitProcess(2)
        }
    }
}

private fun workflow(args: Array<String>) = ArtWorkflow(File(args[1]), File(args[2]), File(args[3]), File(args[4]))
