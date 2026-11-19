package app.bible.stories

import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.feed.Boundary
import app.bible.feed.Density
import app.bible.feed.VerseGrouper
import app.bible.feed.VerseUnit

/**
 * One page of a story in the shared vertical feed (StorySource, proposal §3.2): an introduction, Scripture cards
 * in narrative order, a summary and a calm end card. Interludes are not Scripture and are drawn differently.
 */
sealed interface StoryPage {
    /** Stable identity for the pager. */
    val key: String

    data class Intro(val story: Story) : StoryPage {
        override val key: String get() = "intro"
    }

    /** Canonical verses [first]..[last] from one account of one step. */
    data class Scripture(
        val step: Int,
        val account: Int,
        val first: VerseId,
        val last: VerseId,
        val continues: Boolean,
        /** True for the first card of a step, which shows the step title. */
        val stepStart: Boolean,
    ) : StoryPage {
        override val key: String get() = "s$step-a$account-${first.value}-${last.value}"
        val range: VerseRange get() = VerseRange(first, last)
    }

    data class Summary(val story: Story) : StoryPage {
        override val key: String get() = "summary"
    }

    data class End(val story: Story) : StoryPage {
        override val key: String get() = "end"
    }
}

/**
 * Builds the pages of a story for the reader's density and text size, using the same [VerseGrouper] as free
 * reading. Each passage range is grouped on its own, so a card never runs from one account or range into the
 * next (a story-segment break, weight 4).
 */
object StoryPages {

    /**
     * @param choices the account chosen for each step (step index -> account index); missing steps use the primary.
     * @param units the grouper's view of a verse range, from the translation database.
     */
    suspend fun build(
        story: Story,
        choices: Map<Int, Int>,
        density: Density,
        grouper: VerseGrouper,
        units: suspend (VerseRange) -> List<VerseUnit>,
    ): List<StoryPage> = buildList {
        add(StoryPage.Intro(story))
        story.steps.forEachIndexed { stepIndex, step ->
            val accountIndex = choices[stepIndex]?.takeIf { it in step.accounts.indices } ?: 0
            var first = true
            for (range in step.accounts[accountIndex].passage.ranges) {
                val u = units(range)
                if (u.isEmpty()) continue
                // The end of a range is a hard break even when it is mid-chapter.
                val bounded = u.toMutableList().also { it[it.lastIndex] = it.last().copy(weightAfter = Boundary.CHAPTER) }
                for (span in grouper.group(bounded, density)) {
                    add(
                        StoryPage.Scripture(
                            step = stepIndex,
                            account = accountIndex,
                            first = bounded[span.first].id,
                            last = bounded[span.last].id,
                            continues = span.continues,
                            stepStart = first,
                        ),
                    )
                    first = false
                }
            }
        }
        if (story.summary != null) add(StoryPage.Summary(story))
        add(StoryPage.End(story))
    }

    /** The page to open at [verse] (a saved position), or 0 (the introduction) if it is not on any page. */
    fun pageOf(pages: List<StoryPage>, verse: VerseId?): Int {
        if (verse == null) return 0
        val i = pages.indexOfFirst { it is StoryPage.Scripture && verse >= it.first && verse <= it.last }
        return if (i < 0) 0 else i
    }

    /** The first page of [step], for switching accounts without losing the step. */
    fun firstPageOfStep(pages: List<StoryPage>, step: Int): Int =
        pages.indexOfFirst { it is StoryPage.Scripture && it.step == step }.takeIf { it >= 0 } ?: 0

    /**
     * Account choices that show [verse]: for each step, the account containing it (so opening a story from
     * Mark 6:41 shows Mark's account of the feeding, not Matthew's).
     */
    fun choicesFor(story: Story, verse: VerseId, current: Map<Int, Int> = emptyMap()): Map<Int, Int> {
        val out = current.toMutableMap()
        story.steps.forEachIndexed { s, step ->
            val a = step.accounts.indexOfFirst { verse in it.passage }
            if (a >= 0) out[s] = a
        }
        return out
    }

    /** Rough reading time: about 1,000 characters of KJV a minute, plus a little for the interludes. */
    fun minutes(characters: Int, interludes: Int): Int = maxOf(1, (characters / 1_000.0 + interludes * 0.3 + 0.5).toInt())
}
