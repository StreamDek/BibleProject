package app.bible.app.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bible.app.data.VerseText
import app.bible.app.ui.LocalScriptureFont
import app.bible.core.VerseId
import app.bible.data.study.Language
import app.bible.data.study.MorphInfo
import app.bible.data.study.OriginalVerse
import app.bible.data.study.OriginalWord
import app.bible.data.study.StrongsNumber

/**
 * The Hebrew or Greek of one verse, word by word: each word with its transliteration, STEP Bible's English,
 * Strong's number and (optionally) its grammar in plain words. Hebrew runs right to left. Tap a word to study it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterlinearPage(vm: StudyViewModel, verse: VerseId, highlight: Int?, onWord: (OriginalWord) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var original by remember(verse) { mutableStateOf<OriginalVerse?>(null) }
    var kjv by remember(verse) { mutableStateOf<VerseText?>(null) }
    var morph by remember(verse) { mutableStateOf<Map<String, MorphInfo>>(emptyMap()) }
    var missing by remember(verse) { mutableStateOf(false) }
    var showGrammar by rememberSaveable { mutableStateOf(false) }
    var showXlit by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(verse) {
        kjv = vm.verse(verse)
        val o = vm.original(verse)
        if (o == null) { missing = true; return@LaunchedEffect }
        original = o
        morph = vm.morph(o.words.flatMap { w -> w.parts.mapNotNull { it.grammar } })
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
        val o = original
        Text(
            "${verse.display().uppercase()} · ${if (o?.language == Language.Greek) "GREEK" else "HEBREW"}",
            style = TextStyle(fontSize = 11.sp, letterSpacing = 1.4.sp),
            color = colors.primary,
        )
        kjv?.let {
            Spacer(Modifier.height(4.dp))
            Text(it.text, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 15.sp, lineHeight = 22.sp), color = colors.onSurface.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = ChipSpacing) {
            FilterChip(selected = showXlit, onClick = { showXlit = !showXlit }, label = { Text("Transliteration") }, shape = RoundedCornerShape(50), colors = studyChipColors())
            FilterChip(selected = showGrammar, onClick = { showGrammar = !showGrammar }, label = { Text("Grammar") }, shape = RoundedCornerShape(50), colors = studyChipColors())
        }
        Spacer(Modifier.height(12.dp))
        if (missing) {
            Text("The original text of this verse isn't in the study pack.", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
            return@Column
        }
        if (o == null) { SkeletonLine(0.9f, 60); SkeletonLine(0.7f, 60); return@Column }

        val rtl = o.language != Language.Greek
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (w in o.words) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        WordCell(w, highlight != null && highlight in w.nums, showXlit, showGrammar, morph) { onWord(w) }
                    }
                }
            }
        }
        if (o.words.any { it.isQere }) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Q marks the scribes' reading (Qere), which translators follow, where the written Hebrew (Ketiv) differs.",
                style = MaterialTheme.typography.bodySmall, color = colors.secondary,
            )
        }
        if (o.words.any { it.traditionalDiffers }) {
            Spacer(Modifier.height(12.dp))
            Text(
                "TR marks a word where the Greek the KJV translators used (the Textus Receptus) has a different word. Tap it to see that reading.",
                style = MaterialTheme.typography.bodySmall, color = colors.secondary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (o.language == Language.Greek) "Greek text, English and grammar: STEP Bible TAGNT (STEPBible.org, Tyndale House), CC BY 4.0. The words are those of the Textus Receptus the KJV translated."
            else "Hebrew text (Leningrad Codex), English and grammar: STEP Bible TAHOT (STEPBible.org, Tyndale House), CC BY 4.0.",
            style = MaterialTheme.typography.bodySmall, color = colors.secondary,
        )
    }
}

@Composable
private fun WordCell(w: OriginalWord, highlighted: Boolean, showXlit: Boolean, showGrammar: Boolean, morph: Map<String, MorphInfo>, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = StudyColors.language(w.language)
    val root = w.root
    Column(
        Modifier.widthIn(min = 64.dp, max = 180.dp).clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) accent.copy(alpha = 0.16f) else colors.onSurface.copy(alpha = 0.045f))
            .then(if (highlighted) Modifier.border(1.5.dp, accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClickLabel = "Study ${w.xlit}", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { contentDescription = "${w.xlit}, ${w.gloss}${root?.stepId?.let { ", $it" } ?: ""}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(w.text, style = originalStyle(w.language, 22.sp), textAlign = TextAlign.Center)
        if (showXlit && w.xlit.isNotBlank()) Text(w.xlit, style = TextStyle(fontSize = 12.sp, fontStyle = FontStyle.Italic), color = colors.onSurface.copy(alpha = 0.75f), textAlign = TextAlign.Center)
        Text(w.gloss, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 14.sp), textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            root?.stepId?.let { Text(StrongsNumber.parse(it)?.let(StrongsNumber::id) ?: it, style = MaterialTheme.typography.labelSmall, color = accent) }
            if (w.isQere) Text("Q", style = MaterialTheme.typography.labelSmall, color = colors.secondary)
            if (w.traditionalDiffers) Text("TR", style = MaterialTheme.typography.labelSmall, color = colors.error)
        }
        if (showGrammar) {
            val g = w.parts.mapNotNull { p -> p.grammar?.let { morph[it]?.summary ?: it } }
            if (g.isNotEmpty()) Text(g.joinToString(" + "), style = MaterialTheme.typography.labelSmall, color = colors.secondary, textAlign = TextAlign.Center)
        }
    }
}

/**
 * How a word is used in one verse: its form there, the grammar in plain words, and the STEP sense the verse uses.
 * Shown in the word sheet when it was opened from a verse.
 */
@Composable
fun InThisVerse(words: List<OriginalWord>, morph: Map<String, MorphInfo>, num: Int?, onInterlinear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (words.isEmpty()) return
    StudyHeading("In this verse")
    for (w in words.take(3)) {
        StudyCard(accent = StudyColors.language(w.language)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(w.text, style = originalStyle(w.language, 26.sp))
                Spacer(Modifier.width(10.dp))
                Text("  " + w.xlit, style = TextStyle(fontSize = 15.sp, fontStyle = FontStyle.Italic), color = colors.onSurface.copy(alpha = 0.8f))
            }
            Text("“${w.gloss}”", style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 16.sp))
            for (p in w.parts) {
                val m = p.grammar?.let { morph[it] } ?: continue
                val isThis = num != null && p.num == num
                Spacer(Modifier.height(8.dp))
                Text(
                    (if (w.parts.size > 1) "${p.text}: " else "") + (m.summary ?: m.parsing),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isThis || w.parts.size == 1) colors.onSurface else colors.secondary,
                )
                m.explanation?.let { Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodySmall, color = colors.secondary) }
                m.example?.let { Text("For example: $it", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = colors.secondary) }
            }
            if (w.isQere && w.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp)); Text("Written text (Ketiv): ${w.note}", style = MaterialTheme.typography.bodySmall, color = colors.secondary)
            }
            if (w.traditionalDiffers) {
                Spacer(Modifier.height(8.dp))
                Text("The Greek the KJV translated reads differently here: ${w.note}", style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    androidx.compose.material3.TextButton(onClick = onInterlinear) {
        Text(if (words.first().language == Language.Greek) "See the whole verse in Greek" else "See the whole verse in Hebrew")
    }
    Text("Grammar: STEP Bible (TEHMC, TEGMC), CC BY 4.0.", style = MaterialTheme.typography.bodySmall, color = colors.secondary)
}
