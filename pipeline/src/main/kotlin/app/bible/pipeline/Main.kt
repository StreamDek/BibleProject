package app.bible.pipeline

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
        else -> {
            System.err.println("Commands: import-kjv <osis.xml> <out.db> [units.tsv] [editorial-dir], extract-paragraphs <usfx.xml> <out.txt>")
            exitProcess(2)
        }
    }
}
