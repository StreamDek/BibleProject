package app.bible.app.feed

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bible.core.Book
import app.bible.core.Canon
import app.bible.core.Testament
import app.bible.core.VerseId
import app.bible.feed.Density

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ReadingSettingsSheet(prefs: ReadingPrefs, vm: FeedViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
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
        }
    }
}

/** Jump to any book and chapter without scrolling through cards. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun JumpSheet(onPick: (VerseId) -> Unit, onDismiss: () -> Unit) {
    var book by remember { mutableStateOf<Book?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val b = book
        if (b == null) {
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                for (t in Testament.entries) {
                    item {
                        Text(
                            if (t == Testament.OLD) "Old Testament" else "New Testament",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(Canon.books.filter { it.testament == t }, key = { it.number }) { bk ->
                        Text(
                            bk.name,
                            Modifier.fillMaxWidth().clickable { if (bk.isSingleChapter) onPick(bk.firstVerse) else book = bk }.padding(12.dp),
                        )
                    }
                }
            }
        } else {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                TextButton(onClick = { book = null }) { Text("‹ ${b.name}") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 1..b.chapterCount) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
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
