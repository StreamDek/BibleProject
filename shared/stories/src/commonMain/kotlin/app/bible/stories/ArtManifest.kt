package app.bible.stories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The published story art (imagery policy §4, proposal §6.4). Each approved image is generated once and shared by
 * every user: the app only ever downloads published files from the CDN, and nothing is generated on a device.
 *
 * Files are content-addressed, `img/<hash>.<width>.<ext>` under [base], so they can be cached forever. With an
 * empty [base], or no entry for a story, the app shows its built-in placeholder art.
 */
@Serializable
data class ArtManifest(
    val version: Int = FORMAT_VERSION,
    /** CDN base URL, e.g. "https://cdn.example.com". Empty until images are published. */
    val base: String = "",
    /** Approved cover image per story slug. */
    val images: Map<String, ArtImage> = emptyMap(),
) {
    /** URL of the smallest published width that is at least [widthPx] wide, or null when nothing is published. */
    fun url(image: ArtImage, widthPx: Int): String? {
        if (image.bundled || base.isBlank() || image.widths.isEmpty()) return null
        val w = image.widths.sorted().firstOrNull { it >= widthPx } ?: image.widths.max()
        return "${base.trimEnd('/')}/img/${image.hash}.$w.${image.ext}"
    }

    /** The image for [slug] if the app can show it: bundled in the app, or published to a configured CDN. */
    fun available(slug: String): ArtImage? = images[slug]?.takeIf { it.bundled || base.isNotBlank() }

    /** Path of a bundled image inside the app's resources ("files/art/<hash>.<width>.<ext>"). */
    fun bundledPath(image: ArtImage): String = "files/art/${image.hash}.${image.widths.max()}.${image.ext}"

    fun encode(): String = ArtJson.encodeToString(serializer(), copy(images = images.entries.sortedBy { it.key }.associate { it.key to it.value }))

    companion object {
        const val FORMAT_VERSION = 1
        fun decode(json: String): ArtManifest = ArtJson.decodeFromString(serializer(), json)
    }
}

@Serializable
data class ArtImage(
    /** SHA-256 of the approved original, in hex. Shared by all widths. */
    val hash: String,
    val widths: List<Int>,
    /** Placeholder shown while the image loads (https://blurha.sh). */
    val blurhash: String,
    val depiction: Depiction,
    /** False for landscapes and settings with no people or divine figures. */
    val people: Boolean = true,
    val ext: String = "jpg",
    /** Shipped inside the app (for example a test image before the CDN exists) instead of downloaded. */
    val bundled: Boolean = false,
)

/** How literally an image depicts its passage (imagery policy §3). Shown in "About this image". */
@Serializable
enum class Depiction(val label: String) {
    @SerialName("direct") Direct("Direct depiction"),
    @SerialName("historical") Historical("Historical context"),
    @SerialName("symbolic") Symbolic("Symbolic"),
    @SerialName("atmospheric") Atmospheric("Atmospheric"),
}

private val ArtJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
