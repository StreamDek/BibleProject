package app.bible.pipeline

import app.bible.core.ReferenceParser
import app.bible.stories.Category
import app.bible.stories.EditorialBlock
import app.bible.stories.Period
import app.bible.stories.RelatedPassage
import app.bible.stories.Relation
import app.bible.stories.Story
import app.bible.stories.StoryAccount
import app.bible.stories.StoryCatalogue
import app.bible.stories.StoryKind
import app.bible.stories.StoryStep
import java.io.File

/**
 * Builds the bundled story catalogue (stories.json) from:
 *  - content/catalogue/bible_story_catalogue.csv: one row per passage (id, testament, book, title, reference, type)
 *  - content/stories/curation.tsv: hand-built stories (steps, parallel accounts, related passages), renames, shelves
 *  - content/stories/editorial.tsv: introductions and summaries written by our editors (not Scripture)
 *
 * Every catalogue row ends up in exactly one story. The output holds references only, never Bible text.
 */
object StoriesBuilder {

    data class CatalogueRow(val id: String, val book: String, val title: String, val reference: String, val type: String)

    class CurationException(message: String) : IllegalArgumentException(message)

    fun build(catalogueCsv: File, curationTsv: File, editorialTsv: File?): StoryCatalogue {
        val rows = readCatalogue(catalogueCsv)
        val rowIndex = rows.withIndex().associate { it.value.id to it.index }
        val curation = readCuration(curationTsv)
        val editorial = editorialTsv?.takeIf { it.exists() }?.let { readEditorial(it) } ?: emptyMap()

        // Which rows are taken by hand-built stories.
        val taken = HashMap<String, String>()
        for (s in curation.stories) for (id in s.rows) {
            if (id !in rowIndex) throw CurationException("Story ${s.slug} takes unknown catalogue row $id")
            taken.put(id, s.slug)?.let { throw CurationException("$id is taken by both $it and ${s.slug}") }
        }

        val stories = ArrayList<Story>()
        val rowCategories = HashMap<String, MutableSet<Category>>() // by row id or slug
        for ((cat, keys) in curation.categories) for (k in keys) rowCategories.getOrPut(k) { HashSet() } += cat

        // Hand-built stories.
        for (c in curation.stories) {
            val steps = c.steps.map { (title, refs) ->
                StoryStep(title, refs.map { StoryAccount(parse(it, "story ${c.slug}")) })
            }
            val sourceRows = c.rows.map { rows[rowIndex.getValue(it)] }
            val categories = HashSet<Category>()
            (c.rows + c.slug).forEach { k -> rowCategories[k]?.let { categories += it } }
            val first = steps.first().primary.passage.first
            if (Period.of(first) == Period.Jesus) categories += Category.LifeOfJesus
            val books = steps.flatMap { s -> s.accounts.map { it.passage.first.book } }.toSet()
            stories += Story(
                slug = c.slug,
                title = c.title,
                period = Period.of(first),
                kind = if (sourceRows.all { it.type != "Narrative" }) StoryKind.Teaching else StoryKind.Narrative,
                categories = categories,
                steps = steps,
                related = c.related.map { (ref, rel, note) -> RelatedPassage(parse(ref, "story ${c.slug}"), rel, note) },
                arranged = steps.size > 1 && books.size > 1,
                order = c.rows.minOf { rowIndex.getValue(it) },
                sources = c.rows.sortedBy { rowIndex.getValue(it) },
            )
        }

        // Every other row is a one-step story of its own.
        for ((index, row) in rows.withIndex()) {
            if (row.id in taken) continue
            val title = curation.renames[row.id] ?: row.title
            val passage = parse(row.reference, row.id)
            val categories = HashSet<Category>()
            rowCategories[row.id]?.let { categories += it }
            if (Period.of(passage.first) == Period.Jesus && row.type == "Narrative") categories += Category.LifeOfJesus
            stories += Story(
                slug = slugify(title),
                title = title,
                period = Period.of(passage.first),
                kind = if (row.type == "Narrative") StoryKind.Narrative else StoryKind.Teaching,
                categories = categories,
                steps = listOf(StoryStep(null, listOf(StoryAccount(passage)))),
                order = index,
                sources = listOf(row.id),
            )
        }

        // Slugs must be unique: they key progress, links and editorial text.
        stories.groupBy { it.slug }.filter { it.value.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
            throw CurationException("Duplicate story slugs (add a rename): $it")
        }
        val known = stories.map { it.slug }.toSet()
        val unknownKeys = (curation.categories.values.flatten().filter { !it.startsWith("STORY-") } + editorial.keys.map { it.first })
            .filter { it !in known }
        if (unknownKeys.isNotEmpty()) throw CurationException("Unknown story slugs: ${unknownKeys.distinct()}")

        val withText = stories.map { s ->
            s.copy(
                intro = editorial[s.slug to "intro"]?.let { EditorialBlock(it) },
                summary = editorial[s.slug to "summary"]?.let { EditorialBlock(it) },
            )
        }.sortedBy { it.order }
        return StoryCatalogue(StoryCatalogue.FORMAT_VERSION, withText)
    }

