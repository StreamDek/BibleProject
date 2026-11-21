package app.bible.pipeline.study

import app.bible.core.VerseId
import app.bible.data.study.StudyStore
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fixtures for people, places (STEP Bible TIPNR) and cross-references (OpenBible.info) in study.db. */
class EntityAndCrossRefTest {
    private val db = File("composeApp/src/commonMain/composeResources/files/study.db")
    private val store = StudyStore(JdbcSqliteDriver("jdbc:sqlite:${db.absolutePath}", Properties().apply { put("open_mode", "1") }), Dispatchers.Unconfined)

    @Test fun abrahamIsOnePersonWithHisFamily() = runBlocking {
        // H0085 Abraham, H0087 Abram and G0011 Ἀβραάμ are the same man.
        val byId = store.entitiesFor(listOf("H0085", "H0087", "G0011"))
        assertEquals(1, byId.values.map { it.id }.toSet().size)
        val abraham = byId.getValue("H0085")
        assertEquals("person", abraham.kind)
        assertEquals("Male", abraham.type)
        val links = store.entityLinks(abraham.id)
        assertEquals(listOf("Terah"), links.getValue("parent").map { it.name })
        assertTrue(links.getValue("child").map { it.name }.containsAll(listOf("Isaac", "Ishmael")))
        assertTrue(links.getValue("partner").map { it.name }.contains("Sarah"))
        assertTrue(abraham.verses > 200)
        assertTrue("abraham-tested-with-isaac" in store.entityStories(abraham.id))
        // TIPNR's AI-adapted articles are not shipped: only its data-built summary.
        assertTrue(abraham.summary!!.startsWith("A man living at the time of the Patriarchs"))
    }

    @Test fun namesakesStayApartAndPlacesHaveAPosition() = runBlocking {
        val bethlehems = store.entitiesByName("Bethlehem").filter { it.name == "Bethlehem" }
        assertTrue(bethlehems.size >= 2, "Bethlehem in Judah and Bethlehem in Zebulun")
        val judah = bethlehems.maxBy { it.verses }
        assertEquals("place", judah.kind)
        assertEquals(31.7, judah.lat!!, 0.1)
        assertTrue(store.search("Bethlehem").entities.any { it.id == judah.id })
    }

    @Test fun crossReferencesAreRankedByVotes() = runBlocking {
        val refs = store.crossRefs(VerseId.of(43, 3, 16), 10)
        assertEquals(VerseId.of(45, 5, 8), refs.first().start) // Romans 5:8, the most voted
        assertTrue(refs.zipWithNext().all { (a, b) -> a.votes >= b.votes })
        assertTrue(refs.any { it.end != it.start }, "ranges are kept")
    }
}
