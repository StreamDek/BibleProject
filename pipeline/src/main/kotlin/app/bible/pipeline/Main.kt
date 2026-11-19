package app.bible.pipeline

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "import-kjv" -> {
            require(args.size >= 3) { "usage: import-kjv <osis.xml> <out.db> [units.tsv]" }
            val verses = OsisImporter.parse(File(args[1]))
            val report = BibleDbWriter.write(verses, File(args[2]), translation = Translation.KJV)
            println(report)
            args.getOrNull(3)?.let { UnitsFixture.write(verses, File(it)); println("Wrote fixture $it") }
        }
        else -> {
            System.err.println("Commands: import-kjv <osis.xml> <out.db> [units.tsv]")
            exitProcess(2)
        }
    }
}
