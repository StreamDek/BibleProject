package app.bible.data.user

import app.bible.core.VerseId
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedWordsTest {
    private var clock = 1_000L
    private val store = run {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        UserDatabase.Schema.create(driver)
        UserStore(UserDatabase(driver), Dispatchers.Unconfined) { clock++ }
    }
    private val john316 = VerseId.of(43, 3, 16)

    private fun word(entry: String, note: String = "", fav: Boolean = false) =
        SavedWord(entry, "strongs", "grc", "ἀγαπάω", "to love", john316, note, fav, 0)

    @Test fun savedWordKeepsFirstSaveTimeAndUpdatesNote() = runTest {
        store.saveWord(word("G25"))
        val first = store.savedWord("G25")!!.createdAt
        store.saveWord(word("G25", note = "  agape in John  ", fav = true))
        val again = store.savedWord("G25")!!
        assertEquals(first, again.createdAt)
        assertEquals("agape in John", again.note)
        assertTrue(again.favorite)
        assertEquals(john316, again.verse)
        store.removeSavedWord("G25")
        assertNull(store.savedWord("G25"))
    }

    @Test fun historyIsNewestFirstAndTrimmed() = runTest {
        for (i in 1..5) store.recordStudy("H$i", "heb", "x", "g", null, keep = 3)
        store.recordStudy("H3", "heb", "x", "g", john316, keep = 3)
        val recent = store.recentStudies()
        assertEquals(listOf("H3", "H5", "H4"), recent.map { it.entry })
        assertEquals(john316, recent.first().verse)
    }

    @Test fun versionOneDatabaseMigratesAndKeepsItsData() = runTest {
        // A user.db made by the app before word study (schema version 1): the tables as they were then.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        for (sql in listOf(
            "CREATE TABLE setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
            "CREATE TABLE reading_position (source TEXT NOT NULL PRIMARY KEY, verse_id INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
            "CREATE TABLE highlight (verse_id INTEGER NOT NULL PRIMARY KEY, color INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
            "CREATE TABLE bookmark (verse_id INTEGER NOT NULL PRIMARY KEY, created_at INTEGER NOT NULL)",
            "CREATE TABLE note (verse_id INTEGER NOT NULL PRIMARY KEY, body TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
            "INSERT INTO bookmark VALUES (43003016, 5)",
        )) driver.execute(null, sql, 0)
        UserDatabase.Schema.migrate(driver, 1, UserDatabase.Schema.version)
        val migrated = UserStore(UserDatabase(driver), Dispatchers.Unconfined) { clock++ }
        assertEquals(listOf(john316), migrated.bookmarks())
        migrated.saveWord(word("G26"))
        assertEquals(1, migrated.savedWords().size)
        val tables = driver.executeQuery(null, "SELECT count(*) FROM sqlite_master WHERE name IN ('saved_word','study_history')", { c ->
            c.next(); QueryResult.Value(c.getLong(0)!!)
        }, 0).value
        assertEquals(2L, tables)
    }
}
