package app.bible.stories

import app.bible.core.Canon
import app.bible.core.Passage
import app.bible.core.Testament
import app.bible.core.VerseId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Story Mode content model (proposal §5).
 *
 * A story never stores Bible text. It stores references only, and the text always comes from the translation
 * database when a card is shown, so a story cannot contain altered Scripture. Words that are not Scripture
 * (introductions and summaries) are a separate type, EditorialBlock, which the app renders with its own style
 * and a "not Scripture" label.
 */

/** All published stories, as bundled in the app (composeResources/files/stories.json, built by :pipeline). */
@Serializable
data class StoryCatalogue(
    val version: Int,
    val stories: List<Story>,
) {
    fun encode(): String = buildString {
        // One story per line keeps diffs of the generated file readable.
        append("{\"version\":").append(version).append(",\"stories\":[\n")
        stories.forEachIndexed { i, s ->
            append(StoryJson.encodeToString(Story.serializer(), s))
            append(if (i == stories.lastIndex) "\n" else ",\n")
        }
        append("]}\n")
    }

    companion object {
        const val FORMAT_VERSION = 1
        fun decode(json: String): StoryCatalogue = StoryJson.decodeFromString(serializer(), json)
    }
}

internal val StoryJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

@Serializable
data class Story(
    /** Stable id used for progress, links and editorial text, e.g. "birth-of-jesus". */
    val slug: String,
    val title: String,
    val period: Period,
    val kind: StoryKind = StoryKind.Narrative,
    val categories: Set<Category> = emptySet(),
    /** The narrative in order. Most stories have one step; a step with several accounts shows tabs. */
    val steps: List<StoryStep>,
    val related: List<RelatedPassage> = emptyList(),
    val intro: EditorialBlock? = null,
    val summary: EditorialBlock? = null,
    /** True when our editors arranged steps from different books into one order. Shown on the intro card. */
    val arranged: Boolean = false,
    /** Position in "Journey through the Bible" (canonical order of the catalogue). */
    val order: Int,
    /** Rows of the source catalogue (bible_story_catalogue.csv) this story was built from. */
    val sources: List<String> = emptyList(),
) {
    init {
        require(steps.isNotEmpty()) { "Story $slug has no steps" }
    }

    val testament: Testament get() = if (steps.first().primary.passage.first.book <= 39) Testament.OLD else Testament.NEW

    /** Every account of every step, in order. */
    val accounts: List<StoryAccount> get() = steps.flatMap { it.accounts }

    /** True when any step offers more than one account (parallel accounts). */
    val hasParallels: Boolean get() = steps.any { it.accounts.size > 1 }

    /** "Matthew 14:13-21 · Mark 6:30-44 · +2" for headers and tiles. */
    fun sourcesLine(max: Int = 2): String {
        val refs = accounts.map { it.passage.display() }.distinct()
        val shown = refs.take(max).joinToString(" · ")
        return if (refs.size > max) "$shown · +${refs.size - max}" else shown
    }

    /** Whether [verse] is part of this story's own accounts (not its related passages). */
    operator fun contains(verse: VerseId): Boolean = steps.any { s -> s.accounts.any { verse in it.passage } }
}

@Serializable
data class StoryStep(
    /** Editorial step title, e.g. "Shepherds and angels". Null for a single-step story. */
    val title: String? = null,
    /** One account per source passage. The first is the primary account. Parallels are never merged. */
    val accounts: List<StoryAccount>,
) {
    init {
        require(accounts.isNotEmpty())
    }

    val primary: StoryAccount get() = accounts.first()
}

@Serializable
data class StoryAccount(val passage: Passage) {
    /** Tab label: the book this account comes from, e.g. "Mark". */
    val label: String get() = Canon.book(passage.first.book).name
}

@Serializable
data class RelatedPassage(
    val passage: Passage,
    val relation: Relation,
    val note: String? = null,
)

@Serializable
enum class Relation {
    @SerialName("prophecy") Prophecy,
    @SerialName("parallel") Parallel,
    @SerialName("echo") Echo,
    @SerialName("background") Background,
    ;

    val label: String get() = when (this) {
        Prophecy -> "Prophecy"
        Parallel -> "Parallel"
        Echo -> "Echo"
        Background -> "Background"
    }
}

/**
 * Words written by our editors, never Scripture. [status] tracks editorial review; drafts are shown only in
 * pre-release builds until Story Studio (the admin platform) has a review queue.
 */
@Serializable
data class EditorialBlock(
    val text: String,
    val status: EditorialStatus = EditorialStatus.Draft,
)

@Serializable
enum class EditorialStatus {
    @SerialName("draft") Draft,
    @SerialName("approved") Approved,
}

@Serializable
enum class StoryKind {
    /** Events told as a story. */
    @SerialName("narrative") Narrative,
    /** Poetry, wisdom, prophecy or a letter: read as a passage, not a plot. */
    @SerialName("teaching") Teaching,
}

@Serializable
enum class Category {
    @SerialName("life-of-jesus") LifeOfJesus,
    @SerialName("miracle") Miracle,
    @SerialName("parable") Parable,
    @SerialName("featured") Featured,
}

/** Broad periods of the biblical story, in order. Used for shelves and "Journey through the Bible". */
@Serializable
enum class Period(val title: String) {
    @SerialName("beginnings") Beginnings("Beginnings"),
    @SerialName("patriarchs") Patriarchs("The Patriarchs"),
    @SerialName("exodus") Exodus("Exodus and Wilderness"),
    @SerialName("conquest") Conquest("Conquest and Judges"),
    @SerialName("kingdom") Kingdom("The Kingdom"),
    @SerialName("poetry") Poetry("Poetry and Wisdom"),
    @SerialName("prophets") Prophets("Prophets and Exile"),
    @SerialName("return") Return("Return from Exile"),
    @SerialName("jesus") Jesus("The Life of Jesus"),
    @SerialName("church") Church("The Early Church"),
    @SerialName("letters") Letters("The Letters"),
    @SerialName("revelation") Revelation("Revelation"),
    ;

    companion object {
        /** A period for a passage, from where it starts. Poetry and wisdom books have their own shelf. */
        fun of(first: VerseId): Period = when (first.book) {
            1 -> if (first.chapter <= 11) Beginnings else Patriarchs
            in 2..5 -> Exodus
            in 6..8 -> Conquest
            in 9..14 -> Kingdom
            15, 16, 17 -> Return
            18 -> Patriarchs // Job: traditionally set in the time of the patriarchs
            in 19..22 -> Poetry
            in 23..36 -> Prophets
            in 37..39 -> Return
            in 40..43 -> Jesus
            44 -> Church
            in 45..65 -> Letters
            else -> Revelation
        }
    }
}
