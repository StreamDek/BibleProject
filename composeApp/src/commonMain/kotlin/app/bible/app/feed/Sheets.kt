package app.bible.app.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bible.app.platform.AppIcon
import app.bible.app.platform.appIconChangeNote
import app.bible.app.platform.appIconSupported
import app.bible.app.platform.currentAppIcon
import app.bible.app.platform.selectAppIcon
import app.bible.app.resources.Res
import app.bible.app.resources.app_icon_red
import app.bible.app.resources.app_icon_white
import app.bible.core.Book
import org.jetbrains.compose.resources.painterResource
import app.bible.core.Canon
import app.bible.core.Testament
import app.bible.core.VerseId
import app.bible.feed.Density

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ReadingSettingsSheet(prefs: ReadingPrefs, vm: FeedViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Verses per card", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in Density.Options) {
                    FilterChip(
                        selected = prefs.density == d,
                        onClick = { vm.setDensity(d) },
                        label = { Text(if (d is Density.Fixed) "${d.verses}" else "Auto") },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Text size", style = MaterialTheme.typography.titleSmall)
            var scale by remember { mutableStateOf(prefs.fontScale) }
            Slider(value = scale, onValueChange = { scale = it }, onValueChangeFinished = { vm.setFontScale(scale) }, valueRange = 0.8f..1.6f, steps = 7)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(checked = prefs.dark, onCheckedChange = { vm.toggleDark() })
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Words of Jesus in red", style = MaterialTheme.typography.titleSmall)
                    Text("As marked in the KJV source text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                Switch(checked = prefs.redLetters, onCheckedChange = { vm.setRedLetters(it) })
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Immersive view", style = MaterialTheme.typography.titleSmall)
                    Text("Hide the status bar for a full-screen view", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                Switch(checked = prefs.immersive, onCheckedChange = { vm.setImmersive(it) })
            }
            if (appIconSupported()) {
                Spacer(Modifier.height(20.dp))
                AppIconPicker()
            }
        }
    }
}

/** Lets the reader choose the VerseUp icon shown on their home screen. */
@Composable
private fun AppIconPicker() {
    var selected by remember { mutableStateOf(currentAppIcon()) }
    Text("App icon", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        for (icon in AppIcon.entries) {
            val isSelected = icon == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                    if (!isSelected) { selectAppIcon(icon); selected = icon }
                }.padding(6.dp),
            ) {
                Image(
                    painter = painterResource(if (icon == AppIcon.White) Res.drawable.app_icon_white else Res.drawable.app_icon_red),
                    contentDescription = "${icon.label} icon",
                    modifier = Modifier.size(64.dp)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(4.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(icon.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    appIconChangeNote()?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

/**
 * Jump to any book and chapter without scrolling through cards. The books are in two tabs, Old and New
 * Testament; the list opens on the tab and book being read ([current]), and that book is marked.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun JumpSheet(current: VerseId? = null, onPick: (VerseId) -> Unit, onDismiss: () -> Unit) {
    var book by remember { mutableStateOf<Book?>(null) }
    val currentBook = current?.let { Canon.book(it.book) }
    var testament by remember { mutableStateOf(currentBook?.testament ?: Testament.OLD) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val b = book
        if (b == null) {
            val tabs = Testament.entries
            TabRow(selectedTabIndex = tabs.indexOf(testament)) {
                for (t in tabs) {
                    Tab(
                        selected = t == testament,
                        onClick = { testament = t },
                        text = {
                            Text(
                                if (t == Testament.OLD) "Old Testament" else "New Testament",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                    )
                }
            }
            val books = remember(testament) { Canon.books.filter { it.testament == testament } }
            val startIndex = books.indexOfFirst { it.number == currentBook?.number }
            // Each tab keeps its own list; the current book's tab starts scrolled to it (one row above, for context).
            val listState = key(testament) {
                rememberLazyListState(initialFirstVisibleItemIndex = if (startIndex > 0) startIndex - 1 else 0)
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                items(books, key = { it.number }) { bk ->
                    val isCurrent = bk.number == currentBook?.number
                    Text(
                        bk.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { if (bk.isSingleChapter) onPick(bk.firstVerse) else book = bk }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                TextButton(onClick = { book = null }) { Text("‹ ${b.name}") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 1..b.chapterCount) {
                        val isCurrent = current != null && current.book == b.number && current.chapter == c
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.size(48.dp).clickable { onPick(VerseId.of(b.number, c, 1)) },
                        ) {
                            Text("$c", Modifier.padding(top = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
