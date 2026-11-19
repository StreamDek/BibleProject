package app.bible.stories

import app.bible.core.Canon
import app.bible.core.Passage
import app.bible.core.ReferenceParser
import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.feed.Boundary
import app.bible.feed.Density
import app.bible.feed.VerseGrouper
import app.bible.feed.VerseUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StoryPagesTest {

    private fun p(ref: String): Passage = ReferenceParser.parse(ref)

    /** Every verse 100 characters, a sentence end after every verse, a chapter break at chapter ends. */
    private fun units(range: VerseRange): List<VerseUnit> = range.verses().map { v ->
        val last = v.verse == Canon.book(v.book).verseCount(v.chapter)
        VerseUnit(v, 100, if (last) Boundary.CHAPTER else Boundary.SENTENCE)
    }.toList()

    private val feeding = Story(
        slug = "feeding-five-thousand",
        title = "Feeding the five thousand",
        period = Period.Jesus,
        steps = listOf(StoryStep(null, listOf(StoryAccount(p("Matthew 14:13-21")), StoryAccount(p("Mark 6:30-44"))))),
        summary = EditorialBlock("Summary"),
        order = 1,
    )

    private val birth = Story(
        slug = "birth-of-jesus",
        title = "The Birth of Jesus",
        period = Period.Jesus,
        steps = listOf(
            StoryStep("The angel visits Mary", listOf(StoryAccount(p("Luke 1:26-38")))),
            StoryStep("Joseph's dream", listOf(StoryAccount(p("Matthew 1:18-25")))),
        ),
        arranged = true,
        order = 0,
    )

    private suspend fun build(story: Story, choices: Map<Int, Int> = emptyMap()) =
        StoryPages.build(story, choices, Density.Fixed(5), VerseGrouper()) { units(it) }

    @Test fun introScriptureSummaryEnd() = runTest {
        val pages = build(feeding)
        assertIs<StoryPage.Intro>(pages.first())
        assertIs<StoryPage.End>(pages.last())
        assertIs<StoryPage.Summary>(pages[pages.lastIndex - 1])
        val scripture = pages.filterIsInstance<StoryPage.Scripture>()
        // The primary account (Matthew) by default, covering it exactly, with no other book.
        assertEquals(VerseId.of(40, 14, 13), scripture.first().first)
        assertEquals(VerseId.of(40, 14, 21), scripture.last().last)
        assertTrue(scripture.all { it.first.book == 40 && it.account == 0 })
        assertTrue(scripture.first().stepStart)
        assertTrue(scripture.drop(1).none { it.stepStart })
    }

    @Test fun switchingAccountShowsOnlyThatGospel() = runTest {
        val pages = build(feeding, mapOf(0 to 1))
        val scripture = pages.filterIsInstance<StoryPage.Scripture>()
        assertEquals(VerseId.of(41, 6, 30), scripture.first().first)
        assertEquals(VerseId.of(41, 6, 44), scripture.last().last)
        assertTrue(scripture.all { it.first.book == 41 && it.last.book == 41 })
    }

    @Test fun cardsNeverCrossSteps() = runTest {
        val pages = build(birth).filterIsInstance<StoryPage.Scripture>()
        for (page in pages) {
            val step = birth.steps[page.step].primary.passage
            assertTrue(page.first in step && page.last in step, "$page leaks out of its step")
        }
        assertEquals(listOf(0, 1), pages.filter { it.stepStart }.map { it.step })
    }

    @Test fun gapsInAPassageAreHardBreaks() = runTest {
        val wheat = feeding.copy(steps = listOf(StoryStep(null, listOf(StoryAccount(p("Matthew 13:24-30,36-43"))))))
        val pages = build(wheat).filterIsInstance<StoryPage.Scripture>()
        assertTrue(pages.none { VerseId.of(40, 13, 30) in it.range && VerseId.of(40, 13, 36) in it.range })
        assertTrue(pages.none { p -> (31..35).any { VerseId.of(40, 13, it) in p.range } })
    }

    @Test fun resumeAndAccountChoiceFromAVerse() = runTest {
        val pages = build(feeding, StoryPages.choicesFor(feeding, VerseId.of(41, 6, 41)))
        val i = StoryPages.pageOf(pages, VerseId.of(41, 6, 41))
        val page = pages[i]
        assertIs<StoryPage.Scripture>(page)
        assertEquals(1, page.account)
        assertEquals(0, StoryPages.pageOf(pages, VerseId.of(1, 1, 1)))
        assertEquals(0, StoryPages.pageOf(pages, null))
        assertEquals(1, StoryPages.firstPageOfStep(pages, 0))
    }

    @Test fun catalogueRoundTrip() {
        val c = StoryCatalogue(1, listOf(birth, feeding))
        assertEquals(c, StoryCatalogue.decode(c.encode()))
    }

    @Test fun searchAndStoryForVerse() {
        val single = Story(
            slug = "feeding-alone", title = "Five loaves", period = Period.Jesus,
            steps = listOf(StoryStep(null, listOf(StoryAccount(p("Mark 6:30-52"))))), order = 2,
        )
        val lib = StoryLibrary(StoryCatalogue(1, listOf(feeding, birth, single)))
        assertEquals("birth-of-jesus", lib.stories.first().slug)
        assertEquals(listOf("feeding-five-thousand"), lib.search("five thou").map { it.slug })
        assertEquals("feeding-five-thousand", lib.search("Mark 6:35").first().slug)
        assertTrue(lib.search("zzz").isEmpty())
        // A parallel story is preferred to a plain one covering the same verse.
        assertEquals("feeding-five-thousand", lib.storyFor(VerseId.of(41, 6, 35))?.slug)
        assertEquals("feeding-alone", lib.storyFor(VerseId.of(41, 6, 50))?.slug)
        assertEquals("feeding-five-thousand", lib.next("birth-of-jesus")?.slug)
        assertEquals("Matthew 14:13-21 · Mark 6:30-44", feeding.sourcesLine())
        assertEquals("Mark", feeding.steps[0].accounts[1].label)
    }
}
