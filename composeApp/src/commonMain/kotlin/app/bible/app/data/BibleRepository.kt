package app.bible.app.data

import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.data.bible.BibleDatabase
import app.bible.feed.VerseUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** A verse ready to display: exact translation text plus the translators' italic spans. */
data class VerseText(
    val id: VerseId,
    val text: String,
    val italics: List<IntRange>,
    val paragraphStart: Boolean,
    val poetry: Boolean,
)

/** Read-only access to one translation's database. */
class BibleRepository(private val db: BibleDatabase) {
    private val q get() = db.bibleQueries

    val translationCode: String by lazy { q.metaValue("translation").executeAsOneOrNull() ?: "kjv" }

    suspend fun units(range: VerseRange): List<VerseUnit> = withContext(Dispatchers.IO) {
        q.unitsBetween(range.start.value.toLong(), range.end.value.toLong()).executeAsList().map {
            VerseUnit(VerseId(it.id.toInt()), (it.chars ?: 0L).toInt(), it.weight_after.toInt())
        }
    }

    suspend fun verses(start: VerseId, end: VerseId): List<VerseText> = withContext(Dispatchers.IO) {
        q.versesBetween(start.value.toLong(), end.value.toLong()).executeAsList().map {
            VerseText(
                id = VerseId(it.id.toInt()),
                text = it.text,
                italics = parseSpans(it.italics),
                paragraphStart = it.flags and 1L != 0L,
                poetry = it.flags and 2L != 0L,
            )
        }
    }

    /** Psalm superscription for a chapter (e.g. "A Psalm of David."), if any. */
    suspend fun heading(book: Int, chapter: Int): String? = withContext(Dispatchers.IO) {
        val id = (book * 1_000_000 + chapter * 1_000).toLong()
        q.headingsBetween(id, id).executeAsOneOrNull()?.text
    }

    private fun parseSpans(s: String?): List<IntRange> =
        s?.split(';')?.map { part -> part.split(':').let { (a, b) -> a.toInt() until b.toInt() } } ?: emptyList()
}
