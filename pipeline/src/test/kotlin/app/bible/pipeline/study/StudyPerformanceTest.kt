package app.bible.pipeline.study

import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.data.study.StudyStore
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Measured timings for the study pack's hot paths on the JVM. A phone is slower than a CI runner, so the limits
 * here are a fifth of the spec's on-device targets (word sheet under 150 ms, search under 250 ms, occurrences page
 * under 200 ms). Results are printed so a regression shows in the CI log before it fails.
 */
class StudyPerformanceTest {
    private val db = File("composeApp/src/commonMain/composeResources/files/study.db")
    private val store = StudyStore(JdbcSqliteDriver("jdbc:sqlite:${db.absolutePath}", Properties().apply { put("open_mode", "1") }), Dispatchers.Unconfined)

    private fun median(runs: Int = 15, block: suspend () -> Unit): Double = runBlocking {
        repeat(3) { block() } // warm the page cache and statement cache
        val times = (1..runs).map { val t = System.nanoTime(); block(); (System.nanoTime() - t) / 1e6 }
        times.sorted()[runs / 2]
    }

    @Test fun hotPathsAreFast() {
        val sheet = median {
            // Everything the word sheet loads for one word.
            store.strongs(430); store.stepSenses(430); store.renderings(430, 24); store.distribution(430)
            store.derivedFrom(430); store.derivatives(430)
        }
        val tags = median { store.tags(VerseRange.single(VerseId.of(43, 3, 16))) }
        val queries = listOf("love", "H430", "G26", "agape", "chesed", "אלהים", "λογος", "John 3:16", "lovingkindnees", "creat")
        val search = queries.associateWith { q -> median(7) { store.search(q) } }
        val occurrences = median { store.occurrences(100026, null, 30, null) }
        println("study perf (ms): sheet=%.2f tags=%.2f occurrences=%.2f search=%s".format(sheet, tags, occurrences, search.mapValues { "%.1f".format(it.value) }))
        assertTrue(sheet < 30, "word sheet data took $sheet ms")
        assertTrue(tags < 10, "verse tags took $tags ms")
        assertTrue(occurrences < 40, "occurrence page took $occurrences ms")
        for ((q, t) in search) assertTrue(t < 50, "search \"$q\" took $t ms")
    }
}
