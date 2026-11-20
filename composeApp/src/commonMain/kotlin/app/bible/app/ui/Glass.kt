package app.bible.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/*
 * Ambient glass: headers are frosted, translucent panes. The content scrolling or sitting behind them (Scripture
 * cards, story art) shows through blurred, so the header picks up its colours, with a light sheen along the top and
 * a hairline along the bottom edge. Android 12+ and iOS blur; older Android draws a translucent tint instead.
 *
 * Use: remember a [GlassState] per screen, put [glassSource] on the content and [glass] on the header (as
 * siblings, the header drawn above the content).
 */

typealias GlassState = HazeState

@Composable
fun rememberGlassState(): GlassState = remember { HazeState() }

/** Marks the content that glass headers on this screen blur. */
fun Modifier.glassSource(state: GlassState): Modifier = hazeSource(state)

/** Draws this header as ambient glass over [state]'s content. */
@Composable
fun Modifier.glass(state: GlassState): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val tint = colors.background.copy(alpha = if (dark) 0.45f else 0.55f)
    val style = HazeStyle(
        backgroundColor = colors.background,
        tint = HazeTint(tint),
        blurRadius = 24.dp,
        noiseFactor = 0.08f,
        // Without blur, a stronger tint keeps the text legible.
        fallbackTint = HazeTint(colors.background.copy(alpha = 0.88f)),
    )
    val sheen = Color.White.copy(alpha = if (dark) 0.06f else 0.22f)
    val edge = (if (dark) Color.White else Color.Black).copy(alpha = if (dark) 0.10f else 0.08f)
    return this
        .hazeEffect(state, style)
        .drawWithContent {
            // A soft sheen from the top, fading out, like light on glass.
            drawRect(Brush.verticalGradient(listOf(sheen, Color.Transparent)))
            drawContent()
            val y = size.height - 0.5.dp.toPx()
            drawLine(edge, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
}

/** A header container drawn as ambient glass. */
@Composable
fun GlassHeader(state: GlassState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glass(state), content = content)
}
