package app.bible.pipeline.art

import app.bible.stories.Category
import app.bible.stories.Depiction
import app.bible.stories.Period
import app.bible.stories.Story
import app.bible.stories.StoryCatalogue
import app.bible.stories.StoryKind
import java.io.File

/**
 * Plans one cover brief per story (Tier A in proposal §6.1). The planner gives a careful first draft from the
 * story's period, kind and introduction; editors correct any brief in content/art/overrides.tsv, and every image
 * is still reviewed by a person before it is published.
 */
object BriefPlanner {

    class PlanException(message: String) : IllegalArgumentException(message)

    fun plan(catalogue: StoryCatalogue, overrides: Map<String, Map<String, String>>): List<ArtBrief> {
        val slugs = catalogue.stories.map { it.slug }.toSet()
        (overrides.keys - slugs).takeIf { it.isNotEmpty() }?.let { throw PlanException("overrides.tsv names unknown stories: $it") }
        return catalogue.stories
            .filter { overrides[it.slug]?.get("skip") != "yes" }
            .map { s -> applyOverrides(draft(s), overrides[s.slug].orEmpty()) }
    }

    fun draft(s: Story): ArtBrief {
        val primary = s.steps.first().primary.passage
        val text = "${s.title} ${s.intro?.text.orEmpty()}"
        val depiction = when {
            Category.Parable in s.categories -> Depiction.Symbolic
            s.period == Period.Revelation -> Depiction.Symbolic
            s.kind == StoryKind.Narrative -> Depiction.Direct
            s.period == Period.Letters && primary.first.book in LETTER_PLACES -> Depiction.Historical
            else -> Depiction.Atmospheric
        }
        val people = depiction != Depiction.Atmospheric && depiction != Depiction.Historical
        val divine = if (people && DIVINE.containsMatchIn(text)) DivinePresence.LightSilhouette else DivinePresence.None
        val setting = when (depiction) {
            Depiction.Historical -> LETTER_PLACES[primary.first.book]?.let { "$it in the first century, under Roman rule" }
                ?: SETTINGS.getValue(s.period)
            else -> SETTINGS.getValue(s.period)
        }
        val scene = when (depiction) {
            Depiction.Historical -> "A quiet everyday view of ${setting.substringBefore(" in the first")} in the first century, the place this letter was sent to"
            Depiction.Atmospheric -> if (s.period == Period.Letters) "A calm first-century Mediterranean landscape with an old Roman road, olive trees and a distant harbour"
                else "A landscape that suits ${s.title.lowercase()}: ${sceneFromIntro(s)}"
            Depiction.Symbolic -> if (Category.Parable in s.categories) "The imagery of the parable of ${s.title.lowercase()}: ${sceneFromIntro(s)}"
                else "Visionary, symbolic imagery: ${sceneFromIntro(s)}"
            Depiction.Direct -> "${s.title}: ${sceneFromIntro(s)}"
        }
        return ArtBrief(
            slug = s.slug,
            title = s.title,
            reference = primary.display(),
            depiction = depiction,
            divine = divine,
            people = people,
            scene = scene,
            setting = setting,
            style = if (depiction == Depiction.Atmospheric || depiction == Depiction.Historical) ArtBrief.STYLE_ATMOSPHERIC else ArtBrief.STYLE_CINEMATIC,
        )
    }

