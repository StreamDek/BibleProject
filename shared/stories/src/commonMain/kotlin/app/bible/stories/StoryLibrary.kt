package app.bible.stories

import app.bible.core.Canon
import app.bible.core.ReferenceParser
import app.bible.core.VerseId

/** A titled row of stories on the Stories screen. */
data class Shelf(val title: String, val stories: List<Story>)

/**
 * Offline discovery over the bundled catalogue: shelves, search and lookups. Everything is in memory (a few
 * hundred small objects), so it needs no database.
 */
class StoryLibrary(catalogue: StoryCatalogue) {

    /** All stories in "Journey through the Bible" order. */
    val stories: List<Story> = catalogue.stories.sortedBy { it.order }

    private val bySlug: Map<String, Story> = stories.associateBy { it.slug }

    operator fun get(slug: String): Story? = bySlug[slug]

    /** The story after [slug] in journey order, for "Next story" on the end card. */
    fun next(slug: String): Story? {
        val i = stories.indexOfFirst { it.slug == slug }
        return if (i < 0) null else stories.getOrNull(i + 1)
    }

    /**
     * The story to offer while reading [verse] in the Bible ("Part of: ..."). Multi-step and parallel stories are
     * preferred, then the shortest story, so the most specific match wins.
     */
    fun storyFor(verse: VerseId): Story? = stories.asSequence()
        .filter { it.kind == StoryKind.Narrative && verse in it }
        .minWithOrNull(compareBy<Story>({ if (it.steps.size > 1 || it.hasParallels) 0 else 1 }, { span(it) }))

    private fun span(s: Story): Int = s.accounts.sumOf { a -> a.passage.ranges.sumOf { it.end.value - it.start.value } }

    fun shelves(): List<Shelf> = buildList {
        fun add(title: String, list: List<Story>) { if (list.isNotEmpty()) add(Shelf(title, list)) }
        add("Start here", stories.filter { Category.Featured in it.categories })
        add("Life of Jesus", stories.filter { Category.LifeOfJesus in it.categories })
        add("Parables", stories.filter { Category.Parable in it.categories })
        add("Miracles", stories.filter { Category.Miracle in it.categories })
        for (p in Period.entries) {
            if (p == Period.Jesus) continue // already the "Life of Jesus" shelf
            add(p.title, stories.filter { it.period == p })
        }
    }

    /**
     * Searches titles, books and references. A query that is a Scripture reference ("Luke 2", "John 3:16")
     * finds the stories that contain it.
     */
    fun search(query: String): List<Story> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        ReferenceParser.parseOrNull(q)?.let { p ->
            val hits = stories.filter { s -> s.accounts.any { a -> a.passage.ranges.any { r -> p.ranges.any { it.overlaps(r) } } } }
            if (hits.isNotEmpty()) return hits
        }
        val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return stories
            .map { s -> s to score(s, words) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Story, Int>> { it.second }.thenBy { it.first.order })
            .map { it.first }
    }

    private fun score(s: Story, words: List<String>): Int {
        val title = s.title.lowercase()
        val books = s.accounts.map { Canon.book(it.passage.first.book).name.lowercase() }.distinct()
        val steps = s.steps.mapNotNull { it.title?.lowercase() }
        var total = 0
        for (w in words) {
            val hit = when {
                title.split(' ', '\'', '-').any { it.startsWith(w) } -> 3
                w in title -> 2
                books.any { it.startsWith(w) } -> 2
                steps.any { w in it } -> 1
                s.intro?.text?.lowercase()?.contains(w) == true -> 1
                else -> return 0 // every word must match somewhere
            }
            total += hit
        }
        return total
    }
}
