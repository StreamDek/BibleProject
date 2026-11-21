package app.bible.app.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.app.ui.LocalScriptureFont
import app.bible.app.ui.SettingsButton
import app.bible.core.VerseId
import app.bible.data.bible.SearchResult
import app.bible.data.study.Language
import app.bible.data.study.StepEntry
import app.bible.data.study.StrongsEntry
import app.bible.data.study.StrongsNumber
import app.bible.data.study.StudySearchResult
import app.bible.data.user.SavedWord
import kotlinx.coroutines.delay

/** Which results the Study search shows. */
private enum class StudyFilter(val label: String) { All("All"), Hebrew("Hebrew"), Greek("Greek"), English("English"), Scripture("Scripture") }

/** A saved or studied entry id ("H430", "G26", or a STEP sense such as "H1254A") as a study request. */
fun requestFor(entry: String, verse: VerseId? = null): WordStudyRequest {
    val num = StrongsNumber.parse(entry)
    val sense = StrongsNumber.senseLetter(entry)
    return WordStudyRequest(num = num, stepId = if (sense != null) entry else null)
}

/**
 * The Study page: a concordance search over Strong's and STEP Bible (English, Hebrew, Greek, transliterations,
 * Strong's numbers and references), with the reader's recent and saved words and a few suggested studies.
 */
@Composable
fun StudyScreen(
    vm: StudyViewModel,
    onBack: () -> Unit,
    onOpenVerse: (VerseId) -> Unit,
    onAttributions: () -> Unit,
    initialQuery: String = "",
) {
    val open = LocalWordStudy.current
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var filter by rememberSaveable { mutableStateOf(StudyFilter.All) }
    var result by remember { mutableStateOf(StudySearchResult()) }
    var scripture by remember { mutableStateOf(SearchResult()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) query = initialQuery }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) { result = StudySearchResult(); scripture = SearchResult(); return@LaunchedEffect }
        searching = true
        delay(220) // wait for typing to pause
        result = vm.store().search(q)
        scripture = if (q.any { it.isLetter() && it.code < 0x250 } && StrongsNumber.parse(q) == null) vm.searchScripture(q) else SearchResult()
        searching = false
    }

    // A Surface gives text and icons the theme's content colour; without it they default to black, unreadable in dark mode.
    Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(
                "Study", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            SettingsButton()
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search a word, H430 or John 3:16", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") } },
            shape = CircleShape, // search fields are pill-shaped: no sharp corners
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (query.isNotBlank()) {
            LazyRow(
                horizontalArrangement = ChipSpacing,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(StudyFilter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }, shape = RoundedCornerShape(50), colors = studyChipColors())
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            if (query.isBlank()) discovery(vm, open, onAttributions)
            else results(query, filter, result, scripture, searching, open, onOpenVerse, onSuggestion = { query = it })
        }
    }
    }
}

// ---- search results ---------------------------------------------------------------------------------------------

