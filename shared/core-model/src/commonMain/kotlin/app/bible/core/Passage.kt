package app.bible.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** An inclusive, contiguous range of canonical verses. May cross chapters (e.g. Genesis 1:1-2:3). */
@Serializable(with = VerseRangeSerializer::class)
data class VerseRange(val start: VerseId, val end: VerseId) {
    init {
        require(start <= end) { "start $start is after end $end" }
        require(Canon.isValid(start) && Canon.isValid(end)) { "Invalid verse in $start-$end" }
    }

    operator fun contains(id: VerseId): Boolean = id >= start && id <= end
    fun overlaps(other: VerseRange): Boolean = start <= other.end && other.start <= end
    fun verses(): Sequence<VerseId> = Canon.verses(start, end)
    val verseCount: Int get() = verses().count()

    /** Compact human reference, e.g. "Luke 2:8-14", "Genesis 1:1-2:3", "Psalms 23". */
    fun display(): String {
        val b = Canon.book(start.book)
        val sameBook = start.book == end.book
        val wholeChapters = sameBook && start.verse == 1 && end.verse == b.verseCount(end.chapter)
        return when {
            !sameBook -> "${start.display()} - ${end.display()}"
            wholeChapters && b.isSingleChapter -> b.name
            wholeChapters && start.chapter == end.chapter -> "${b.name} ${start.chapter}"
            wholeChapters -> "${b.name} ${start.chapter}-${end.chapter}"
            start == end -> start.display()
            start.chapter == end.chapter -> "${b.name} ${start.chapter}:${start.verse}-${end.verse}"
            else -> "${b.name} ${start.chapter}:${start.verse}-${end.chapter}:${end.verse}"
        }
    }

    /** Stable machine form, e.g. "42002008-42002014". */
    fun encode(): String = "${start.value}-${end.value}"

    override fun toString(): String = display()

    companion object {
        fun single(id: VerseId) = VerseRange(id, id)
        fun chapter(book: Int, chapter: Int): VerseRange {
            val b = Canon.book(book)
            return VerseRange(VerseId.of(book, chapter, 1), VerseId.of(book, chapter, b.verseCount(chapter)))
        }
        fun book(book: Int): VerseRange = Canon.book(book).let { VerseRange(it.firstVerse, it.lastVerse) }
        fun decode(s: String): VerseRange {
            val (a, b) = s.split('-').map { VerseId(it.toInt()) }
            return VerseRange(a, b)
        }
    }
}

/**
 * An ordered list of verse ranges, which may have gaps (e.g. Matthew 13:24-30,36-43).
 * Stories store passages, never Scripture text.
 */
@Serializable
data class Passage(val ranges: List<VerseRange>) {
    init { require(ranges.isNotEmpty()) }

    operator fun contains(id: VerseId): Boolean = ranges.any { id in it }
    fun verses(): Sequence<VerseId> = ranges.asSequence().flatMap { it.verses() }
    val first: VerseId get() = ranges.first().start
    val last: VerseId get() = ranges.last().end

    fun display(): String {
        if (ranges.size == 1) return ranges[0].display()
        // "Matthew 13:24-30, 36-43" when later ranges stay in the same book and chapter.
        val sb = StringBuilder(ranges[0].display())
        for (i in 1 until ranges.size) {
            val prev = ranges[i - 1]
            val r = ranges[i]
            val sameChapter = r.start.book == prev.end.book && r.start.chapter == prev.end.chapter &&
                r.end.chapter == r.start.chapter && r.start.book == r.end.book
            sb.append(", ")
            if (sameChapter) {
                sb.append(if (r.start == r.end) "${r.start.verse}" else "${r.start.verse}-${r.end.verse}")
            } else {
                sb.append(r.display())
            }
        }
        return sb.toString()
    }

    override fun toString(): String = display()

    companion object {
        fun of(vararg ranges: VerseRange) = Passage(ranges.toList())
    }
}

object VerseRangeSerializer : KSerializer<VerseRange> {
    override val descriptor = PrimitiveSerialDescriptor("app.bible.core.VerseRange", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: VerseRange) = encoder.encodeString(value.encode())
    override fun deserialize(decoder: Decoder): VerseRange = VerseRange.decode(decoder.decodeString())
}
