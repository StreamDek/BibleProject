package app.bible.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The reader's navigation icons: Material "Rounded" icons, with smooth curved shapes. */
object NavIcons {
    val Search: ImageVector get() = Icons.Rounded.Search
    val Saved: ImageVector get() = Icons.Rounded.BookmarkBorder
    val Stories: ImageVector get() = Icons.Rounded.AutoStories
    val Chapter: ImageVector get() = Icons.AutoMirrored.Rounded.MenuBook
    val Settings: ImageVector get() = Icons.Rounded.Settings
}

/** One item of the reader's bottom navigation: an icon over a small label. */
@Composable
fun NavItem(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onBackground
    Column(
        modifier.widthIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, color = color.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}

/** The reading settings button (it replaced "Aa"). [overArt] draws it white with a soft shadow, for use on images. */
@Composable
fun SettingsButton(onClick: () -> Unit, overArt: Boolean = false) {
    IconButton(onClick = onClick) {
        if (overArt) {
            // A blurred dark copy underneath stands in for a text shadow.
            Icon(NavIcons.Settings, null, tint = Color.Black.copy(alpha = 0.35f), modifier = Modifier.size(24.dp).padding(top = 1.dp))
        }
        Icon(
            NavIcons.Settings,
            contentDescription = "Reading settings",
            tint = if (overArt) Color.White else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp),
        )
    }
}
