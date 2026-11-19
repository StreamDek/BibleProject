package app.bible.stories

import app.bible.core.Testament
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checks the story catalogue bundled in the app (built by ./gradlew :pipeline:buildStories). */
class BundledStoriesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "content/stories").exists() }
    private val catalogue = StoryCatalogue.decode(File(root, "composeApp/src/commonMain/composeResources/files/stories.json").readText())
    private val csvIds = File(root, "content/catalogue/bible_story_catalogue.csv").readLines().drop(1)
        .filter { it.isNotBlank() }.map { it.substringBefore(',').removePrefix("﻿") }

    @Test fun everyCatalogueRowIsInExactlyOneStory() {
        val sources = catalogue.stories.flatMap { it.sources }
        assertEquals(652, csvIds.size)
        assertEquals(csvIds.toSet(), sources.toSet())
        assertEquals(sources.size, sources.toSet().size)
    }

    @Test fun slugsAndTitlesAreUnique() {
        val slugs = catalogue.stories.map { it.slug }
        assertEquals(slugs.size, slugs.toSet().size)
        val titles = catalogue.stories.map { it.title.lowercase() }
        assertEquals(titles.size, titles.toSet().size, "Repeated titles: ${titles.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    @Test fun parallelsStaySeparateAccounts() {
        val feeding = catalogue.stories.first { it.slug == "feeding-five-thousand" }
        assertEquals(listOf("Matthew", "Mark", "Luke", "John"), feeding.steps.single().accounts.map { it.label })
        // An account is one book: parallels are never merged into one passage.
        for (s in catalogue.stories) for (a in s.accounts) {
            assertTrue(a.passage.ranges.all { it.start.book == a.passage.first.book && it.end.book == a.passage.first.book }, s.slug)
        }
        assertTrue(catalogue.stories.count { it.hasParallels } >= 40)
    }

    @Test fun multiStepStoriesAreLabelledAsArranged() {
        val birth = catalogue.stories.first { it.slug == "birth-of-jesus" }
        assertTrue(birth.arranged)
        assertTrue(birth.steps.all { it.title != null })
        assertEquals(Testament.NEW, birth.testament)
    }

    @Test fun editorialTextIsShortAndSeparate() {
        for (s in catalogue.stories) {
            listOfNotNull(s.intro, s.summary).forEach { assertTrue(it.text.length in 40..600, "${s.slug}: ${it.text.length} chars") }
        }
        val without = catalogue.stories.filter { it.intro == null }.map { it.slug }
        assertTrue(without.isEmpty(), "Stories without an introduction: $without")
    }

    @Test fun shelvesAreFilled() {
        val shelves = StoryLibrary(catalogue).shelves().associate { it.title to it.stories.size }
        for (name in listOf("Start here", "Life of Jesus", "Parables", "Miracles", "Beginnings", "The Early Church")) {
            assertTrue((shelves[name] ?: 0) > 5, "$name: ${shelves[name]}")
        }
    }
}
