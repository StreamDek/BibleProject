package app.bible.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Scripture is set in a serif. A bundled open-source serif (e.g. Literata) replaces this in Phase 1. */
val ScriptureFont: FontFamily = FontFamily.Serif

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
    MaterialTheme(colorScheme = if (dark) Night else Parchment, content = content)
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