    fun slugify(title: String): String =
        title.lowercase().replace("'s ", "s ").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun parse(ref: String, where: String) = ReferenceParser.parseOrError(ref).getOrElse {
        throw CurationException("Bad reference \"$ref\" in $where: ${it.message}")
    }

    // ---- readers -------------------------------------------------------------------------------------------

    fun readCatalogue(file: File): List<CatalogueRow> =
        file.readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val f = parseCsvLine(line.removePrefix("﻿"))
            CatalogueRow(id = f[0], book = f[2], title = f[3], reference = f[4], type = f[5])
        }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        for (ch in line) when {
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> { out += sb.toString(); sb.clear() }
            else -> sb.append(ch)
        }
        out += sb.toString()
        return out
    }

    data class CuratedStory(
        val slug: String,
        val title: String,
        val rows: List<String>,
        val steps: MutableList<Pair<String?, List<String>>> = ArrayList(),
        val related: MutableList<Triple<String, Relation, String?>> = ArrayList(),
    )

    data class Curation(
        val stories: List<CuratedStory>,
        val renames: Map<String, String>,
        val categories: Map<Category, List<String>>,
    )

    fun readCuration(file: File): Curation {
        val stories = ArrayList<CuratedStory>()
        val renames = HashMap<String, String>()
        val categories = HashMap<Category, MutableList<String>>()
        file.readLines().forEachIndexed { n, raw ->
            val line = raw.trimEnd()
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val f = line.split('\t').map { it.trim() }
            fun need(count: Int) { if (f.size < count) throw CurationException("curation.tsv line ${n + 1}: expected $count columns") }
            fun current() = stories.lastOrNull() ?: throw CurationException("curation.tsv line ${n + 1}: ${f[0]} before any story")
            when (f[0]) {
                "story" -> { need(4); stories += CuratedStory(f[1], f[2], f[3].split(',').map { it.trim() }.filter { it.isNotEmpty() }) }
                "step" -> { need(3); current().steps += f[1].takeIf { it != "-" } to f[2].split('|').map { it.trim() } }
                "related" -> {
                    need(3)
                    val rel = Relation.entries.firstOrNull { it.name.equals(f[2], ignoreCase = true) }
                        ?: throw CurationException("curation.tsv line ${n + 1}: unknown relation ${f[2]}")
                    current().related += Triple(f[1], rel, f.getOrNull(3)?.takeIf { it.isNotEmpty() })
                }
                "rename" -> { need(3); renames[f[1]] = f[2] }
                "category" -> {
                    need(3)
                    val cat = when (f[1]) {
                        "miracle" -> Category.Miracle
                        "parable" -> Category.Parable
                        "featured" -> Category.Featured
                        else -> throw CurationException("curation.tsv line ${n + 1}: unknown category ${f[1]}")
                    }
                    categories.getOrPut(cat) { ArrayList() } += f[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }
                }
                else -> throw CurationException("curation.tsv line ${n + 1}: unknown line type ${f[0]}")
            }
        }
        stories.firstOrNull { it.steps.isEmpty() }?.let { throw CurationException("Story ${it.slug} has no steps") }
        return Curation(stories, renames, categories)
    }

    /** editorial.tsv: slug, kind (intro or summary), text. Returns (slug, kind) -> text. */
    fun readEditorial(file: File): Map<Pair<String, String>, String> {
        val out = LinkedHashMap<Pair<String, String>, String>()
        file.readLines().forEachIndexed { n, raw ->
            val line = raw.trimEnd()
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val f = line.split('\t')
            if (f.size != 3) throw CurationException("editorial.tsv line ${n + 1}: expected slug, kind, text")
            val kind = f[1].trim()
            if (kind != "intro" && kind != "summary") throw CurationException("editorial.tsv line ${n + 1}: kind must be intro or summary")
            val key = f[0].trim() to kind
            if (key in out) throw CurationException("editorial.tsv line ${n + 1}: second $kind for ${f[0]}")
            out[key] = f[2].trim()
        }
        return out
    }
}