    /**
     * The introduction without quotations (image models tend to draw quoted words as lettering) and without
     * references to other books.
     */
    fun sceneFromIntro(s: Story): String {
        val intro = s.intro?.text ?: return s.title
        return intro
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\s*\\([^)]*\\)"), "")
            .replace(Regex("[^.]*\\b(quotes?|quoted)\\b[^.]*\\.\\s*"), "")
            .replace(Regex("\\s+([,.;:])"), "$1")
            .replace(Regex("([,;:])\\s*([.,;:])"), "$2")
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("^[\\s,.;:]+"), "")
            .trim()
            .ifEmpty { s.title }
    }

    private fun applyOverrides(b: ArtBrief, o: Map<String, String>): ArtBrief {
        var r = b
        for ((field, value) in o) r = when (field) {
            "scene" -> r.copy(scene = value)
            "setting" -> r.copy(setting = value)
            "depiction" -> r.copy(depiction = enumValueOfLabel(value))
            "divine_figure" -> r.copy(divine = if (value == "light_silhouette") DivinePresence.LightSilhouette else DivinePresence.None)
            "people" -> r.copy(people = value == "yes")
            "must_show" -> r.copy(mustShow = value.split(';').map { it.trim() }.filter { it.isNotEmpty() })
            "must_not_show" -> r.copy(mustNotShow = value.split(';').map { it.trim() }.filter { it.isNotEmpty() })
            "keep_vague" -> r.copy(keepVague = value.split(';').map { it.trim() }.filter { it.isNotEmpty() })
            "style" -> r.copy(style = value)
            "skip" -> r
            else -> throw PlanException("overrides.tsv: unknown field \"$field\" for ${b.slug}")
        }
        if (r.depiction == Depiction.Atmospheric && o["people"] == null) r = r.copy(people = false)
        if (!r.people) r = r.copy(divine = DivinePresence.None)
        return r
    }

    private fun enumValueOfLabel(v: String) = Depiction.entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
        ?: throw PlanException("overrides.tsv: unknown depiction \"$v\" (direct, historical, symbolic, atmospheric)")

    /** overrides.tsv: slug, field, value. Fields: scene, setting, depiction, divine_figure, people, must_show, must_not_show, keep_vague, style, skip. */
    fun readOverrides(file: File): Map<String, Map<String, String>> {
        if (!file.exists()) return emptyMap()
        val out = LinkedHashMap<String, LinkedHashMap<String, String>>()
        file.readLines().forEachIndexed { n, raw ->
            val line = raw.trimEnd()
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val f = line.split('\t')
            if (f.size != 3) throw PlanException("overrides.tsv line ${n + 1}: expected slug, field, value")
            val prev = out.getOrPut(f[0].trim()) { LinkedHashMap() }.put(f[1].trim(), f[2].trim())
            if (prev != null) throw PlanException("overrides.tsv line ${n + 1}: ${f[1]} given twice for ${f[0]}")
        }
        return out
    }

    private val DIVINE = Regex("\\b(Jesus|God|LORD|the Lord|Christ|Son of man|the Lamb|angel of the Lord)\\b")

    private val SETTINGS = mapOf(
        Period.Beginnings to "the ancient Near East at the dawn of history: primeval landscapes, rivers, gardens and open plains",
        Period.Patriarchs to "Bronze Age Canaan, Mesopotamia and Egypt: tent-dwelling herders with flocks, wells and hill country",
        Period.Exodus to "ancient Egypt and the Sinai wilderness: desert mountains and the Israelite tribes with tents and flocks",
        Period.Conquest to "the hill country and valleys of ancient Canaan: walled towns, fields and Israelite tribes",
        Period.Kingdom to "the kingdoms of ancient Israel and Judah: Jerusalem, stone palaces and walls, Iron Age dress and armour",
        Period.Poetry to "the landscapes of ancient Israel: hills, shepherds' fields, vineyards, springs and night skies",
        Period.Prophets to "Israel, Judah, Assyria and Babylon in the Iron Age: city walls, temple courts, and exile by the rivers of Babylon",
        Period.Return to "the Persian period: Jerusalem being rebuilt, and the Persian court",
        Period.Jesus to "first-century Galilee and Judea under Roman rule: the Sea of Galilee, villages of stone houses, hillsides and Jerusalem",
        Period.Church to "the first-century Roman Mediterranean: Jerusalem, Greek and Roman cities, roads, harbours and ships",
        Period.Letters to "the first-century Roman Mediterranean world",
        Period.Revelation to "symbolic, visionary imagery from the book of Revelation: heaven, light, and the sea and sky",
    )

    /** Where each letter was sent, by book number. */
    private val LETTER_PLACES = mapOf(
        45 to "Rome", 46 to "Corinth", 47 to "Corinth", 48 to "the hill country of Galatia", 49 to "Ephesus",
        50 to "Philippi", 51 to "Colossae in the Lycus valley", 52 to "Thessalonica", 53 to "Thessalonica",
        54 to "Ephesus", 55 to "Ephesus", 56 to "the island of Crete", 57 to "Colossae in the Lycus valley",
    )
}
