package app.bible.app.stories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import app.bible.stories.ArtImage
import app.bible.stories.ArtManifest
import app.bible.stories.Blurhash
import app.bible.stories.Period
import app.bible.stories.Story
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Canvas as BitmapCanvas

/** The published story art, provided once at the top of the app. Empty until images are published. */
val LocalStoryArt = staticCompositionLocalOf { ArtManifest() }

/** The published image for [story], or null when it has none yet. */
@Composable
fun publishedArt(story: Story): ArtImage? {
    val m = LocalStoryArt.current
    return if (m.base.isBlank()) null else m.images[story.slug]
}

/**
 * Story art. A published image (generated once, reviewed, and shared by every user) loads from the CDN over its
 * blurhash preview, and stays in the device's image cache. Without one, the story shows placeholder art.
 */
@Composable
fun StoryArt(story: Story, modifier: Modifier = Modifier) {
    val manifest = LocalStoryArt.current
    val image = publishedArt(story)
    if (image == null) {
        PlaceholderArt(story, modifier)
        return
    }
    BoxWithConstraints(modifier) {
        val widthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else 720
        val preview = remember(image.blurhash) { blurhashBitmap(image.blurhash) }
        if (preview != null) {
            Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
        } else {
            PlaceholderArt(story, Modifier.fillMaxSize())
        }
        AsyncImage(
            model = manifest.url(image, widthPx),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A tiny bitmap from the blurhash; drawn scaled up with smoothing, it gives a soft preview. */
private fun blurhashBitmap(hash: String): ImageBitmap? {
    val w = 12
    val h = 21
    val px = Blurhash.decode(hash, w, h) ?: return null
    val bmp = ImageBitmap(w, h)
    val canvas = BitmapCanvas(bmp)
    val paint = Paint()
    for (y in 0 until h) for (x in 0 until w) {
        paint.color = Color(px[y * w + x])
        canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
    }
    return bmp
}

/**
 * Placeholder art: a warm gradient for the story's period with a soft source of light. It is deliberately abstract
 * (no figures or faces), in keeping with the imagery policy. The light's position varies a little per story so
 * neighbouring tiles do not look identical.
 */
@Composable
private fun PlaceholderArt(story: Story, modifier: Modifier = Modifier) {
    val (top, bottom, glow) = palette(story.period)
    val h = story.slug.hashCode()
    val gx = 0.25f + ((h ushr 3) and 0xFF) / 255f * 0.5f
    val gy = 0.18f + ((h ushr 11) and 0xFF) / 255f * 0.22f
    Box(modifier.background(Brush.verticalGradient(listOf(top, bottom)))) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * gx, size.height * gy)
            val r = maxOf(size.width, size.height) * 0.75f
            drawRect(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.55f), glow.copy(alpha = 0.18f), Color.Transparent),
                    center = center,
                    radius = r,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = center, radius = r * 0.18f),
                radius = r * 0.18f,
                center = center,
            )
        }
    }
}

private fun palette(p: Period): Triple<Color, Color, Color> = when (p) {
    Period.Beginnings -> Triple(Color(0xFF1E3A4C), Color(0xFF0C1418), Color(0xFFF2D7A0))
    Period.Patriarchs -> Triple(Color(0xFF6B4A2B), Color(0xFF1C130C), Color(0xFFF5C77E))
    Period.Exodus -> Triple(Color(0xFF7A4B26), Color(0xFF20140C), Color(0xFFFFB86B))
    Period.Conquest -> Triple(Color(0xFF55603A), Color(0xFF15180E), Color(0xFFF0D58C))
    Period.Kingdom -> Triple(Color(0xFF4B2E5C), Color(0xFF140C19), Color(0xFFE9C46A))
    Period.Poetry -> Triple(Color(0xFF2F4F4F), Color(0xFF0D1515), Color(0xFFE6E2B8))
    Period.Prophets -> Triple(Color(0xFF3A3550), Color(0xFF100E17), Color(0xFFD9B98A))
    Period.Return -> Triple(Color(0xFF5A4632), Color(0xFF17120D), Color(0xFFEBD3A4))
    Period.Jesus -> Triple(Color(0xFF2C4A6B), Color(0xFF0C1420), Color(0xFFFFE3A8))
    Period.Church -> Triple(Color(0xFF6B3A2C), Color(0xFF1C0F0B), Color(0xFFFFC98A))
    Period.Letters -> Triple(Color(0xFF4A4336), Color(0xFF14120E), Color(0xFFEAD8B0))
    Period.Revelation -> Triple(Color(0xFF2A2350), Color(0xFF0A0815), Color(0xFFFFE9B0))
}
