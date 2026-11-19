package app.bible.core

import kotlin.jvm.JvmInline

/**
 * A canonical verse identifier encoded as BBCCCVVV (e.g. Matthew 2:8 = 40002008).
 * It is translation-independent: stories, images, questions, plans and the reading
 * position all refer to verses by this ID, never by translation text or card index.
 */
@JvmInline
value class VerseId(val value: Int) : Comparable<VerseId> {
    val book: Int get() = value / 1_000_000
    val chapter: Int get() = (value / 1_000) % 1_000
    val verse: Int get() = value % 1_000

    override fun compareTo(other: VerseId): Int = value.compareTo(other.value)

    fun bookOrNull(): Book? = Canon.bookOrNull(book)

    /** e.g. "Luke 2:8" */
    fun display(): String = "${Canon.book(book).name} $chapter:$verse"

    /** e.g. "Luke.2.8" */
    fun osis(): String = "${Canon.book(book).osis}.$chapter.$verse"

    override fun toString(): String = if (Canon.bookOrNull(book) != null) display() else "VerseId($value)"

    companion object {
        fun of(book: Int, chapter: Int, verse: Int): VerseId {
            require(book in 1..99 && chapter in 0..999 && verse in 0..999) { "Out of range: $book $chapter:$verse" }
            return VerseId(book * 1_000_000 + chapter * 1_000 + verse)
        }

        /** Parses an OSIS verse ID such as "1Sam.17.4". */
        fun parseOsis(osis: String): VerseId? {
            val parts = osis.split('.')
            if (parts.size != 3) return null
            val b = Canon.byOsis(parts[0]) ?: return null
            val c = parts[1].toIntOrNull() ?: return null
            val v = parts[2].toIntOrNull() ?: return null
            val id = of(b.number, c, v)
            return id.takeIf { Canon.isValid(it) }
        }
    }
}
