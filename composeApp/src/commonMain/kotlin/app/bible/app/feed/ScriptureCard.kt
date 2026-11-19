package app.bible.app.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bible.app.data.VerseText
import app.bible.app.ui.ScriptureFont
import app.bible.app.ui.cardBackground

/**
 * A full-screen Scripture card (text-only variant). The reference and translation sit above the text; verse
 * numbers are small superscripts; words the translators supplied are in italics, as printed in the KJV.
 */
@Composable
fun ScriptureCard(
    card: FeedCard,
    prefs: ReadingPrefs,
    translation: String,
    loadText: suspend (FeedCard) -> List<VerseText>,
    loadHeading: suspend (FeedCard) -> String?,
    modifier: Modifier = Modifier,
) {
    var verses by remember(card) { mutableStateOf<List<VerseText>?>(null) }
    var heading by remember(card) { mutableStateOf<String?>(null) }
    LaunchedEffect(card) {
        verses = loadText(card)
        heading = loadHeading(card)
    }
    val colors = MaterialTheme.colorScheme
    // Dense cards (10 verses) are given a larger character budget, so their type is set a little smaller.
    val densityScale = if (card.last.verse - card.first.verse >= 7) 0.88f else 1f
    val size = (21 * prefs.fontScale * densityScale).sp

    // The card only becomes scrollable when its text overflows (very large type). Otherwise the scroll modifier
    // would claim the drag and the pager would need a long swipe instead of a flick.
    val scroll = rememberScrollState()
    Box(
        modifier
            .fillMaxSize()
            .background(cardBackground(card.first.book, prefs.dark)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(top = 72.dp, bottom = 64.dp)
                .verticalScroll(scroll, enabled = scroll.maxValue > 0),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "${card.reference.uppercase()} · ${translation.uppercase()}",
                style = TextStyle(fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium),
                color = colors.primary,
            )
            heading?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = TextStyle(fontFamily = ScriptureFont, fontStyle = FontStyle.Italic, fontSize = (size.value * 0.8f).sp), color = colors.secondary)
            }
            Spacer(Modifier.height(18.dp))
            val v = verses
            if (v != null) {
                Text(
                    text = scriptureText(v, colors.secondary),
                    style = TextStyle(fontFamily = ScriptureFont, fontSize = size, lineHeight = size * 1.5f),
                    color = colors.onBackground,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().semantics {
                        contentDescription = card.reference + ". " + v.joinToString(" ") { it.text }
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

private fun scriptureText(verses: List<VerseText>, numberColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        verses.forEachIndexed { i, v ->
            if (i > 0) append(if (v.paragraphStart || v.poetry) "\n" else " ")
            withStyle(SpanStyle(fontSize = 11.sp, baselineShift = BaselineShift.Superscript, color = numberColor, fontWeight = FontWeight.SemiBold)) {
                append(v.id.verse.toString())
            }
            append(" ")
            val start = length
            append(v.text)
            for (r in v.italics) {
                if (r.last < v.text.length) addStyle(SpanStyle(fontStyle = FontStyle.Italic), start + r.first, start + r.last + 1)
            }
        }
    }
