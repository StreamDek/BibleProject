package app.bible.app.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import app.bible.app.ui.topSafePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import app.bible.app.ui.glass
import app.bible.app.ui.glassSource
import app.bible.app.ui.rememberGlassState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.app.data.VerseText
import app.bible.app.ui.LocalScriptureColors
import app.bible.app.ui.LocalScriptureFont
import app.bible.core.Book
import app.bible.core.Canon
import app.bible.core.VerseId
import app.bible.core.VerseRange
import app.bible.data.user.VerseMarks
import kotlinx.coroutines.flow.distinctUntilChanged

/** One row of the classic reader: a chapter heading or a verse. */
private sealed interface ClassicRow {
    val key: Int
    data class ChapterHeading(val book: Int, val chapter: Int) : ClassicRow { override val key get() = -(book * 1_000 + chapter) }
    data class Verse(val id: VerseId) : ClassicRow { override val key get() = id.value }
}

/**
 * The classic reader: one book as continuous text, a verse per line as in a printed KJV, with chapter
 * headings. It opens at the feed's position and hands its position back when closed, so both views stay in
 * sync. Holding a verse opens the same actions as in the feed.
 */
@Composable
fun ClassicReader(
    vm: FeedViewModel,
    start: VerseId,
    onClose: () -> Unit,
    onJump: () -> Unit,
    onSettings: () -> Unit,
    onVerseAction: (VerseActionTarget) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val revision by vm.marksRevision.collectAsStateWithLifecycle()
    val book = Canon.book(start.book)
    // Rebuild when the book changes (Jump, search, or next/previous book).
    val rows = remember(book.number) { rowsFor(book) }
    val listState = remember(book.number, state.generation) {
        androidx.compose.foundation.lazy.LazyListState(
            firstVisibleItemIndex = rows.indexOfFirst { it is ClassicRow.Verse && it.id == start }.let { i ->
                if (i > 0 && (rows[i - 1] as? ClassicRow.ChapterHeading) != null) i - 1 else i.coerceAtLeast(0)
            },
        )
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            val verse = rows.drop(i).firstNotNullOfOrNull { (it as? ClassicRow.Verse)?.id }
            if (verse != null) vm.onClassicPosition(verse)
        }
    }
    val colors = MaterialTheme.colorScheme
    val currentChapter = rows.drop(listState.firstVisibleItemIndex).firstNotNullOfOrNull { (it as? ClassicRow.Verse)?.id }?.chapter ?: start.chapter

    val glass = rememberGlassState()
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(glass),
            // The text starts below the glass header and scrolls under it.
            contentPadding = PaddingValues(top = with(density) { headerHeight.toDp() }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ClassicRow.ChapterHeading -> ChapterHeading(vm, row)
                    is ClassicRow.Verse -> ClassicVerse(vm, row.id, state.prefs, revision, onVerseAction)
                }
            }
            item(key = "end") {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Canon.bookOrNull(book.number - 1)?.let { prev ->
                        TextButton(onClick = { vm.open(prev.firstVerse) }) { Text("‹ ${prev.name}") }
                    } ?: Spacer(Modifier)
                    Canon.bookOrNull(book.number + 1)?.let { next ->
                        TextButton(onClick = { vm.open(next.firstVerse) }) { Text("${next.name} ›") }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }.glass(glass)
                .topSafePadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("Cards", color = colors.primary) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onJump) {
                Text("${book.name} $currentChapter ▾", color = colors.onBackground, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSettings) { Text("Aa", color = colors.onBackground) }
        }
    }
}

private fun rowsFor(book: Book): List<ClassicRow> = buildList {
    for (c in 1..book.chapterCount) {
        add(ClassicRow.ChapterHeading(book.number, c))
        for (v in 1..book.verseCount(c)) add(ClassicRow.Verse(VerseId.of(book.number, c, v)))
    }
}

@Composable
private fun ChapterHeading(vm: FeedViewModel, row: ClassicRow.ChapterHeading) {
    val colors = MaterialTheme.colorScheme
    val psalmTitle by produceState<String?>(null, row) { value = vm.psalmTitle(row.book, row.chapter) }
    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 8.dp)) {
        Text(
            if (row.book == 19) "Psalm ${row.chapter}" else "Chapter ${row.chapter}",
            style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            color = colors.primary,
        )
        psalmTitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = TextStyle(fontFamily = LocalScriptureFont.current, fontStyle = FontStyle.Italic, fontSize = 16.sp), color = colors.secondary)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ClassicVerse(
    vm: FeedViewModel,
    id: VerseId,
    prefs: ReadingPrefs,
    revision: Int,
    onVerseAction: (VerseActionTarget) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scripture = LocalScriptureColors.current
    val haptics = LocalHapticFeedback.current
    val text by produceState<VerseText?>(null, id) { value = vm.chapterText(id.book, id.chapter).firstOrNull { it.id == id } }
    val section by produceState<String?>(null, id) { value = vm.sections(VerseRange.single(id))[id] }
    val marks by produceState(VerseMarks.None, id, revision) { value = vm.marks(VerseRange.single(id)) }
    val size = (19 * prefs.fontScale).sp
    Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
        section?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, style = TextStyle(fontFamily = LocalScriptureFont.current, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.9f).sp), color = colors.onBackground.copy(alpha = 0.85f))
            Spacer(Modifier.height(4.dp))
        }
        val t = text
        Text(
            text = t?.let { buildScripture(listOf(it), marks, prefs.redLetters, scripture, colors.secondary, colors.primary).text }
                ?: androidx.compose.ui.text.AnnotatedString(" "),
            style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = size, lineHeight = size * 1.55f),
            color = colors.onBackground,
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClickLabel = "Verse options",
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Offer the neighbouring verses too, so a passage can be highlighted or copied at once.
                        val last = Canon.book(id.book).verseCount(id.chapter)
                        val from = (id.verse - 4).coerceAtLeast(1)
                        val to = (from + 9).coerceAtMost(last)
                        onVerseAction(VerseActionTarget((from..to).map { VerseId.of(id.book, id.chapter, it) }, id))
                    },
                )
                .padding(vertical = 3.dp),
        )
    }
}
