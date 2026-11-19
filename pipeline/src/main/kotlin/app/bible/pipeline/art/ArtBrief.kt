package app.bible.pipeline.art

import app.bible.stories.Depiction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** How God or Jesus may appear (imagery policy §1). */
@Serializable
enum class DivinePresence {
    /** Neither God nor Jesus is shown. */
    @SerialName("none") None,
    /** A glowing human silhouette made of light, with no facial features. */
    @SerialName("light_silhouette") LightSilhouette,
}

/**
 * A scene brief (proposal §6.2): the structured, reviewable description an image is generated from. Briefs are
 * planned from the story catalogue, can be corrected by an editor in content/art/overrides.tsv, and are checked
 * into git (content/art/briefs.jsonl) so every image can be traced back to exactly what it was made from.
 */
@Serializable
data class ArtBrief(
    val slug: String,
    val title: String,
    val reference: String,
    val depiction: Depiction,
    @SerialName("divine_figure") val divine: DivinePresence,
    /** Whether people appear at all. False for landscapes and settings. */
    val people: Boolean,
    val scene: String,
    val setting: String,
    @SerialName("must_show") val mustShow: List<String> = emptyList(),
    @SerialName("must_not_show") val mustNotShow: List<String> = emptyList(),
    @SerialName("keep_vague") val keepVague: List<String> = emptyList(),
    val style: String = STYLE_CINEMATIC,
    val aspect: String = "9:16",
) {
    /** The text prompt sent to the image model. Deterministic: the same brief always gives the same prompt. */
    fun prompt(): String = buildString {
        append(if (style == STYLE_ATMOSPHERIC) ATMOSPHERIC_STYLE else CINEMATIC_STYLE)
        append(" Scene: ").append(scene.trim().trimEnd('.')).append('.')
        append(" Setting: ").append(setting.trim().trimEnd('.')).append('.')
        if (mustShow.isNotEmpty()) append(" Show: ").append(mustShow.joinToString("; ")).append('.')
        if (people) append(" ").append(PEOPLE_RULE)
        else append(" A landscape or setting only, with no people or figures.")
        if (divine == DivinePresence.LightSilhouette) append(" ").append(DIVINE_RULE)
        if (keepVague.isNotEmpty()) append(" Keep these details indistinct: ").append(keepVague.joinToString("; ")).append('.')
        append(" ").append(COMPOSITION)
        val avoid = BASE_AVOID + mustNotShow
        append(" Avoid: ").append(avoid.joinToString(", ")).append('.')
    }

    /** Identifies a generation request: the prompt plus the model and size. Same hash means nothing to regenerate. */
    fun hash(model: String): String = sha256Hex("$model\n$aspect\n${prompt()}").take(16)

    companion object {
        const val STYLE_CINEMATIC = "story-cinematic"
        const val STYLE_ATMOSPHERIC = "regular-atmospheric"

        private const val CINEMATIC_STYLE =
            "A reverent, painterly, cinematic illustration with warm natural light and rich but gentle colour, like a classical oil painting."
        private const val ATMOSPHERIC_STYLE =
            "A quiet, painterly landscape illustration with natural light and soft atmosphere."
        private const val PEOPLE_RULE =
            "People have historically plausible ancient Near Eastern and Mediterranean appearance, dress and setting, not European."
        private const val DIVINE_RULE =
            "God or Jesus, where present, is shown only as a luminous human silhouette formed of soft light, with no face or facial features visible."
        private const val COMPOSITION =
            "Vertical composition; the main subject in the upper half; the lower part of the image calm, dark and low in detail."
        private val BASE_AVOID = listOf(
            "any text, letters, writing or inscriptions",
            "modern objects",
            "halos",
            "gore or graphic violence",
            "nudity",
            "detailed faces on God or Jesus",
        )

        val Jsonl = Json { encodeDefaults = true; ignoreUnknownKeys = false }

        fun readJsonl(text: String): List<ArtBrief> =
            text.lines().filter { it.isNotBlank() }.map { Jsonl.decodeFromString(serializer(), it) }

        fun writeJsonl(briefs: List<ArtBrief>): String =
            briefs.joinToString("\n", postfix = "\n") { Jsonl.encodeToString(serializer(), it) }
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun sha256Hex(text: String): String = sha256Hex(text.toByteArray())
