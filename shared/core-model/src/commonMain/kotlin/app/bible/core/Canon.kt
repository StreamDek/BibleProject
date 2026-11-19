package app.bible.core

enum class Testament { OLD, NEW }

/**
 * A canonical book. [number] is 1..66 in Protestant order, [osis] the OSIS abbreviation
 * (e.g. "1Sam"), [verseCounts] the number of verses in each chapter (KJV versification).
 */
class Book internal constructor(
    val number: Int,
    val osis: String,
    val name: String,
    val testament: Testament,
    private val verseCounts: IntArray,
) {
    val chapterCount: Int get() = verseCounts.size
    fun verseCount(chapter: Int): Int = verseCounts[chapter - 1]
    fun hasChapter(chapter: Int): Boolean = chapter in 1..chapterCount
    val isSingleChapter: Boolean get() = chapterCount == 1
    val firstVerse: VerseId get() = VerseId.of(number, 1, 1)
    val lastVerse: VerseId get() = VerseId.of(number, chapterCount, verseCount(chapterCount))
    override fun toString(): String = name
}

/** The canonical scheme every story, image, question and plan refers to: 66 books, KJV versification. */
object Canon {
    val books: List<Book> = KjvBookData.books
    private val byOsis = books.associateBy { it.osis.lowercase() }

    val totalVerses: Int = books.sumOf { b -> (1..b.chapterCount).sumOf { b.verseCount(it) } }

    fun book(number: Int): Book = books[number - 1]
    fun bookOrNull(number: Int): Book? = books.getOrNull(number - 1)
    fun byOsis(osis: String): Book? = byOsis[osis.lowercase()]

    fun isValid(id: VerseId): Boolean {
        val b = bookOrNull(id.book) ?: return false
        return b.hasChapter(id.chapter) && id.verse in 1..b.verseCount(id.chapter)
    }

    /** The next verse in canonical order, crossing chapter and book boundaries, or null after Revelation 22:21. */
    fun next(id: VerseId): VerseId? {
        val b = book(id.book)
        return when {
            id.verse < b.verseCount(id.chapter) -> VerseId.of(id.book, id.chapter, id.verse + 1)
            id.chapter < b.chapterCount -> VerseId.of(id.book, id.chapter + 1, 1)
            id.book < books.size -> VerseId.of(id.book + 1, 1, 1)
            else -> null
        }
    }

    /** The previous verse in canonical order, or null before Genesis 1:1. */
    fun previous(id: VerseId): VerseId? {
        return when {
            id.verse > 1 -> VerseId.of(id.book, id.chapter, id.verse - 1)
            id.chapter > 1 -> VerseId.of(id.book, id.chapter - 1, book(id.book).verseCount(id.chapter - 1))
            id.book > 1 -> book(id.book - 1).lastVerse
            else -> null
        }
    }

    /** All verse IDs from [start] to [end] inclusive, in canonical order. */
    fun verses(start: VerseId, end: VerseId): Sequence<VerseId> = sequence {
        var cur: VerseId? = start
        while (cur != null && cur <= end) {
            yield(cur)
            cur = next(cur)
        }
    }
}
