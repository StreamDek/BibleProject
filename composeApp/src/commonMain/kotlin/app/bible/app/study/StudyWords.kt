package app.bible.app.study

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bible.app.data.VerseText
import app.bible.app.ui.LocalScriptureFont
import app.bible.data.study.WordTag

/** One tappable piece of a verse: a tagged phrase (with its Strong's numbers) or an untagged word. */
data class WordSpan(val start: Int, val end: Int, val nums: List<Int>)

/**
 * Splits a verse into tappable spans: each KJV word tag becomes one span, and every other word becomes an untagged
 * span (so tapping it can explain why there is no original word, rather than doing nothing).
 */
fun wordSpans(text: String, tags: List<WordTag>): List<WordSpan> {
    val out = ArrayList<WordSpan>()
    val sorted = tags.filter { it.start < it.end && it.end <= text.length }.sortedBy { it.start }
    var pos = 0
    fun untagged(from: Int, to: Int) {
        for (m in WORD.findAll(text.substring(from, to))) out += WordSpan(from + m.range.first, from + m.range.last + 1, emptyList())
    }
    for (t in sorted) {
        if (t.start < pos) continue
        untagged(pos, t.start)
        out += WordSpan(t.start, t.end, t.nums)
        pos = t.end
    }
    untagged(pos, text.length)
    return out
}

private val WORD = Regex("[\\p{L}’']+")

/**
 * "Study a word": the verse with every word tappable. Tagged words are underlined with a dotted accent so they
 * read as links without shouting; untagged words are still tappable, and say why they have no original word.
 */
@Composable
fun StudyWordsSection(vm: StudyViewModel, verse: VerseText, onWord: (WordContext) -> Unit) {
    var tags by remember(verse.id) { mutableStateOf<List<WordTag>?>(null) }
    LaunchedEffect(verse.id) { tags = runCatching { vm.tags(verse.id) }.getOrDefault(emptyList()) }
    val t = tags ?: return
    if (t.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val spans = remember(verse, t) { wordSpans(verse.text, t) }
    val italics = verse.italics
    val text = remember(spans, verse, colors.primary) {
        buildAnnotatedString {
            var pos = 0
            for (s in spans) {
                if (s.start > pos) append(verse.text.substring(pos, s.start))
                val tagged = s.nums.isNotEmpty()
                val supplied = italics.any { s.start in it }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "w${s.start}",
                        styles = TextLinkStyles(
                            if (tagged) SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline, fontStyle = if (supplied) FontStyle.Italic else null)
                            else SpanStyle(fontStyle = if (supplied) FontStyle.Italic else null),
                        ),
                    ) { onWord(WordContext(verse.id, verse.text, s.start, s.end, s.nums)) },
                ) { append(verse.text.substring(s.start, s.end)) }
                pos = s.end
            }
            if (pos < verse.text.length) append(verse.text.substring(pos))
        }
    }
    Column {
        Text("Study a word", style = MaterialTheme.typography.labelMedium, color = colors.secondary)
        Spacer(Modifier.height(6.dp))
        Text(text, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 17.sp, lineHeight = 27.sp), color = colors.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap an underlined word to see its Hebrew or Greek.",
            style = MaterialTheme.typography.bodySmall, color = colors.secondary,
        )
    }
}