private fun LazyListScope.results(
    query: String,
    filter: StudyFilter,
    r: StudySearchResult,
    scripture: SearchResult,
    searching: Boolean,
    open: (WordStudyRequest) -> Unit,
    onOpenVerse: (VerseId) -> Unit,
    onSuggestion: (String) -> Unit,
) {
    fun shows(f: StudyFilter) = filter == StudyFilter.All || filter == f
    val strongs = r.strongs.filter {
        when (filter) {
            StudyFilter.Hebrew -> it.language != Language.Greek
            StudyFilter.Greek -> it.language == Language.Greek
            StudyFilter.All -> true
            else -> false
        }
    }
    val step = r.step.filter {
        when (filter) {
            StudyFilter.Hebrew -> it.language != Language.Greek
            StudyFilter.Greek -> it.language == Language.Greek
            StudyFilter.All -> true
            else -> false
        }
    }
    r.reference?.let { ref ->
        if (shows(StudyFilter.Scripture)) {
            item(key = "ref") {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenVerse(ref.first) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Go to ${ref.display()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text("Hold any verse there and choose a word to study it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
    if (strongs.isNotEmpty()) {
        item(key = "h-words") { GroupHeader("Hebrew and Greek words", "Strong's") }
        items(strongs, key = { "s${it.num}" }) { e -> EntryRow(e) { open(WordStudyRequest(num = e.num)) } }
    }
    if (step.isNotEmpty()) {
        item(key = "h-step") { GroupHeader("STEP Bible senses", "STEP Bible") }
        items(step, key = { "t${it.id}" }) { s -> StepRow(s) { open(WordStudyRequest(num = s.num.takeIf { it > 0 }, stepId = s.id)) } }
    }
    if (shows(StudyFilter.English) && r.english.isNotEmpty()) {
        item(key = "h-en") { GroupHeader("“${r.english.first().word}” in the KJV", "Word tags") }
        items(r.english, key = { "e${it.entry.num}" }) { h ->
            EntryRow(h.entry, trailing = "${h.count}×") { open(WordStudyRequest(num = h.entry.num)) }
        }
    }
    if (shows(StudyFilter.Scripture) && scripture.hits.isNotEmpty()) {
        item(key = "h-verses") {
            GroupHeader(
                if (scripture.total > scripture.hits.size) "Verses (first ${scripture.hits.size} of ${scripture.total})" else "Verses (${scripture.total})",
                "KJV",
            )
        }
        items(scripture.hits.take(30), key = { "v${it.verse.value}" }) { hit ->
            Column(Modifier.fillMaxWidth().clickable { onOpenVerse(hit.verse) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(hit.verse.display(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(hit.text, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 15.sp, lineHeight = 22.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    val nothing = strongs.isEmpty() && step.isEmpty() && (!shows(StudyFilter.English) || r.english.isEmpty()) &&
        (!shows(StudyFilter.Scripture) || (scripture.hits.isEmpty() && r.reference == null))
    if (nothing && !searching) {
        item(key = "empty") {
            Column(Modifier.padding(20.dp)) {
                Text("Nothing found for “$query”${if (filter != StudyFilter.All) " in ${filter.label}" else ""}.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Try an English word (love), a Strong's number (H430, G26), a transliteration (agape, chesed) or Hebrew and Greek without accents.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                )
                if (r.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Did you mean", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Row(horizontalArrangement = ChipSpacing) {
                        for (s in r.suggestions.take(4)) TextButton(onClick = { onSuggestion(s) }) { Text(s) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, source: String) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
        Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun EntryRow(e: StrongsEntry, trailing: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(e.lemma, style = originalStyle(e.language, 22.sp).copy(textDirection = TextDirection.Content))
                Spacer(Modifier.width(10.dp))
                e.xlit?.let { Text(it, style = TextStyle(fontSize = 14.sp, fontStyle = FontStyle.Italic), color = colors.onSurface.copy(alpha = 0.8f)) }
            }
            Text(e.definition ?: e.kjvDefinition ?: "", style = MaterialTheme.typography.bodySmall, color = colors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(e.id, style = MaterialTheme.typography.labelLarge, color = StudyColors.language(e.language))
            Text(trailing ?: e.language.label, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
        }
    }
}

@Composable
private fun StepRow(s: StepEntry, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.lemma, style = originalStyle(s.language, 20.sp).copy(textDirection = TextDirection.Content))
                Spacer(Modifier.width(10.dp))
                s.xlit?.let { Text(it, style = TextStyle(fontSize = 14.sp, fontStyle = FontStyle.Italic), color = colors.onSurface.copy(alpha = 0.8f)) }
            }
            Text(s.gloss ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(s.id, style = MaterialTheme.typography.labelLarge, color = StudyColors.step())
            Text(s.language.label, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
        }
    }
}

// ---- discovery --------------------------------------------------------------------------------------------------

private fun LazyListScope.discovery(vm: StudyViewModel, open: (WordStudyRequest) -> Unit, onAttributions: () -> Unit) {
    item(key = "recent") { RecentWords(vm, open) }
    item(key = "saved") { SavedWords(vm, open) }
    item(key = "suggested") { Suggested(vm, open) }
    item(key = "credits") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text(
                "Word studies use Strong's Hebrew and Greek dictionaries (public domain), STEP Bible's lexicons " +
                    "(STEPBible.org, CC BY 4.0) and the KJV's word tags from CrossWire. Every word in VerseUp's study pack " +
                    "comes from these sources; nothing is generated.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
            )
            TextButton(onClick = onAttributions, modifier = Modifier.padding(start = 0.dp)) { Text("Attributions and licences") }
        }
    }
}

@Composable
private fun RecentWords(vm: StudyViewModel, open: (WordStudyRequest) -> Unit) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    if (recent.isEmpty()) {
        Column(Modifier.padding(20.dp)) {
            Text("Study the words behind the English", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Hold any verse in the Bible and tap a word to see the Hebrew or Greek it translates, what it means, and everywhere else the KJV uses it. Or search above.",
                style = MaterialTheme.typography.bodyMedium, color = colors.secondary,
            )
        }
        return
    }
    val last = recent.first()
    Column(Modifier.padding(top = 12.dp)) {
        // Continue the latest study.
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(StudyColors.language(Language.of(last.lang)).copy(alpha = 0.10f))
                .clickable { open(requestFor(last.entry)) }.padding(20.dp),
        ) {
            Text("CONTINUE STUDYING", style = TextStyle(fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold), color = colors.secondary)
            Spacer(Modifier.height(6.dp))
            Text(last.lemma, style = originalStyle(Language.of(last.lang), 32.sp).copy(textDirection = TextDirection.Content))
            Text("${last.gloss} · ${last.entry}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            last.verse?.let { Text("from ${it.display()}", style = MaterialTheme.typography.bodySmall, color = colors.secondary) }
        }
        if (recent.size > 1) {
            SectionTitle("Recently studied")
            LazyRow(horizontalArrangement = ChipSpacing, contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(recent.drop(1), key = { it.entry }) { w ->
                    WordTile(w.lemma, w.gloss, w.entry, Language.of(w.lang)) { open(requestFor(w.entry)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun WordTile(lemma: String, gloss: String, id: String, language: Language, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 110.dp, max = 170.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)).clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(lemma, style = originalStyle(language, 22.sp).copy(textDirection = TextDirection.Content), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(gloss, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text("$id · ${language.label}", style = MaterialTheme.typography.labelSmall, color = StudyColors.language(language))
    }
}

/** Saved words: filter by language, star favourites, add a note, remove. */
@Composable
private fun SavedWords(vm: StudyViewModel, open: (WordStudyRequest) -> Unit) {
    val saved by vm.saved.collectAsStateWithLifecycle()
    var lang by rememberSaveable { mutableStateOf<String?>(null) }
    var favourites by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedWord?>(null) }
    val colors = MaterialTheme.colorScheme
    SectionTitle(if (saved.isEmpty()) "Saved words" else "Saved words · ${saved.size}")
    if (saved.isEmpty()) {
        Text(
            "Words you save with the bookmark in a word study appear here, with your notes.",
            style = MaterialTheme.typography.bodySmall, color = colors.secondary, modifier = Modifier.padding(horizontal = 20.dp),
        )
        return
    }
    LazyRow(horizontalArrangement = ChipSpacing, contentPadding = PaddingValues(horizontal = 16.dp)) {
        item { FilterChip(selected = lang == null && !favourites, onClick = { lang = null; favourites = false }, label = { Text("All") }, shape = RoundedCornerShape(50), colors = studyChipColors()) }
        item { FilterChip(selected = lang == "heb", onClick = { lang = if (lang == "heb") null else "heb" }, label = { Text("Hebrew") }, shape = RoundedCornerShape(50), colors = studyChipColors()) }
        item { FilterChip(selected = lang == "grc", onClick = { lang = if (lang == "grc") null else "grc" }, label = { Text("Greek") }, shape = RoundedCornerShape(50), colors = studyChipColors()) }
        item { FilterChip(selected = favourites, onClick = { favourites = !favourites }, label = { Text("Starred") }, shape = RoundedCornerShape(50), colors = studyChipColors()) }
    }
    val shown = saved.filter { (lang == null || it.lang == lang || (lang == "heb" && it.lang == "arc")) && (!favourites || it.favorite) }
    Column {
        for (w in shown) {
            Row(
                Modifier.fillMaxWidth().clickable { open(requestFor(w.entry, w.verse)) }.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(w.lemma, style = originalStyle(Language.of(w.lang), 20.sp).copy(textDirection = TextDirection.Content))
                        Spacer(Modifier.width(8.dp))
                        Text(w.entry, style = MaterialTheme.typography.labelMedium, color = StudyColors.language(Language.of(w.lang)))
                    }
                    Text(w.gloss, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (w.note.isNotBlank()) Text(w.note, style = MaterialTheme.typography.bodySmall, color = colors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.updateSaved(w.copy(favorite = !w.favorite)) }) {
                    Icon(if (w.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = if (w.favorite) "Unstar" else "Star", tint = if (w.favorite) colors.primary else colors.secondary)
                }
                IconButton(onClick = { editing = w }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit note", tint = colors.secondary) }
            }
        }
        if (shown.isEmpty()) Text("No saved words match.", style = MaterialTheme.typography.bodySmall, color = colors.secondary, modifier = Modifier.padding(20.dp))
    }
    editing?.let { w ->
        var note by remember(w.entry) { mutableStateOf(w.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${w.lemma} · ${w.entry}") },
            text = {
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Your note") },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            },
            confirmButton = { TextButton(onClick = { vm.updateSaved(w.copy(note = note)); editing = null }) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.removeSaved(w.entry); editing = null }) {
                        Icon(Icons.Rounded.Delete, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Remove word")
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}

/** A few well-known word studies. The cards read every field from the study pack. */
@Composable
private fun Suggested(vm: StudyViewModel, open: (WordStudyRequest) -> Unit) {
    var entries by remember { mutableStateOf<Map<Int, StrongsEntry>>(emptyMap()) }
    var glosses by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        runCatching {
            val s = vm.store()
            val nums = StudyViewModel.SUGGESTED_HEBREW + StudyViewModel.SUGGESTED_GREEK
            entries = s.strongs(nums)
            // STEP Bible's gloss is the clearest one-line meaning; Strong's definition is the fallback.
            glosses = nums.associateWith { n -> s.stepSenses(n).firstOrNull()?.gloss ?: "" }
        }
    }
    for ((title, nums) in listOf("Hebrew words to start with" to StudyViewModel.SUGGESTED_HEBREW, "Greek words to start with" to StudyViewModel.SUGGESTED_GREEK)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = ChipSpacing, contentPadding = PaddingValues(horizontal = 16.dp)) {
            val list = nums.mapNotNull { entries[it] }
            if (list.isEmpty()) item { Box(Modifier.width(200.dp)) { SkeletonLine(0.9f, 60) } }
            items(list, key = { it.num }) { e ->
                WordTile(e.lemma, glosses[e.num]?.takeIf { it.isNotBlank() } ?: e.definition?.take(40) ?: "", e.id, e.language) { open(WordStudyRequest(num = e.num)) }
            }
        }
    }
}
