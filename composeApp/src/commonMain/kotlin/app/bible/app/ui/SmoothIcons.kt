package app.bible.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * VerseUp's own line icons for the bottom bar: drawn as strokes with round ends and round joins on a 24-unit
 * grid, so there are no sharp corners anywhere.
 */
internal object SmoothIcons {
    val Stories: ImageVector by lazy {
        icon(
            "Stories",
            // An open book: two gently curved pages meeting at the spine.
            "M12 6.6C10.2 5.2 7.6 4.6 4.6 4.6H3.6a1 1 0 0 0-1 1v11.6a1 1 0 0 0 1 1h1c3 0 5.6.7 7.4 2.2" +
                "c1.8-1.5 4.4-2.2 7.4-2.2h1a1 1 0 0 0 1-1V5.6a1 1 0 0 0-1-1h-1c-3 0-5.6.6-7.4 2z",
            "M12 6.6v12.8",
        )
    }

    val Chapter: ImageVector by lazy {
        icon(
            "Chapter",
            // A page of text with soft corners.
            "M7.2 3h9.6A2.7 2.7 0 0 1 19.5 5.7v12.6A2.7 2.7 0 0 1 16.8 21H7.2A2.7 2.7 0 0 1 4.5 18.3V5.7A2.7 2.7 0 0 1 7.2 3z",
            "M8.5 8.2h7", "M8.5 12h7", "M8.5 15.8h4.2",
        )
    }

    val Search: ImageVector by lazy {
        icon(
            "Search",
            "M10.8 3.8a7 7 0 1 1 0 14a7 7 0 1 1 0-14z",
            "M16 16l4.2 4.2",
        )
    }

    val Study: ImageVector by lazy {
        icon(
            "Study",
            // A scholar's cap, for word study.
            "M11.2 4.9a1.9 1.9 0 0 1 1.6 0l7.9 3.6a.7.7 0 0 1 0 1.3l-7.9 3.6a1.9 1.9 0 0 1-1.6 0L3.3 9.8a.7.7 0 0 1 0-1.3z",
            "M6.5 11.4v3.9c0 1.6 2.5 3.2 5.5 3.2s5.5-1.6 5.5-3.2v-3.9",
            "M21 9.2v4.6",
        )
    }

    val Saved: ImageVector by lazy {
        icon(
            "Saved",
            // A bookmark with rounded top corners and a soft notch.
            "M8.2 3.5h7.6a2.2 2.2 0 0 1 2.2 2.2v14.1a.6.6 0 0 1-.95.5l-4.5-3.3a.9.9 0 0 0-1.1 0l-4.5 3.3A.6.6 0 0 1 6 19.8V5.7a2.2 2.2 0 0 1 2.2-2.2z",
        )
    }

    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) {
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }
}
