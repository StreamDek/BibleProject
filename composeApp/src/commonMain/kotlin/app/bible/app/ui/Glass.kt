package app.bible.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 * Ambient glass: headers are frosted, translucent panes. The content scrolling behind them shows through blurred,
 * so the header picks up its colours, and the bottom edge fades into the content with no line. Android 12+ and
 * iOS blur; older Android draws a translucent tint instead.
 *
 * Use: remember a [GlassState] per screen, put [glassSource] on the content and [glass] on the header (as
 * siblings, the header drawn above the content). Give the header some bottom padding for the fade.
 */

typealias GlassState = HazeState

@Composable
fun rememberGlassState(): GlassState = remember { HazeState() }

/** Marks the content that glass headers on this screen blur. */
fun Modifier.glassSource(state: GlassState): Modifier = hazeSource(state)

/**
 * Draws this header as ambient glass over [state]'s content. There are no lines: the bottom of the glass fades
 * into the content below. [visibility] (0 to 1) fades the glass in and out, so it can appear only while content
 * is scrolled under the header.
 */
@Composable
fun Modifier.glass(state: GlassState, visibility: Float = 1f): Modifier {
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
    return this.hazeEffect(state, style) {
        alpha = visibility
        // Solid for most of the header, then fading out so it flows into the content.
        mask = Brush.verticalGradient(0f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent)
    }
}

/** A header container drawn as ambient glass. */
@Composable
fun GlassHeader(state: GlassState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glass(state), content = content)
}
