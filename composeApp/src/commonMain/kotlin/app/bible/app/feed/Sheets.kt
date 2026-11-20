package app.bible.app.feed

import app.bible.app.platform.Speech

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
            Text("Text size", style = MaterialTheme.typography.titleSmall)
            var scale by remember { mutableStateOf(prefs.fontScale) }
            Slider(value = scale, onValueChange = { scale = it }, onValueChangeFinished = { vm.setFontScale(scale) }, valueRange = 0.8f..1.6f, steps = 7)
        }
    }
}

/**
 * The app's Settings, opened from the gear beside "Aa" on any screen: appearance, reading and story options and
 * the app icon. ("Aa" holds only the text size, for quick changes while reading.)
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppSettingsSheet(prefs: ReadingPrefs, vm: FeedViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            SettingsGroup("Appearance")
            SettingSwitch("Dark", null, prefs.dark) { vm.toggleDark() }
            SettingSwitch("Immersive view", "Hide the status bar for a full-screen view", prefs.immersive) { vm.setImmersive(it) }
            Spacer(Modifier.height(12.dp))
            SettingsGroup("Reading")
            Text("Verses per card", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
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
            SettingSwitch("Words of Jesus in red", "As marked in the KJV source text", prefs.redLetters) { vm.setRedLetters(it) }
            if (Speech.available) SettingSwitch(
                "Read aloud",
                "Adds a play button beside Aa that reads the text to you, page after page, with your phone's voice",
                prefs.readAloud,
            ) { vm.setReadAloud(it) }
            Spacer(Modifier.height(12.dp))
            SettingsGroup("Stories")
            SettingSwitch(
                "Story image as background",
                "The story's image, softly blurred, behind the text while you read",
                prefs.storyArtBackground,
            ) { vm.setStoryArtBackground(it) }
            if (appIconSupported()) {
                Spacer(Modifier.height(20.dp))
                AppIconPicker()
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
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
            // Chapter numbers in an even grid: as many equal columns as fit, each square cell filling its share,
            // so there is no leftover gap on the right. Long books open near the chapter being read.
            val isCurrentBook = current != null && current.book == b.number
            val gridState = rememberLazyGridState(
                initialFirstVisibleItemIndex = if (isCurrentBook && current!!.chapter > 40) current.chapter - 10 else 0,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 52.dp),
                state = gridState,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box { TextButton(onClick = { book = null }) { Text("‹ ${b.name}") } }
                }
                items((1..b.chapterCount).toList(), key = { it }) { c ->
                    val isCurrent = isCurrentBook && current!!.chapter == c
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { onPick(VerseId.of(b.number, c, 1)) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "$c",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}
