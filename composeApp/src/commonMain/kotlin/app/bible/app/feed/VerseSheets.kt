package app.bible.app.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import app.bible.app.platform.PlatformBackHandler
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.app.data.VerseText
import app.bible.app.ui.HighlightNames
import app.bible.app.ui.LocalScriptureColors
import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.data.bible.SearchResult
import app.bible.data.user.SavedNote
import app.bible.data.user.VerseMarks
import kotlinx.coroutines.delay

/** Actions on one or more verses: copy, highlight, bookmark and note. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VerseActionsSheet(vm: FeedViewModel, target: VerseActionTarget, translation: String, onDismiss: () -> Unit) {
    val selected = remember(target) { mutableStateListOf(target.initial) }
    var texts by remember(target) { mutableStateOf<Map<VerseId, VerseText>>(emptyMap()) }
    var marks by remember(target) { mutableStateOf(VerseMarks.None) }
    var editingNote by remember(target) { mutableStateOf(false) }
    var noteText by remember(target) { mutableStateOf("") }
    val revision by vm.marksRevision.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val range = VerseRange(target.verses.first(), target.verses.last())

    LaunchedEffect(target) {
        val list = if (range.start.chapter == range.end.chapter) {
            vm.chapterText(range.start.book, range.start.chapter).filter { it.id in range }
        } else target.verses.mapNotNull { vm.verse(it) }
        texts = list.associateBy { it.id }
    }
    LaunchedEffect(target, revision) {
        marks = vm.marks(range)
        if (!editingNote) noteText = marks.notes[target.initial] ?: ""
    }

    val sorted = selected.sorted()
    val primary = sorted.firstOrNull() ?: target.initial
    val reference = describe(sorted)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Scrolls, and stays above the keyboard, so Save is always reachable however long the note grows.
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(reference, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (target.verses.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text("Verses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (v in target.verses) {
                        FilterChip(
                            selected = v in selected,
                            onClick = { if (v in selected) { if (selected.size > 1) selected.remove(v) } else selected.add(v) },
                            label = { Text("${v.verse}") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Highlight", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            val palette = LocalScriptureColors.current.highlights
            val current = sorted.mapNotNull { marks.highlights[it] }.distinct().singleOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                palette.forEachIndexed { i, color ->
                    Box(
                        Modifier.size(36.dp)
                            .background(color.copy(alpha = 1f), CircleShape)
                            .border(if (current == i) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (current == i) 0.9f else 0.2f), CircleShape)
                            .clickable { vm.highlight(sorted, i) }
                            .semantics { contentDescription = "Highlight ${HighlightNames.getOrElse(i) { "" }}" },
                    )
                }
                TextButton(onClick = { vm.highlight(sorted, null) }, enabled = sorted.any { it in marks.highlights }) { Text("Clear") }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(copyText(sorted.mapNotNull { texts[it] }, reference, translation)))
                    onDismiss()
                }) { Text("Copy") }
                OutlinedButton(onClick = { vm.toggleBookmark(primary) }) {
                    Text(if (primary in marks.bookmarks) "Remove bookmark" else "Bookmark")
                }
                OutlinedButton(onClick = { editingNote = !editingNote; if (editingNote) noteText = marks.notes[primary] ?: "" }) {
                    Text(if (primary in marks.notes) "Edit note" else "Note")
                }
            }

            if (editingNote) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note on ${primary.display()}") },
                    shape = RoundedCornerShape(16.dp),
                    // A long note scrolls inside the field instead of pushing the buttons off the sheet.
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    if (primary in marks.notes) TextButton(onClick = { vm.saveNote(primary, ""); editingNote = false }) { Text("Delete") }
                    TextButton(onClick = { editingNote = false; noteText = marks.notes[primary] ?: "" }) { Text("Cancel") }
                    Button(onClick = { vm.saveNote(primary, noteText); editingNote = false }) { Text("Save") }
                }
            } else marks.notes[primary]?.let { note ->
                // The saved note, set apart from the actions above on its own tinted card.
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().clickable { editingNote = true; noteText = note },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Your note · ${primary.display()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/** "John 3:16-17 KJV" style label for the selected verses (contiguous runs joined with commas). */
private fun describe(verses: List<VerseId>): String {
    if (verses.isEmpty()) return ""
    val runs = ArrayList<VerseRange>()
    var start = verses.first(); var prev = start
    for (v in verses.drop(1)) {
        if (v.book == prev.book && v.chapter == prev.chapter && v.verse == prev.verse + 1) { prev = v; continue }
        runs += VerseRange(start, prev); start = v; prev = v
    }
    runs += VerseRange(start, prev)
    return runs.joinToString(", ") { it.display() }
}

