package app.bible.pipeline.study

import app.bible.core.AttributionRegistry
import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.data.study.Fold
import app.bible.data.study.StrongsNumber
import app.bible.data.study.StudyStore
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.sql.DriverManager
import java.text.Normalizer
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scholarly fixtures for the bundled study.db. Expected values were read from the upstream files themselves
 * (Strong's XML, STEP TBESH/TBESG, CrossWire kjv.osis.xml), not written from memory.
 */
class StudyPackTest {
    private val files = File("composeApp/src/commonMain/composeResources/files")
    private val db = File(files, "study.db")
    private val bible = File(files, "bible-kjv.db")
    private val store = StudyStore(JdbcSqliteDriver("jdbc:sqlite:${db.absolutePath}", Properties().apply { put("open_mode", "1") }), Dispatchers.Unconfined)

    private fun verseText(id: VerseId): String = DriverManager.getConnection("jdbc:sqlite:${bible.absolutePath}").use { c ->
        c.prepareStatement("SELECT text FROM verse WHERE id = ?").use { ps -> ps.setInt(1, id.value); ps.executeQuery().use { it.next(); it.getString(1) } }
    }

    private fun tagged(id: VerseId): List<Pair<String, List<String>>> = runBlocking {
        val text = verseText(id)
        store.tags(VerseRange.single(id)).getValue(id).map { text.substring(it.start, it.end) to it.nums.map(StrongsNumber::id) }
    }

    @Test fun strongsIdentifiersResolveWithTheirLanguage() = runBlocking {
        val g26 = store.strongs(StrongsNumber.parse("G26")!!)!!
        assertEquals("ἀγάπη", g26.lemma)
        assertEquals("agápē", g26.xlit)
        assertEquals("from G25;", g26.derivation)
        val h26 = store.strongs(StrongsNumber.parse("H26")!!)!!
        assertTrue(h26.lemma != g26.lemma, "H26 and G26 are different words")
        val h430 = store.strongs(StrongsNumber.parse("h0430")!!)!!
        assertEquals("אֱלֹהִים", h430.lemma)
        assertEquals("ʼĕlôhîym", h430.xlit)
        assertEquals("plural of H433 (אֱלוֹהַּ);", h430.derivation)
        assertEquals(listOf("H433"), store.derivedFrom(430).map { it.id })
        assertEquals("arc", store.strongs(2)!!.language.code) // H2 is the Aramaic word for father
    }

    @Test fun stepKeepsItsOwnSensesAndProvenance() = runBlocking {
        // One Strong's number, two STEP senses (polysemy is never merged).
        val bara = store.stepSenses(1254)
        assertEquals(listOf("H1254A" to "to create", "H1254B" to "to fatten"), bara.map { it.id to it.gloss })
        assertTrue(bara.all { it.meaning == null && it.sourceId == "step-tbesh" }, "Hebrew definitions are not shipped")
        val agape = store.step("G0026")!!
        assertEquals("love", agape.gloss)
        assertEquals("G:N-F", agape.morph)
        assertTrue(agape.meaning!!.startsWith("**ἀγάπη**"))
        // Distinct people with the same name stay distinct.
        assertEquals(listOf("G2264G", "G2264H", "G2264I"), store.stepSenses(StrongsNumber.parse("G2264")!!).map { it.id })
    }

    @Test fun kjvTagsKeepPhrasesAndManyToOneMappings() {
        assertEquals("In the beginning" to listOf("H7225"), tagged(VerseId.of(1, 1, 1))[0])
        // Two Hebrew words (object marker + verb) behind one English word.
        assertEquals("created" to listOf("H853", "H1254"), tagged(VerseId.of(1, 1, 1))[2])
        val john316 = tagged(VerseId.of(43, 3, 16))
        // Article + noun behind "God"; one Greek word behind "only begotten"; one Greek verb split around "not".
        assertTrue("God" to listOf("G3588", "G2316") in john316)
        assertTrue("only begotten" to listOf("G3439") in john316)
        assertTrue("should" to listOf("G622") in john316 && "perish" to listOf("G622") in john316)
        assertTrue(john316.none { it.first == "not" && it.second != listOf("G3361") })
    }

    @Test fun occurrencesAndRenderingsComeFromTheTags() = runBlocking {
        val g26 = StrongsNumber.parse("G26")!!
        val total = store.strongs(g26)!!.verses
        assertEquals(total, store.distribution(g26).sumOf { it.verses })
        var paged = 0
        var after: VerseId? = null
        while (true) {
            val page = store.occurrences(g26, after, 50)
            paged += page.size
            if (page.size < 50) break
            after = page.last()
        }
        assertEquals(total, paged)
        assertTrue(store.distribution(g26).all { it.book >= 40 }, "a Greek word only occurs in the New Testament")
        val renderings = store.renderings(g26).associate { it.text to it.count }
        assertTrue((renderings["love"] ?: 0) > (renderings["charity"] ?: 0) && (renderings["charity"] ?: 0) > 0)
        // H430 renders as "God" far more than "gods".
        val elohim = store.renderings(430).associate { it.text to it.count }
        assertTrue((elohim["god"] ?: 0) > (elohim["gods"] ?: 0))
    }

    @Test fun searchFindsEveryKindOfQuery() = runBlocking {
        assertEquals("G26", store.search("G26").strongs.first().id)
        assertEquals("H430", store.search("H0430").strongs.first().id)
        assertTrue(store.search("G2").strongs.map { it.id }.containsAll(listOf("G2", "G20", "G21")), "partial identifier")
        assertTrue(store.search("agape").strongs.any { it.id == "G26" }, "transliteration, accents ignored")
        assertTrue(store.search("elohim").strongs.any { it.id == "H430" }, "STEP transliteration")
        assertTrue(store.search("ἀγάπη").strongs.any { it.id == "G26" })
        assertTrue(store.search("αγαπη").strongs.any { it.id == "G26" }, "Greek without accents")
        assertTrue(store.search("אלהים").strongs.any { it.id == "H430" }, "Hebrew without vowel points")
        val love = store.search("love")
        assertEquals("G26", love.english.first().entry.id)
        assertTrue(love.english.none { it.entry.id == "G3588" }, "the article is not credited with English words")
        assertNotNull(store.search("John 3:16").reference)
        assertTrue(store.search("lovingkindnees").suggestions.contains("lovingkindness"))
        assertTrue(store.search("").isEmpty)
    }

    @Test fun textIsCleanUnicode() = runBlocking {
        for (id in listOf("H430", "H1254", "H7307", "G26", "G3056", "G5485")) {
            val e = store.strongs(StrongsNumber.parse(id)!!)!!
            assertEquals(Normalizer.normalize(e.lemma, Normalizer.Form.NFC), e.lemma)
            assertTrue('�' !in e.lemma && '�' !in (e.definition ?: ""))
        }
        assertEquals("αγαπη", Fold.fold("ἀγάπη"))
        assertEquals("אלהים", Fold.fold("אֱלֹהִים"))
        assertEquals("elohim", Fold.fold("e.lo.him"))
        assertEquals("λογοσ", Fold.fold("Λόγος"))
        assertNull(StrongsNumber.parse("X12"))
    }

    @Test fun everyPackSourceIsCredited() = runBlocking {
        val registry = AttributionRegistry.decode(File(files, "attributions.json").readText())
        assertEquals(emptyList(), registry.problems())
        for (s in store.sources()) assertNotNull(registry.byId(s.id), "${s.id} is not in attributions.json")
        for (r in registry.resources) r.licenceFile?.let { assertTrue(File(files, it).exists(), "missing $it") }
        // Every bundled font has an entry.
        File("composeApp/src/commonMain/composeResources/font").listFiles()!!.forEach { f ->
            val family = f.nameWithoutExtension.substringBefore("_italic").removeSuffix("_regular").removeSuffix("_semibold").replace('_', '-')
            assertTrue(registry.resources.any { it.id == "font-$family" }, "font ${f.name} has no attribution")
        }
    }
}
