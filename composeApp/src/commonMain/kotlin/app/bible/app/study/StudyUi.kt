package app.bible.app.study

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bible.app.platform.reducedMotion
import app.bible.app.resources.Res
import app.bible.app.resources.noto_serif_hebrew_regular
import app.bible.app.resources.noto_serif_hebrew_semibold
import app.bible.app.ui.LocalScriptureFont
import app.bible.data.study.Language
import org.jetbrains.compose.resources.Font

/**
 * Word-study colours. Restrained accents tell the languages and sources apart; every one is always paired with a
 * text label, so colour is never the only signal.
 */
object StudyColors {
    @Composable fun hebrew(): Color = if (isDark()) Color(0xFFE3A977) else Color(0xFF94551F)
    @Composable fun greek(): Color = if (isDark()) Color(0xFF93B6E2) else Color(0xFF305F92)
    @Composable fun step(): Color = if (isDark()) Color(0xFF86C7B6) else Color(0xFF2B7A6A)
    @Composable fun strongs(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    @Composable fun language(l: Language): Color = if (l == Language.Greek) greek() else hebrew()

    @Composable private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminanceApprox() < 0.4f
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Hebrew is set in Noto Serif Hebrew (vowel points and cantillation); Greek in Literata. */
@Composable
fun hebrewFont(): FontFamily = FontFamily(
    Font(Res.font.noto_serif_hebrew_regular, FontWeight.Normal),
    Font(Res.font.noto_serif_hebrew_semibold, FontWeight.SemiBold),
)

/** The font and direction for an original-language word. */
@Composable
fun originalStyle(language: Language, size: TextUnit, weight: FontWeight = FontWeight.Normal): TextStyle {
    val family = if (language == Language.Greek) LocalScriptureFont.current else hebrewFont()
    return TextStyle(
        fontFamily = family,
        fontSize = size,
        fontWeight = weight,
        lineHeight = size * 1.35f,
        textDirection = if (language.rightToLeft) TextDirection.Rtl else TextDirection.Ltr,
    )
}

/** "Hebrew", "Aramaic" or "Greek" in the language's accent, as a small rounded label. */
@Composable
fun LanguageBadge(language: Language, modifier: Modifier = Modifier) {
    val c = StudyColors.language(language)
    Text(
        language.label.uppercase(),
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
        color = c,
        modifier = modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A small source label such as "STRONG'S" or "STEP BIBLE". */
@Composable
fun SourceTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
        color = color,
        modifier = modifier,
    )
}

/** A section title inside a study view (marked as a heading for screen readers). */
@Composable
fun StudyHeading(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    Text(
        text.uppercase(),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp),
        color = color,
        modifier = modifier.padding(top = 22.dp, bottom = 8.dp).semantics { heading() },
    )
}

/**
 * The source switch: Strong's | STEP Bible | Compare. A pill whose highlight slides to the chosen option (it jumps
 * when the system asks for reduced motion).
 */
@Composable
fun SourceSwitch(selected: StudySource, onSelect: (StudySource) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val options = StudySource.entries
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = 0.07f)).padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val reduce = remember { reducedMotion() }
        val x by animateDpAsState(
            w * options.indexOf(selected),
            animationSpec = if (reduce) snap<Dp>() else spring<Dp>(dampingRatio = 0.82f, stiffness = 520f),
            label = "source",
        )
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(colors.surface))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            for (o in options) {
                val on = o == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .clickable(role = Role.Tab) { onSelect(o) }
                        .semantics { this.selected = on; contentDescription = "${o.label} source" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        o.label,
                        style = TextStyle(fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (on) colors.onSurface else colors.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/** A rounded card for one block of study content. */
@Composable
fun StudyCard(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.onSurface.copy(alpha = 0.045f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (accent != null) Box(Modifier.width(28.dp).height(3.dp).clip(CircleShape).background(accent))
        if (accent != null) Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Renders STEP's "**bold**" marks and line breaks. */
fun markup(text: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    for ((i, part) in text.split("**").withIndex()) {
        if (i > 0) bold = !bold
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}

/** A skeleton line that stands in for text while it loads, at the same size, so nothing jumps. */
@Composable
fun SkeletonLine(widthFraction: Float, height: Int = 14, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth(widthFraction).padding(vertical = 5.dp).heightIn(min = height.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

/** Plain-language names for the part-of-speech codes in Strong's Hebrew (Open Scriptures edition). */
fun hebrewPartOfSpeech(code: String?): String? {
    if (code.isNullOrBlank()) return null
    return code.split(' ').joinToString(" or ") { c ->
        when (c) {
            "n-m" -> "masculine noun"
            "n-f" -> "feminine noun"
            "n" -> "noun"
            "n-pr-m" -> "name of a man"
            "n-pr-f" -> "name of a woman"
            "n-pr-loc" -> "place name"
            "n-pr" -> "proper name"
            "v" -> "verb"
            "a" -> "adjective"
            "a-m" -> "masculine adjective"
            "a-f" -> "feminine adjective"
            "adv" -> "adverb"
            "prep" -> "preposition"
            "conj" -> "conjunction"
            "inj", "i" -> "interjection"
            "prt" -> "particle"
            "pron", "p" -> "pronoun"
            "d", "dp" -> "demonstrative"
            "r" -> "relative particle"
            else -> c
        }
    }
}

/**
 * STEP's simple grammar code ("H:N-M", "G:V", "N:N-M-P") in words. Only the parts the code gives are shown, and
 * the explanations are deliberately short.
 */
fun stepGrammar(code: String?): String? {
    if (code.isNullOrBlank()) return null
    val lang = when (code.substringBefore(':')) {
        "H" -> "Hebrew"; "A" -> "Aramaic"; "G" -> "Greek"; "N" -> "Name"; else -> null
    }
    val parts = code.substringAfter(':').split('-')
    val type = when (parts.getOrNull(0)) {
        "N" -> "noun"; "V" -> "verb"; "A" -> "adjective"; "Adv" -> "adverb"; "Art" -> "article"; "Conj" -> "conjunction"
        "Prep" -> "preposition"; "Part" -> "particle"; "Intj" -> "interjection"; "Neg" -> "negative"; "PerP" -> "personal pronoun"
        "DemP" -> "demonstrative pronoun"; "RelP" -> "relative pronoun"; "PosP" -> "possessive pronoun"; "RefP" -> "reflexive pronoun"
        "Intg" -> "interrogative"; "Cond" -> "conditional"; "Cor" -> "correlative"; "ImpP" -> "impersonal pronoun"
        else -> parts.getOrNull(0)?.lowercase()
    }
    val gender = when (parts.getOrNull(1)) { "M" -> "masculine"; "F" -> "feminine"; "N" -> "neuter"; "C" -> "common gender"; else -> null }
    val extra = when (parts.getOrNull(2)) {
        "P" -> "person"; "L" -> "place"; "LG", "PG", "G" -> "people group"; "T" -> "title"; else -> null
    }
    return listOfNotNull(lang, gender, type, extra?.let { "($it)" }).joinToString(" ").replaceFirstChar { it.uppercase() }
}

/** Splits text into plain parts and Strong's ids ("H430", "G25") so ids can be tapped. */
fun strongsLinks(text: String): List<Pair<String, Int?>> {
    val out = ArrayList<Pair<String, Int?>>()
    var last = 0
    for (m in Regex("\\b([HG])(\\d{1,4})\\b").findAll(text)) {
        if (m.range.first > last) out += text.substring(last, m.range.first) to null
        out += m.value to app.bible.data.study.StrongsNumber.of(m.groupValues[1][0], m.groupValues[2].toInt())
        last = m.range.last + 1
    }
    if (last < text.length) out += text.substring(last) to null
    return out
}

/** Arrangement used by the study chips. */
val ChipSpacing = Arrangement.spacedBy(8.dp)

/** Filter chips in the app's own colours (Material's default selected colour is a lavender that clashes with both themes). */
@Composable
fun studyChipColors(): androidx.compose.material3.SelectableChipColors {
    val c = MaterialTheme.colorScheme
    return androidx.compose.material3.FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = c.onSurface.copy(alpha = 0.85f),
        selectedContainerColor = c.primary.copy(alpha = 0.18f),
        selectedLabelColor = c.onSurface,
    )
}