/** Text placed on the clipboard: the verses with their numbers, then the reference and translation. */
private fun copyText(verses: List<VerseText>, reference: String, translation: String): String {
    val body = if (verses.size == 1) verses[0].text else verses.joinToString(" ") { "${it.id.verse} ${it.text}" }
    return "$body\n— $reference (${translation.uppercase()})"
}

/** Offline search: a reference ("jn 3 16", "Romans 8"), words, or "an exact phrase". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(vm: FeedViewModel, onPick: (VerseId) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(SearchResult()) }
    LaunchedEffect(query) {
        delay(200) // wait for typing to pause
        result = vm.search(query)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Words, \"a phrase\" or a reference") },
                shape = CircleShape, // search fields are pill-shaped: no sharp corners
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                result.reference?.let { ref ->
                    item {
                        Text(
                            "Go to ${ref.display()}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(ref.first) }.padding(12.dp),
                        )
                        HorizontalDivider()
                    }
                }
                if (query.isNotBlank() && result.total > 0) {
                    item {
                        Text(
                            if (result.total > result.hits.size) "${result.total} verses (showing the first ${result.hits.size})" else "${result.total} verses",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                items(result.hits, key = { it.verse.value }) { hit ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(hit.verse) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(hit.verse.display(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(hit.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Bookmarks and notes saved on this device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSheet(vm: FeedViewModel, onPick: (VerseId) -> Unit, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var bookmarks by remember { mutableStateOf<List<Pair<VerseId, String>>>(emptyList()) }
    var notes by remember { mutableStateOf<List<SavedNote>>(emptyList()) }
    // The note being read, opened from the Notes list.
    var reading by remember { mutableStateOf<SavedNote?>(null) }
    val revision by vm.marksRevision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        bookmarks = vm.bookmarks().map { it to (vm.verse(it)?.text ?: "") }
        notes = vm.notes()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val note = reading
        if (note != null) {
            NoteReader(vm, note, onBack = { reading = null }, onOpenVerse = { onPick(note.verse) })
            return@ModalBottomSheet
        }
        Column(Modifier.padding(bottom = 16.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Bookmarks") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Notes") })
            }
            val empty = if (tab == 0) bookmarks.isEmpty() else notes.isEmpty()
            if (empty) {
                Text(
                    if (tab == 0) "No bookmarks yet. Hold a verse and choose Bookmark." else "No notes yet. Hold a verse and choose Note.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                if (tab == 0) items(bookmarks, key = { it.first.value }) { (verse, text) ->
                    SavedRow(verse.display(), text) { onPick(verse) }
                } else items(notes, key = { it.verse.value }) { n ->
                    // Tapping a note opens it to read in full; from there it can be opened in the Bible.
                    NoteRow(n) { reading = n }
                }
            }
        }
    }
}

/** One note in the Notes list, on its own tinted card. */
@Composable
private fun NoteRow(note: SavedNote, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(note.verse.display(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(note.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A note opened from the Notes list: the verse it is on, then the whole note, with a way to that verse. */
@Composable
private fun NoteReader(vm: FeedViewModel, note: SavedNote, onBack: () -> Unit, onOpenVerse: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var verse by remember(note.verse) { mutableStateOf<String?>(null) }
    LaunchedEffect(note.verse) { verse = vm.verse(note.verse)?.text }
    PlatformBackHandler(enabled = true, onBack = onBack)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Notes") }
        Text(note.verse.display(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        verse?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = 0.75f), fontStyle = FontStyle.Italic)
        }
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = colors.primary.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Your note", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Spacer(Modifier.height(6.dp))
                Text(note.body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenVerse, modifier = Modifier.align(Alignment.End)) { Text("Open in Bible") }
    }
}

@Composable
private fun SavedRow(title: String, body: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
