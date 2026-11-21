package app.bible.pipeline.study

import app.bible.core.VerseId
import app.bible.data.study.Language
import app.bible.data.study.StrongsNumber
import app.bible.data.study.StudyStore
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.Normalizer
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fixtures for the original-language text in study.db, checked against STEP Bible's TAHOT/TAGNT rows. */
class OriginalTextTest {
    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
    private fun fold(s: String) = app.bible.data.study.Fold.fold(s)
    private val db = File("composeApp/src/commonMain/composeResources/files/study.db")
    private val store = StudyStore(JdbcSqliteDriver("jdbc:sqlite:${db.absolutePath}", Properties().apply { put("open_mode", "1") }), Dispatchers.Unconfined)

    @Test fun genesisOneOneHebrew() = runBlocking {
        val v = store.original(VerseId.of(1, 1, 1))!!
        assertEquals(Language.Hebrew, v.language)
        assertEquals(7, v.words.size)
        val first = v.words[0]
        assertEquals(nfc("בְּרֵאשִׁ֖ית"), nfc(first.text)) // the prefix separator is removed, the points and accent kept
        assertEquals(listOf("H9003", "H7225G"), first.parts.map { it.stepId })
        assertEquals("H7225G", first.root?.stepId)
        assertEquals(listOf("HR", "HNcfsa"), first.parts.map { it.grammar })
        assertEquals("in beginning", first.gloss)
        assertEquals(listOf(7225), first.nums) // H9003 is STEP's own prefix number, not a Strong's entry
        // The sense this verse uses, which the KJV's tags alone cannot tell.
        assertEquals(listOf("H0430G"), v.sensesOf(430))
        assertEquals(listOf("H1254A"), v.sensesOf(1254))
        // Punctuation is not a part.
        assertEquals(listOf("H9009", "H0776G"), v.words[6].parts.map { it.stepId })
    }

    @Test fun johnThreeSixteenGreek() = runBlocking {
        val v = store.original(VerseId.of(43, 3, 16))!!
        assertEquals(Language.Greek, v.language)
        assertEquals(nfc("ἠγάπησεν"), nfc(v.words[2].text))
        assertEquals("ēgapēsen", v.words[2].xlit)
        assertEquals("V-AAI-3S", v.words[2].root?.grammar)
        assertEquals(listOf(StrongsNumber.of('G', 25)), v.words[2].nums)
        assertTrue(v.words.any { fold(it.text) == fold("μονογενῆ") })
        val m = store.morph(listOf("V-AAI-3S", "HVqp3ms"))
        assertEquals("Verb Aorist Active Indicative 3rd Singular", m.getValue("V-AAI-3S").summary)
        assertTrue(m.getValue("HVqp3ms").summary!!.startsWith("Verb : Qal"))
    }

    @Test fun theKjvsGreekTextIsTheOneShown() = runBlocking {
        // Matthew 1:25 "her firstborn son" is in the Textus Receptus (type K) but not in Nestle-Aland.
        val v = store.original(VerseId.of(40, 1, 25))!!
        assertTrue(v.words.any { fold(it.text).startsWith(fold("πρωτότοκον")) }, "TR words are included")
        // 1 John 5:7, the Johannine Comma, is in the KJV's Greek.
        assertTrue(store.original(VerseId.of(62, 5, 7))!!.words.any { fold(it.text).startsWith(fold("πατηρ")) })
        // Where the Traditional text has another word, the note says so.
        val mt530 = store.original(VerseId.of(40, 5, 30))!!.words.first { it.traditionalDiffers }
        assertTrue("TR" in mt530.note, mt530.note)
    }

    @Test fun everyKjvVerseHasItsOriginalAndAramaicIsLabelled() = runBlocking {
        // Daniel 2:4b onwards is Aramaic.
        val dan = store.original(VerseId.of(27, 2, 5))!!
        assertTrue(dan.words.all { it.language == Language.Aramaic }, "Daniel 2:5 is Aramaic")
        // KJV numbering: Malachi 4 exists (Hebrew Bibles number it 3:19-24).
        assertTrue(store.original(VerseId.of(39, 4, 6)) != null)
        // Psalm 51:1 in KJV numbering is the prayer, not the title.
        assertEquals(listOf(2603), store.original(VerseId.of(19, 51, 1))!!.words.first().nums) // חָנֵּנִי "be gracious to me"
        val counts = store.senseCounts(listOf("H0430G", "H0430H"))
        assertTrue(counts.getValue("H0430G") > 2000 && counts.getValue("H0430H") < 100, counts.toString())
    }
}
