package app.bible.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanonTest {
    @Test fun canonHas66BooksAnd31102Verses() {
        assertEquals(66, Canon.books.size)
        assertEquals(1189, Canon.books.sumOf { it.chapterCount })
        assertEquals(31102, Canon.totalVerses)
    }

    @Test fun verseIdEncoding() {
        val id = VerseId.of(40, 2, 8)
        assertEquals(40002008, id.value)
        assertEquals(40, id.book); assertEquals(2, id.chapter); assertEquals(8, id.verse)
        assertEquals("Matthew 2:8", id.display())
        assertEquals(id, VerseId.parseOsis("Matt.2.8"))
        assertNull(VerseId.parseOsis("Matt.2.99"))
    }

    @Test fun navigationCrossesChaptersAndBooks() {
        assertEquals(VerseId.of(1, 2, 1), Canon.next(VerseId.of(1, 1, 31)))
        assertEquals(VerseId.of(2, 1, 1), Canon.next(VerseId.of(1, 50, 26)))
        assertNull(Canon.next(VerseId.of(66, 22, 21)))
        assertEquals(VerseId.of(1, 50, 26), Canon.previous(VerseId.of(2, 1, 1)))
        assertNull(Canon.previous(VerseId.of(1, 1, 1)))
    }

    @Test fun validity() {
        assertTrue(Canon.isValid(VerseId.of(19, 119, 176)))
        assertFalse(Canon.isValid(VerseId.of(19, 151, 1)))
        assertFalse(Canon.isValid(VerseId.of(67, 1, 1)))
    }
}
