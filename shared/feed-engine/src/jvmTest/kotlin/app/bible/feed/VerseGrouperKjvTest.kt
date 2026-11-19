package app.bible.feed

import app.bible.core.Canon
import app.bible.core.ReferenceParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Golden and invariant tests over the whole KJV. */
class VerseGrouperKjvTest {
    private val units = KjvFixture.units
    private val grouper = VerseGrouper()

    @Test fun fixtureCoversTheWholeCanon() = assertEquals(Canon.totalVerses, units.size)

    @Test fun invariantsHoldForEveryDensity() {
        for (density in Density.Options) {
            val g = VerseGrouper.forDensity(density)
            val cards = g.group(units, density)
            // 1. Every verse appears exactly once, in order.
            var expected = 0
            for (c in cards) { assertEquals(expected, c.first, "$density gap at ${units[expected].id}"); expected = c.last + 1 }
            assertEquals(units.size, expected)
            for (c in cards) {
                val first = units[c.first].id; val last = units[c.last].id
                // 2. Never crosses a chapter.
                assertTrue(first.book == last.book && first.chapter == last.chapter, "$density crosses chapter: $first-$last")
                // 3. Fits the budget unless it is a single verse.
                val chars = (c.first..c.last).sumOf { units[it].chars }
                assertTrue(c.size == 1 || chars <= g.hardBudget, "$density over budget: $first-$last ($chars)")
                // 4. Size window for fixed densities.
                if (density is Density.Fixed) assertTrue(c.size <= maxOf(1, density.verses * 2), "$density too big: $first-$last")
            }
        }
    }

    @Test fun verseByVerseIsExactlyOneVerse() {
        val cards = grouper.group(units, Density.Fixed(1))
        assertEquals(units.size, cards.size)
    }

    @Test fun midSentenceBreaksAreRareAndMarked() {
        val rates = mutableMapOf<Density, Double>()
        for (density in Density.Options.filter { it != Density.Fixed(1) }) {
            val cards = VerseGrouper.forDensity(density).group(units, density)
            val midSentence = cards.count { units[it.last].weightAfter == Boundary.NONE }
            val unmarked = cards.count { units[it.last].weightAfter == Boundary.NONE && !it.continues }
            val rate = midSentence.toDouble() / cards.size
            println("$density: ${cards.size} cards, avg ${"%.2f".format(units.size.toDouble() / cards.size)} verses, " +
                "mid-sentence ends ${"%.2f".format(rate * 100)}%, unmarked $unmarked")
            val samples = cards.filter { units[it.last].weightAfter == Boundary.NONE }.take(6)
                .joinToString { "${units[it.first].id}-${units[it.last].id.verse}" }
            println("   e.g. $samples")
            rates[density] = rate
            assertEquals(0, unmarked, "$density has unmarked mid-sentence ends")
        }
        rates.forEach { (d, r) -> assertTrue(r < 0.03, "$d ends mid-sentence too often: $r") }
    }

    @Test fun luke2AtFiveVersesGolden() {
        val p = ReferenceParser.parse("Luke 2")
        val u = KjvFixture.range(p.first, p.last)
        val cards = grouper.group(u, Density.Default)
        val shown = cards.joinToString(" | ") { "${u[it.first].id.verse}-${u[it.last].id.verse}" }
        println("Luke 2 @5: $shown")
        assertEquals(GOLDEN_LUKE_2, shown)
    }

    @Test fun storyEndingAfterSixVersesStaysTogetherInAuto() {
        // Auto groups by paragraph: the angel's announcement to the shepherds is not split mid-sentence.
        val p = ReferenceParser.parse("Luke 2:8-20")
        val u = KjvFixture.range(p.first, p.last)
        val cards = grouper.group(u, Density.Auto)
        assertTrue(cards.all { u[it.last].weightAfter >= Boundary.SENTENCE || it.last == u.lastIndex })
    }

    companion object {
        // Locked after review. Cards start at our section breaks (2:8 shepherds, 2:21 presentation, 2:25 Simeon,
        // 2:36 Anna, 2:39 Nazareth, 2:41 the boy Jesus in the temple) and otherwise end on paragraph or sentence ends.
        const val GOLDEN_LUKE_2 = "1-3 | 4-7 | 8-12 | 13-17 | 18-20 | 21-24 | 25-32 | 33-35 | 36-38 | 39-40 | 41-45 | 46-48 | 49-52"
    }
}
