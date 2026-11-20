package app.bible.app.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import app.bible.app.ui.topSafePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bible.app.data.VerseText
import app.bible.app.ui.LocalScriptureColors
import app.bible.app.ui.LocalScriptureFont
import app.bible.app.ui.ScriptureColors
import app.bible.app.ui.cardBackground
import app.bible.core.VerseId
import app.bible.data.user.VerseMarks

/**
 * A full-screen Scripture card (text-only variant). The reference and translation sit above the text; verse
 * numbers are small superscripts; words the translators supplied are in italics, as printed in the KJV, and
 * the words of Jesus can be shown in red. Tap toggles the controls; holding a verse opens its actions.
 */
@Composable
fun ScriptureCard(
    card: FeedCard,
    prefs: ReadingPrefs,
    translation: String,
    vm: FeedViewModel,
    marksRevision: Int,
    onTap: () -> Unit,
    onVerseAction: (VerseId) -> Unit,
    modifier: Modifier = Modifier,
    /** A line above the reference, e.g. a story step title. */
    label: String? = null,
    /** Tapping the reference, e.g. to open the passage in the Bible from a story. */
    onReferenceTap: (() -> Unit)? = null,
    /** Room left at the top for the reader's controls. */
    topInset: Dp = 72.dp,
) {
    var verses by remember(card) { mutableStateOf<List<VerseText>?>(null) }
    var heading by remember(card) { mutableStateOf<String?>(null) }
    var section by remember(card) { mutableStateOf<String?>(null) }
    var marks by remember(card) { mutableStateOf(VerseMarks.None) }
    LaunchedEffect(card) {
        verses = vm.textFor(card)
        heading = vm.heading(card)
        section = vm.sectionTitle(card)
    }
    LaunchedEffect(card, marksRevision) { marks = vm.marks(card.range) }

    val colors = MaterialTheme.colorScheme
    val scripture = LocalScriptureColors.current
    val font = LocalScriptureFont.current
    val haptics = LocalHapticFeedback.current
    // Dense cards (7 or 10 verses) are given a larger character budget, so their type is set a little smaller.
    val span = card.last.verse - card.first.verse + 1
    val densityScale = when {
        span >= 8 -> 0.88f
        span >= 6 -> 0.94f
        else -> 1f
    }
    val size = (21 * prefs.fontScale * densityScale).sp

    // For mapping a long-press position to the verse under the finger.
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val v = verses
    val built = remember(v, marks, prefs.redLetters, scripture, colors.secondary, colors.primary) {
        v?.let { buildScripture(it, marks, prefs.redLetters, scripture, colors.secondary, colors.primary) }
    }

    val latestBuilt by rememberUpdatedState(built)
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnVerseAction by rememberUpdatedState(onVerseAction)

    // The card only becomes scrollable when its text overflows (very large type). Otherwise the scroll modifier
    // would claim the drag and the pager would need a long swipe instead of a flick.
    val scroll = rememberScrollState()
    Box(
        modifier
            .fillMaxSize()
            .background(cardBackground(card.first.book, prefs.dark))
            .onGloballyPositioned { boxCoords = it }
            .pointerInput(card) {
                detectTapGestures(
                    onTap = { latestOnTap() },
                    onLongPress = { pos ->
                        val verse = verseAt(pos, boxCoords, textCoords, layout, latestBuilt) ?: card.first
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestOnVerseAction(verse)
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .topSafePadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(top = topInset, bottom = 96.dp)
                .verticalScroll(scroll, enabled = scroll.maxValue > 0),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            label?.let {
                Text(it, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp), color = colors.secondary)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = "${card.reference.uppercase()} · ${translation.uppercase()}" + if (onReferenceTap != null) "  ›" else "",
                style = TextStyle(fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium),
                color = colors.primary,
                modifier = if (onReferenceTap != null) Modifier.clickable(onClickLabel = "Open in the Bible") { onReferenceTap() } else Modifier,
            )
            section?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.82f).sp), color = colors.onBackground.copy(alpha = 0.85f))
            }
            heading?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = TextStyle(fontFamily = font, fontStyle = FontStyle.Italic, fontSize = (size.value * 0.8f).sp), color = colors.secondary)
            }
            Spacer(Modifier.height(18.dp))
            if (v != null && built != null) {
                Text(
                    text = built.text,
                    style = TextStyle(fontFamily = font, fontSize = size, lineHeight = size * 1.5f),
                    color = colors.onBackground,
                    onTextLayout = { layout = it },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                        .onGloballyPositioned { textCoords = it }
                        .semantics {
                            contentDescription = card.reference + ". " + v.joinToString(" ") { it.text }
                            onLongClick(label = "Verse options") { onVerseAction(card.first); true }
                        },
                )
                if (card.continues) {
                    Spacer(Modifier.height(12.dp))
                    Text("continues…", style = TextStyle(fontSize = 13.sp, fontStyle = FontStyle.Italic), color = colors.secondary)
                }
            }
        }
    }
}

/** Scripture text plus where each verse starts in it. */
class BuiltScripture(val text: AnnotatedString, val verseStarts: List<Pair<Int, VerseId>>)

fun buildScripture(
    verses: List<VerseText>,
    marks: VerseMarks,
    redLetters: Boolean,
    scripture: ScriptureColors,
    numberColor: Color,
    markColor: Color,
    newLinePerVerse: Boolean = false,
): BuiltScripture {
    val starts = ArrayList<Pair<Int, VerseId>>(verses.size)
    val text = buildAnnotatedString {
        verses.forEachIndexed { i, v ->
            if (i > 0) append(if (newLinePerVerse || v.paragraphStart || v.poetry) "\n" else " ")
            starts += length to v.id
            val bookmarked = v.id in marks.bookmarks
            withStyle(
                SpanStyle(
                    fontSize = 11.sp,
                    baselineShift = BaselineShift.Superscript,
                    color = if (bookmarked) markColor else numberColor,
                    fontWeight = if (bookmarked) FontWeight.Bold else FontWeight.SemiBold,
                ),
            ) {
                append(v.id.verse.toString())
                if (bookmarked) append("◆")
            }
            append(" ")
            val start = length
            append(v.text)
            marks.highlights[v.id]?.let { c ->
                scripture.highlights.getOrNull(c)?.let { addStyle(SpanStyle(background = it), start, length) }
            }
            if (redLetters) for (r in v.red) {
                if (r.last < v.text.length) addStyle(SpanStyle(color = scripture.red), start + r.first, start + r.last + 1)
            }
            for (r in v.italics) {
                if (r.last < v.text.length) addStyle(SpanStyle(fontStyle = FontStyle.Italic), start + r.first, start + r.last + 1)
            }
            if (v.id in marks.notes) {
                withStyle(SpanStyle(fontSize = 12.sp, baselineShift = BaselineShift.Superscript, color = markColor)) { append(" ✎") }
            }
        }
    }
    return BuiltScripture(text, starts)
}

private fun verseAt(
    pos: androidx.compose.ui.geometry.Offset,
    box: LayoutCoordinates?,
    text: LayoutCoordinates?,
    layout: TextLayoutResult?,
    built: BuiltScripture?,
): VerseId? {
    if (box == null || text == null || layout == null || built == null || !box.isAttached || !text.isAttached) return null
    val local = text.localPositionOf(box, pos)
    val offset = layout.getOffsetForPosition(local)
    return built.verseStarts.lastOrNull { it.first <= offset }?.second
}
