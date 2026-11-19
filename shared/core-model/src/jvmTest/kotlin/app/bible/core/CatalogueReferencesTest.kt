package app.bible.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every reference in the story catalogue must parse to valid canonical verses. */
class CatalogueReferencesTest {
    @Test fun allCatalogueReferencesParse() {
        val csv = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "content/catalogue/bible_story_catalogue.csv") }
            .first { it.exists() }
        val rows = csv.readLines().drop(1).filter { it.isNotBlank() }
        val failures = mutableListOf<String>()
        for (line in rows) {
            val ref = parseCsvLine(line)[4]
            if (ReferenceParser.parseOrNull(ref) == null) failures += ref
        }
        assertEquals(652, rows.size)
        assertTrue(failures.isEmpty(), "Unparseable references: $failures")
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var q = false
        for (ch in line) when {
            ch == '"' -> q = !q
            ch == ',' && !q -> { out += sb.toString(); sb.clear() }
            else -> sb.append(ch)
        }
        out += sb.toString(); return out
    }
}
