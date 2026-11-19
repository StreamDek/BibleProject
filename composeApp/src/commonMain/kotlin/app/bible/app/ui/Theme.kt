package app.bible.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.bible.app.resources.Res
import app.bible.app.resources.literata
import app.bible.app.resources.literata_italic
import org.jetbrains.compose.resources.Font

/** Colours used inside Scripture text. */
@Immutable
data class ScriptureColors(
    /** Words of Jesus (red letters). */
    val red: Color,
    /** Highlight palette; a highlight stores its index into this list. */
    val highlights: List<Color>,
)

/** Scripture is set in Literata (SIL Open Font License, bundled). */
val LocalScriptureFont = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }
val LocalScriptureColors = staticCompositionLocalOf { DarkScripture }

/** Names of the highlight colours, in palette order (for accessibility labels). */
val HighlightNames = listOf("Gold", "Green", "Blue", "Rose", "Lavender")

private val DarkScripture = ScriptureColors(
    red = Color(0xFFF08A7E),
    highlights = listOf(Color(0x66C9A227), Color(0x5C5E9E5A), Color(0x5C4F7FB8), Color(0x5CC0607A), Color(0x5C8C6FC0)),
)
private val LightScripture = ScriptureColors(
    red = Color(0xFFB0281C),
    highlights = listOf(Color(0x80F3D36B), Color(0x80A9D8A0), Color(0x80A8C8EE), Color(0x80F2B1C2), Color(0x80CDB9EE)),
)

private val Night = darkColorScheme(
    background = Color(0xFF0E0D0B), surface = Color(0xFF1A1714), onBackground = Color(0xFFF4EDE1),
    onSurface = Color(0xFFF4EDE1), primary = Color(0xFFE2B566), onPrimary = Color(0xFF231A0B),
    secondary = Color(0xFFB9A88E),
)
private val Parchment = lightColorScheme(
    background = Color(0xFFF7F1E6), surface = Color(0xFFFFFBF4), onBackground = Color(0xFF2A241C),
    onSurface = Color(0xFF2A241C), primary = Color(0xFF8A5A14), onPrimary = Color.White,
    secondary = Color(0xFF6E6250),
)

@Composable
fun BibleTheme(dark: Boolean, content: @Composable () -> Unit) {
    val literata = FontFamily(
        Font(Res.font.literata, FontWeight.Normal),
        Font(Res.font.literata, FontWeight.Medium),
        Font(Res.font.literata, FontWeight.SemiBold),
        Font(Res.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
    )
    CompositionLocalProvider(
        LocalScriptureFont provides literata,
        LocalScriptureColors provides if (dark) DarkScripture else LightScripture,
    ) {
        MaterialTheme(colorScheme = if (dark) Night else Parchment, content = content)
    }
}

/**
 * Text-only card backgrounds: quiet gradients that stand in for images until the image pipeline exists,
 * and remain the background in text-only mode. Old and New Testament get slightly different warmth.
 */
fun cardBackground(book: Int, dark: Boolean): Brush {
    val ot = book <= 39
    val colors = when {
        dark && ot -> listOf(Color(0xFF2B2219), Color(0xFF15110D), Color(0xFF0B0907))
        dark -> listOf(Color(0xFF1C2230), Color(0xFF12141B), Color(0xFF09090C))
        ot -> listOf(Color(0xFFFBF3E3), Color(0xFFF3E7D1), Color(0xFFEBDCC0))
        else -> listOf(Color(0xFFF4F1EA), Color(0xFFE9E4DA), Color(0xFFDDD6C9))
    }
    return Brush.verticalGradient(colors)
}
