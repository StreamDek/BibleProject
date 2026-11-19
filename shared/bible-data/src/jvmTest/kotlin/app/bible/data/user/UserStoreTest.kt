package app.bible.data.user

import app.bible.core.VerseRange
import app.bible.core.VerseId
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserStoreTest {
    private var clock = 1_000L
    private val store = run {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        UserDatabase.Schema.create(driver)
        UserStore(UserDatabase(driver), Dispatchers.Unconfined) { clock++ }
    }
    private val john316 = VerseId.of(43, 3, 16)
    private val john317 = VerseId.of(43, 3, 17)

    @Test fun positionAndSettingsPersist() = runTest {
        assertNull(store.position())
        store.savePosition(john316)
        store.savePosition(john317)
        assertEquals(john317, store.position())
        store.putSetting("density", "7")
        assertEquals("7", store.setting("density"))
    }

    @Test fun highlightsBookmarksAndNotes() = runTest {
        val john3 = VerseRange.chapter(43, 3)
        store.setHighlight(listOf(john316, john317), 2)
        assertTrue(store.toggleBookmark(john316))
        store.setNote(john316, "  God so loved  ")
        var marks = store.marks(john3)
        assertEquals(mapOf(john316 to 2, john317 to 2), marks.highlights)
        assertEquals(setOf(john316), marks.bookmarks)
        assertEquals("God so loved", marks.notes[john316])

        store.setHighlight(listOf(john317), null)
        assertFalse(store.toggleBookmark(john316))
        store.setNote(john316, "Edited")
        store.setNote(john317, "   ") // blank: nothing saved
        marks = store.marks(john3)
        assertEquals(mapOf(john316 to 2), marks.highlights)
        assertTrue(marks.bookmarks.isEmpty())
        assertEquals(mapOf(john316 to "Edited"), marks.notes)
        assertEquals(1, store.notes().size)
        // Marks outside the range are not returned.
        assertTrue(store.marks(VerseRange.chapter(43, 4)).highlights.isEmpty())
    }

    @Test fun everyChangeBumpsTheRevision() = runTest {
        val before = store.revision.value
        store.setHighlight(listOf(john316), 0)
        store.toggleBookmark(john316)
        store.setNote(john316, "x")
        assertEquals(before + 3, store.revision.value)
    }
}
