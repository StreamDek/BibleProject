package app.bible.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The attribution registry (composeResources/files/attributions.json): every third-party text, dataset, font and
 * library VerseUp ships, with its licence and the credit it requires. The Attributions & Licences page and the
 * source labels in word study are generated from it, so credits are never hard-coded in screens.
 */
@Serializable
data class AttributionRegistry(
    val about: String = "",
    val categories: List<AttributionCategory>,
    val resources: List<Attribution>,
) {
    fun byId(id: String): Attribution? = resources.firstOrNull { it.id == id }

    /** Problems that make an entry unusable: missing required fields, unknown categories, repeated ids. */
    fun problems(): List<String> = buildList {
        val cats = categories.map { it.id }.toSet()
        val seen = HashSet<String>()
        for (r in resources) {
            if (!seen.add(r.id)) add("${r.id}: listed twice")
            if (r.category !in cats) add("${r.id}: unknown category ${r.category}")
            listOf("name" to r.name, "author" to r.author, "description" to r.description, "copyright" to r.copyright,
                "licence" to r.licence, "attribution" to r.attribution, "url" to r.url, "version" to r.version)
                .filter { it.second.isBlank() }.forEach { add("${r.id}: missing ${it.first}") }
            if (r.licence != "Public domain" && r.licenceFile == null && r.licenceUrl == null) add("${r.id}: no licence text or link")
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(text: String): AttributionRegistry = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class AttributionCategory(val id: String, val title: String)

@Serializable
data class Attribution(
    val id: String,
    val category: String,
    val name: String,
    val author: String,
    val description: String,
    val copyright: String,
    val licence: String,
    val attribution: String,
    val url: String,
    val version: String,
    val licenceUrl: String? = null,
    /** A full licence text bundled in composeResources/files, e.g. "licences/cc-by-4.0.txt". */
    val licenceFile: String? = null,
    /** The licence in a few plain words, for readers ("Free to use with credit."). */
    val summary: String? = null,
    /** What VerseUp changed, where the licence asks for it or it helps readers. */
    val modifications: String? = null,
)
