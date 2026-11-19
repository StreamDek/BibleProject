package app.bible.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReferenceParserTest {
    private fun r(b: Int, c1: Int, v1: Int, c2: Int, v2: Int) = VerseRange(VerseId.of(b, c1, v1), VerseId.of(b, c2, v2))
    private fun p(s: String) = ReferenceParser.parse(s)

    @Test fun wholeChapter() = assertEquals(Passage.of(r(1, 3, 1, 3, 24)), p("Genesis 3"))
    @Test fun chapterRange() = assertEquals(Passage.of(r(1, 13, 1, 14, 24)), p("Genesis 13-14"))
    @Test fun singleVerse() = assertEquals(Passage.of(r(43, 3, 16, 3, 16)), p("John 3:16"))
    @Test fun verseRange() = assertEquals(Passage.of(r(42, 2, 8, 2, 14)), p("Luke 2:8-14"))
    @Test fun crossChapter() = assertEquals(Passage.of(r(1, 1, 1, 2, 3)), p("Genesis 1:1-2:3"))
    @Test fun gapWithinChapter() =
        assertEquals(Passage.of(r(40, 13, 24, 13, 30), r(40, 13, 36, 13, 43)), p("Matthew 13:24-30,36-43"))
    @Test fun semicolonContinuesBook() =
        assertEquals(Passage.of(r(42, 1, 26, 1, 38), r(42, 2, 1, 2, 20)), p("Luke 1:26-38; 2:1-20"))
    @Test fun semicolonNewBook() =
        assertEquals(Passage.of(r(23, 7, 14, 7, 14), r(33, 5, 2, 5, 2)), p("Isaiah 7:14; Micah 5:2"))
    @Test fun numberedBooksAndAliases() {
        assertEquals(p("1 Samuel 17"), p("1Sam 17"))
        assertEquals(p("1 Samuel 17"), p("I Samuel 17"))
        assertEquals(p("Psalms 23"), p("Psalm 23"))
        assertEquals(p("Song of Solomon 2"), p("Song of Songs 2"))
        assertEquals(p("Revelation 21:1-8"), p("Rev. 21:1-8"))
    }
    @Test fun singleChapterBooks() {
        assertEquals(Passage.of(r(65, 1, 3, 1, 3)), p("Jude 3"))
        assertEquals(Passage.of(r(65, 1, 1, 1, 25)), p("Jude 1"))
        assertEquals(Passage.of(r(31, 1, 1, 1, 21)), p("Obadiah"))
    }
    @Test fun enDash() = assertEquals(p("Matthew 1:18-25"), p("Matthew 1:18–25"))
    @Test fun rejectsInvalid() {
        assertNull(ReferenceParser.parseOrNull("Genesis 51"))
        assertNull(ReferenceParser.parseOrNull("John 3:99"))
        assertNull(ReferenceParser.parseOrNull("Hezekiah 1:1"))
        assertFailsWith<IllegalArgumentException> { p("Luke 2:20-8") }
    }
    @Test fun displayRoundTrips() {
        for (s in listOf("Luke 2:8-14", "Genesis 1:1-2:3", "Psalms 23", "Genesis 13-14", "Jude", "Matthew 13:24-30, 36-43", "John 3:16")) {
            assertEquals(s, p(s).display())
        }
    }
}
