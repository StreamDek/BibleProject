package app.bible.feed

import app.bible.core.VerseId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Strength of the boundary after a verse. Higher is a better place to end a card. */
object Boundary {
    const val NONE = 0      // mid-sentence or mid-quotation: never break here if avoidable
    const val SENTENCE = 1  // sentence ends
    const val PARAGRAPH = 2 // paragraph or stanza ends
    const val SECTION = 3   // editorial section (pericope) or story segment ends
    const val CHAPTER = 4   // chapter ends: cards never cross this
}

/** What the grouper needs to know about one verse. Text itself is not needed. */
data class VerseUnit(val id: VerseId, val chars: Int, val weightAfter: Int)

/** How many verses a reader wants per card. */
sealed interface Density {
    data class Fixed(val verses: Int) : Density {
        init { require(verses >= 1) }
    }
    /** Group by natural narrative units (paragraphs / sections), split only when too long. */
    data object Auto : Density

    companion object {
        val Default: Density = Fixed(5)
        val Options: List<Density> = listOf(Fixed(1), Fixed(3), Fixed(5), Fixed(10), Auto)
    }
}

/**
 * A card's verses: indices into the unit list, inclusive. [continues] is true when the card had to end
 * mid-sentence because a single sentence was longer than a card can hold.
 */
data class CardSpan(val first: Int, val last: Int, val continues: Boolean = false) {
    val size: Int get() = last - first + 1
}

/**
 * Splits a run of verses into cards.
 *
 * Rules, in priority order:
 *  1. A card never crosses a chapter boundary ([Boundary.CHAPTER]).
 *  2. A card aims to fit [charBudget] characters. It may grow up to [hardBudget] only to reach the end of a
 *     sentence (the UI then sets the text slightly smaller). A single verse longer than that stands alone.
 *  3. A card does not end mid-sentence unless no sentence boundary exists within reach.
 *  4. Within the allowed size window, end at the strongest boundary; break ties by closeness to the target.
 *  5. Avoid leaving a tiny final card before a chapter end.
 *
 * Deterministic: the same inputs always produce the same cards, so card identity
 * (translation, density, first verse) is stable across launches.
 */
