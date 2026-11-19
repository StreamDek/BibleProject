package app.bible.feed

import app.bible.core.VerseId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerseGrouperTest {
    private fun units(vararg weights: Int, chars: Int = 100) =
        weights.mapIndexed { i, w -> VerseUnit(VerseId.of(1, 1, i + 1), chars, w) }

    private val S = Boundary.SENTENCE
    private val N = Boundary.NONE
    private val P = Boundary.PARAGRAPH
    private val C = Boundary.CHAPTER

    @Test fun doesNotBreakMidSentenceToHitFive() {
        // Verses 1-6 are one sentence ending at verse 6: the card takes 6, not 5.
        val u = units(N, N, N, N, N, S, S, S, S, S, S, C)
        val cards = VerseGrouper().group(u, Density.Fixed(5))
        assertEquals(5, cards[0].last)
        assertTrue(cards.all { u[it.last].weightAfter >= S })
    }

    @Test fun prefersParagraphEnds() {
        val u = units(S, S, P, S, S, S, S, S, S, S, S, C)
        val cards = VerseGrouper().group(u, Density.Fixed(5))
        assertEquals(2, cards[0].last) // ends at the paragraph after 3 verses (within 3..7)
    }

    @Test fun neverCrossesChapters() {
        val u = units(S, S, C, S, S, C)
        val cards = VerseGrouper().group(u, Density.Fixed(10))
        assertEquals(listOf(CardSpan(0, 2), CardSpan(3, 5)), cards)
    }

    @Test fun respectsCharacterBudget() {
        val u = units(S, S, S, S, S, S, S, S, S, C, chars = 300)
        val cards = VerseGrouper(charBudget = 700).group(u, Density.Fixed(5))
        assertTrue(cards.all { it.size <= 2 })
    }

    @Test fun overlongSentenceIsMarkedContinues() {
        val u = units(N, N, N, N, N, N, N, N, N, N, N, N, N, N, S, C, chars = 200)
        val cards = VerseGrouper(charBudget = 700).group(u, Density.Fixed(5))
        assertTrue(cards.first().continues)
    }

    @Test fun avoidsTinyLastCard() {
        val u = units(S, S, S, S, S, S, C) // 7 verses at density 5: one card of 7, not 5 + 2
        assertEquals(listOf(CardSpan(0, 6)), VerseGrouper().group(u, Density.Fixed(5)))
    }

    @Test fun autoFollowsParagraphs() {
        val u = units(S, S, P, S, P, S, S, S, C)
        val cards = VerseGrouper().group(u, Density.Auto)
        assertEquals(listOf(CardSpan(0, 2), CardSpan(3, 4), CardSpan(5, 8)), cards)
    }
}
