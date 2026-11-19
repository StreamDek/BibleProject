package app.bible.data.bible

import app.bible.core.VerseId
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs against the bundled KJV database built by :pipeline:importKjv. */
class BibleSearchTest {
    private val dbFile = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "composeApp/src/commonMain/composeResources/files/bible-kjv.db") }
        .first { it.exists() }
    private val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties().apply { put("open_mode", "1") })
    private val search = BibleSearch(driver)

    @AfterTest fun close() = driver.close()

    @Test fun referenceQueries() {
        val r = search.search("jn 3 16")
        assertEquals(VerseId.of(43, 3, 16), assertNotNull(r.reference).first)
    }

    @Test fun wordsMustAllAppearInBibleOrder() {
        val r = search.search("God so loved", limit = 5_000)
        assertTrue(r.hits.any { it.verse == VerseId.of(43, 3, 16) })
        assertTrue(r.hits.all { h -> listOf("god", "so", "loved").all { h.text.lowercase().contains(it) } })
        assertEquals(r.hits.sortedBy { it.verse }, r.hits)
    }

    @Test fun exactPhraseAndPrefix() {
        val phrase = search.search("\"Jesus wept\"")
        assertEquals(listOf(VerseId.of(43, 11, 35)), phrase.hits.map { it.verse })
        // The last word is a prefix while typing.
        assertTrue(search.search("shepherd abid").hits.any { it.verse == VerseId.of(42, 2, 8) })
        assertTrue(search.search("Romans 8 28").reference != null)
    }

    @Test fun limitAndTotal() {
        val r = search.search("lord", limit = 10)
        assertEquals(10, r.hits.size)
        assertTrue(r.total > 6_000, "total ${r.total}")
    }

    @Test fun hostileInputIsQuotedAndSafe() {
        assertNull(BibleSearch.ftsQuery("  "))
        assertNull(BibleSearch.ftsQuery("3 16"))
        assertEquals("\"NEAR\"* \"OR\" \"x\"", BibleSearch.ftsQuery("NEAR( OR x*"))
        search.search("\"unterminated") // must not throw
        search.search("a:b OR -c ^d")
    }
}
