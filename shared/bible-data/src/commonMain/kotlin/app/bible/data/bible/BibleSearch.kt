package app.bible.data.bible

import app.bible.core.Passage
import app.bible.core.ReferenceParser
import app.bible.core.VerseId
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

data class SearchHit(val verse: VerseId, val text: String)

data class SearchResult(
    /** Set when the query is a Bible reference such as "jn 3 16" or "Romans 8". */
    val reference: Passage? = null,
    /** Verses containing every word (or the quoted phrase), in Bible order. */
    val hits: List<SearchHit> = emptyList(),
    /** Total number of matching verses; [hits] holds at most the requested limit. */
    val total: Int = 0,
)

/**
 * Offline search over a bible-<translation>.db: by reference, by words (every word must appear, and the last
 * word may be a prefix while typing) or by "an exact phrase". Uses the FTS5 index the pipeline builds.
 */
class BibleSearch(private val driver: SqlDriver) {

    fun search(input: String, limit: Int = 200): SearchResult {
        val text = input.trim()
        if (text.isEmpty()) return SearchResult()
        val reference = parseReference(text)
        val match = ftsQuery(text) ?: return SearchResult(reference)
        val total = driver.executeQuery(null, "SELECT count(*) FROM verse_fts WHERE verse_fts MATCH ?", { c ->
            QueryResult.Value(if (c.next().value) c.getLong(0)!!.toInt() else 0)
        }, 1) { bindString(0, match) }.value
        val hits = driver.executeQuery(
            null,
            "SELECT v.id, v.text FROM verse_fts f JOIN verse v ON v.id = f.rowid WHERE verse_fts MATCH ? ORDER BY v.id LIMIT ?",
            { c ->
                val out = ArrayList<SearchHit>()
                while (c.next().value) out += SearchHit(VerseId(c.getLong(0)!!.toInt()), c.getString(1)!!)
                QueryResult.Value(out)
            },
            2,
        ) { bindString(0, match); bindLong(1, limit.toLong()) }.value
        return SearchResult(reference, hits, total)
    }

    companion object {
        private val WORD = Regex("[\\p{L}\\p{N}’']+")

        /** Builds a safe FTS5 query: user text never reaches FTS5 syntax unquoted. Null when nothing to search. */
        fun ftsQuery(input: String): String? {
            val t = input.trim()
            if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) {
                val words = WORD.findAll(t).map { it.value.replace("’", "'") }.toList()
                return if (words.isEmpty()) null else "\"" + words.joinToString(" ") { it.replace("\"", "") } + "\""
            }
            val words = WORD.findAll(t).map { it.value.replace("’", "'").replace("\"", "") }.toList()
            if (words.isEmpty() || words.all { it.all(Char::isDigit) }) return null
            // Longer words also match their endings ("shepherd" finds "shepherds"); the last word is a prefix
            // while typing.
            return words.mapIndexed { i, w -> if (w.length >= 4 || (i == words.lastIndex && w.length >= 2)) "\"$w\"*" else "\"$w\"" }
                .joinToString(" ")
        }

        private val LOOSE_REFERENCE = Regex("^(.*\\D)\\s*(\\d+)[\\s.]+(\\d+)$")

        /** Parses a reference, also accepting "jn 3 16" (a space or dot instead of the colon). */
        fun parseReference(text: String): Passage? = ReferenceParser.parseOrNull(text)
            ?: LOOSE_REFERENCE.matchEntire(text.trim())?.let { m ->
                ReferenceParser.parseOrNull("${m.groupValues[1].trim()} ${m.groupValues[2]}:${m.groupValues[3]}")
            }
    }
}