class VerseGrouper(
    val charBudget: Int = DEFAULT_CHAR_BUDGET,
    val hardBudget: Int = charBudget * 3 / 2,
) {

    fun group(units: List<VerseUnit>, density: Density): List<CardSpan> {
        if (units.isEmpty()) return emptyList()
        val cards = ArrayList<CardSpan>()
        // Hard segments end at chapter boundaries (and the end of the list).
        var segStart = 0
        for (i in units.indices) {
            if (units[i].weightAfter >= Boundary.CHAPTER || i == units.lastIndex) {
                groupSegment(units, segStart, i, density, cards)
                segStart = i + 1
            }
        }
        return cards
    }

    private fun groupSegment(u: List<VerseUnit>, from: Int, to: Int, density: Density, out: MutableList<CardSpan>) {
        var i = from
        while (i <= to) {
            val card = when (density) {
                is Density.Fixed -> nextFixed(u, i, to, density.verses)
                Density.Auto -> nextAuto(u, i, to)
            }
            out += card
            i = card.last + 1
        }
    }

    // ---- Fixed density -------------------------------------------------------------------------------

    private fun nextFixed(u: List<VerseUnit>, start: Int, segEnd: Int, n: Int): CardSpan {
        if (n == 1) return CardSpan(start, start) // the reader asked for verse-by-verse
        val minLen = max(1, ceil(n * 0.6).toInt())
        val maxLen = n + ceil(n * 0.4).toInt()
        val remaining = segEnd - start + 1

        // Take everything that is left if it fits comfortably (avoids a tiny last card).
        if (remaining <= maxLen && chars(u, start, segEnd) <= charBudget) return CardSpan(start, segEnd)

        val fitEnd = lastFitting(u, start, min(segEnd, start + maxLen - 1))
        val lo = start + minLen - 1
        val candidates = (min(lo, fitEnd)..fitEnd)
        var best = pickBest(u, candidates, start, n, segEnd, minLen)
        if (best != null) return CardSpan(start, best)

        // Every candidate is mid-sentence: look further (up to 2n) for the first sentence end that fits.
        val farEnd = lastFitting(u, start, min(segEnd, start + 2 * n - 1), hardBudget)
        for (e in fitEnd + 1..farEnd) if (u[e].weightAfter >= Boundary.SENTENCE) return CardSpan(start, e)
        // A single sentence longer than a card: break at the largest fitting size and mark it.
        return CardSpan(start, fitEnd, continues = u[fitEnd].weightAfter < Boundary.SENTENCE && fitEnd < segEnd)
    }

    /** Best end index among [candidates] with weight >= SENTENCE, or null if none. */
    private fun pickBest(u: List<VerseUnit>, candidates: IntRange, start: Int, target: Int, segEnd: Int, minLen: Int): Int? {
        var best: Int? = null
        var bestScore = Int.MIN_VALUE
        for (e in candidates) {
            val w = if (e == segEnd) Boundary.CHAPTER else u[e].weightAfter
            if (w < Boundary.SENTENCE) continue
            val len = e - start + 1
            var score = w * 100 - abs(len - target) * 10
            // Penalise ends that would leave fewer than minLen verses before the chapter end.
            val left = segEnd - e
            if (left in 1 until minLen) score -= 250
            if (score > bestScore) { bestScore = score; best = e }
        }
        return best
    }

    // ---- Auto (narrative) density -------------------------------------------------------------------

    private fun nextAuto(u: List<VerseUnit>, start: Int, segEnd: Int): CardSpan {
        // Extend to the end of the paragraph/section if it fits in one card.
        var e = start
        while (e < segEnd && u[e].weightAfter < Boundary.PARAGRAPH) e++
        if (chars(u, start, e) <= charBudget) return CardSpan(start, e)
        if (chars(u, start, e) <= hardBudget && e - start < 12) return CardSpan(start, e)
        // Too long: split inside the unit at the strongest boundary that fits, preferring later ones.
        val fitEnd = lastFitting(u, start, e)
        for (w in Boundary.PARAGRAPH downTo Boundary.SENTENCE) {
            for (k in fitEnd downTo start) if (u[k].weightAfter >= w && k > start + (fitEnd - start) / 3) return CardSpan(start, k)
        }
        for (k in fitEnd downTo start) if (u[k].weightAfter >= Boundary.SENTENCE) return CardSpan(start, k)
        val farEnd = lastFitting(u, start, e, hardBudget)
        for (k in fitEnd + 1..farEnd) if (u[k].weightAfter >= Boundary.SENTENCE) return CardSpan(start, k)
        return CardSpan(start, fitEnd, continues = fitEnd < e)
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private fun chars(u: List<VerseUnit>, from: Int, to: Int): Int {
        var c = 0
        for (k in from..to) c += u[k].chars
        return c
    }

    /** Largest end index <= limit such that from..end fits the budget (at least `from`). */
    private fun lastFitting(u: List<VerseUnit>, from: Int, limit: Int, budget: Int = charBudget): Int {
        var c = 0
        var end = from
        for (k in from..limit) {
            c += u[k].chars
            if (c > budget && k > from) break
            end = k
        }
        return end
    }

    companion object {
        /** Roughly what fits on a phone card at the default font size. The UI recomputes this from font size. */
        const val DEFAULT_CHAR_BUDGET = 700

        /**
         * A grouper sized for a density. Ten verses do not fit the default budget at normal type size, so
         * dense cards get a larger budget and the card renders them in slightly smaller type.
         */
        fun forDensity(density: Density, baseBudget: Int = DEFAULT_CHAR_BUDGET): VerseGrouper {
            val scale = if (density is Density.Fixed && density.verses > 5) density.verses / 5.0 else 1.0
            return VerseGrouper(charBudget = (baseBudget * scale).toInt())
        }
    }
}
