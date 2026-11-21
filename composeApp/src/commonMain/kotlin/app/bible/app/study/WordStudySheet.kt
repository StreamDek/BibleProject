package app.bible.app.study

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.app.data.VerseText
import app.bible.app.platform.PlatformBackHandler
import app.bible.app.platform.reducedMotion
import app.bible.app.ui.LocalScriptureFont
import app.bible.core.Canon
import app.bible.core.Testament
import app.bible.core.VerseId
import app.bible.data.study.BookCount
import app.bible.data.study.Fold
import app.bible.data.study.KjvRendering
import app.bible.data.study.Language
import app.bible.data.study.StepEntry
import app.bible.data.study.StrongsEntry
import app.bible.data.study.StrongsNumber
import app.bible.data.study.WordTag
import app.bible.data.study.MorphInfo
import app.bible.data.study.OriginalWord
import kotlinx.coroutines.launch

/** A page inside the word-study sheet. The sheet keeps a stack of them, so related words can be followed and undone. */
sealed interface StudyPage {
    /** [verse] is the verse a word was opened from when there is no tapped KJV word (e.g. from the Hebrew or Greek). */
    data class Word(val num: Int?, val stepId: String?, val context: WordContext?, val verse: VerseId? = null) : StudyPage {
        val fromVerse: VerseId? get() = context?.verse ?: verse
    }
    data class Interlinear(val verse: VerseId, val highlight: Int?) : StudyPage
    data class Occurrences(val num: Int, val title: String, val language: Language, val book: Int?) : StudyPage
}

/** Everything the sheet shows for one Strong's number, loaded in one go so source switching is instant. */
data class WordData(
    val strongs: StrongsEntry?,
    val senses: List<StepEntry>,
    val renderings: List<KjvRendering>,
    val distribution: List<BookCount>,
    val derivedFrom: List<StrongsEntry>,
    val derivatives: List<StrongsEntry>,
) {
    val language: Language get() = strongs?.language ?: senses.firstOrNull()?.language ?: Language.Hebrew
    val lemma: String get() = strongs?.lemma ?: senses.firstOrNull()?.lemma ?: ""
    val gloss: String get() = senses.firstOrNull()?.gloss ?: strongs?.definition?.take(60) ?: ""
}

/** The Greek article and the Hebrew object marker: listed last when a phrase carries several numbers. */
private val MARKERS = setOf(StrongsNumber.of('G', 3588), 853)

/**
 * The word-study sheet. It opens compact (half height) with the word, its original form, transliteration,
 * Strong's number and a short definition, and expands by drag or "Explore" into the full study: definitions from
 * each source, KJV renderings, occurrences by book and related words. Closing it leaves the reader exactly where
 * they were, because it is an overlay and never moves the reading position.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordStudySheet(
    vm: StudyViewModel,
    request: WordStudyRequest,
    onDismiss: () -> Unit,
    onOpenVerse: (VerseId) -> Unit,
    onSearch: (String) -> Unit,
    onAttributions: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val stack = remember(request) {
        mutableStateListOf<StudyPage>(
            request.interlinear?.let { StudyPage.Interlinear(it, request.num) } ?: StudyPage.Word(
                num = request.num ?: request.context?.nums?.let { n -> n.firstOrNull { it !in MARKERS } ?: n.firstOrNull() },
                stepId = request.stepId,
                context = request.context,
            ),
        )
    }
    PlatformBackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    val reduce = remember { reducedMotion() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
            // Top row: back through the study history, and close.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (stack.size > 1) {
                    IconButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to the previous word")
                    }
                } else Spacer(Modifier.width(48.dp))
                Text(
                    "Word study",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close word study") }
            }
            val depth = stack.size
            AnimatedContent(
                targetState = stack.last() to depth,
                transitionSpec = {
                    if (reduce) ContentTransform(fadeIn(tween(0)), fadeOut(tween(0)))
                    else {
                        val forward = targetState.second >= initialState.second
                        (slideInHorizontally(tween(260)) { w -> if (forward) w / 5 else -w / 5 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(220)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(160)))
                    }
                },
                label = "study page",
            ) { (page, _) ->
                when (page) {
                    is StudyPage.Word -> WordPage(
                        vm, page,
                        onPick = { num -> stack[stack.lastIndex] = page.copy(num = num, stepId = null) },
                        onOpenWord = { num -> stack.add(StudyPage.Word(num, null, null)) },
                        onOccurrences = { num, title, lang, book -> stack.add(StudyPage.Occurrences(num, title, lang, book)) },
                        onExpand = { scope.launch { sheet.expand() } },
                        onSearch = onSearch,
                        onAttributions = onAttributions,
                        onInterlinear = { verse, num -> stack.add(StudyPage.Interlinear(verse, num)) },
                    )
                    is StudyPage.Interlinear -> InterlinearPage(vm, page.verse, page.highlight) { w ->
                        scope.launch {
                            val root = w.root
                            val num = root?.num?.takeIf { it > 0 }
                            val ctx = num?.let { vm.contextFor(page.verse, it) }
                            stack.add(StudyPage.Word(num, root?.stepId, ctx, page.verse))
                        }
                    }
                    is StudyPage.Occurrences -> OccurrencesPage(vm, page, onOpenVerse = onOpenVerse)
                }
            }
        }
    }
}

// ---- the word page ----------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordPage(
    vm: StudyViewModel,
    page: StudyPage.Word,
    onPick: (Int) -> Unit,
    onOpenWord: (Int) -> Unit,
    onOccurrences: (Int, String, Language, Int?) -> Unit,
    onExpand: () -> Unit,
    onSearch: (String) -> Unit,
    onAttributions: () -> Unit,
    onInterlinear: (VerseId, Int?) -> Unit,
) {
    val source by vm.source.collectAsStateWithLifecycle()
    // The original word in the verse it was opened from: its form, grammar and the STEP sense used there.
    var here by remember(page) { mutableStateOf<List<OriginalWord>>(emptyList()) }
    var hereSenses by remember(page) { mutableStateOf<List<String>>(emptyList()) }
    var hereMorph by remember(page) { mutableStateOf<Map<String, MorphInfo>>(emptyMap()) }
    val saved by vm.saved.collectAsStateWithLifecycle()
    var data by remember(page) { mutableStateOf<WordData?>(null) }
    var phrase by remember(page) { mutableStateOf<List<StrongsEntry>>(emptyList()) }
    var missing by remember(page) { mutableStateOf(false) }

    LaunchedEffect(page) {
        val s = vm.store()
        page.context?.nums?.takeIf { it.size > 1 }?.let { nums ->
            val found = s.strongs(nums)
            phrase = nums.distinct().mapNotNull { found[it] }.sortedBy { if (it.num in MARKERS) 1 else 0 }
        }
        val num = page.num ?: page.stepId?.let { s.step(it)?.num?.takeIf { n -> n > 0 } }
        if (num == null) {
            val step = page.stepId?.let { s.step(it) }
            if (step == null) { missing = true; return@LaunchedEffect }
            data = WordData(null, listOf(step), emptyList(), emptyList(), emptyList(), emptyList())
            return@LaunchedEffect
        }
        val entry = s.strongs(num)
        // A sense asked for on its own shows alone; from a verse, every sense shows with the one used there marked.
        val senses = s.stepSenses(num).let { all -> page.stepId?.takeIf { page.fromVerse == null }?.let { id -> all.filter { it.id == id }.ifEmpty { all } } ?: all }
        if (entry == null && senses.isEmpty()) { missing = true; return@LaunchedEffect }
        page.fromVerse?.let { v ->
            vm.original(v)?.let { o ->
                here = o.wordsWith(num).ifEmpty { page.stepId?.let { id -> o.words.filter { w -> w.parts.any { it.stepId == id } } } ?: emptyList() }
                hereSenses = o.sensesOf(num)
                hereMorph = vm.morph(here.flatMap { w -> w.parts.mapNotNull { it.grammar } })
            }
        }
        val d = WordData(entry, senses, s.renderings(num, 24), s.distribution(num), s.derivedFrom(num), s.derivatives(num))
        data = d
        vm.recordStudy(num, d.language, d.lemma, d.gloss, page.context?.verse)
    }

    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 40.dp),
    ) {
        page.context?.let { ctx -> VerseContext(ctx) }

        // The words a tapped phrase translates, when there is more than one.
        if (phrase.size > 1) {
            Text("This phrase translates ${phrase.size} words", style = MaterialTheme.typography.labelMedium, color = colors.secondary)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = ChipSpacing) {
                for (e in phrase) {
                    FilterChip(
                        selected = e.num == page.num,
                        onClick = { onPick(e.num) },
                        label = {
                            Text(
                                e.lemma + "  " + e.id + if (e.num in MARKERS) " · grammar word" else "",
                                style = originalStyle(e.language, 15.sp).copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                            )
                        },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (page.num == null && page.stepId == null) {
            UntaggedWord(page.context, onSearch)
            return@Column
        }
        if (missing) {
            Text(
                "This word isn't in the study pack. Its number may be outside Strong's numbering.",
                style = MaterialTheme.typography.bodyMedium, color = colors.secondary, modifier = Modifier.padding(vertical = 24.dp),
            )
            return@Column
        }
        val d = data
        if (d == null) { WordSkeleton(); return@Column }

        WordHeader(d, page)
        Spacer(Modifier.height(14.dp))

        // Actions: explore (expand the sheet), occurrences, save.
        val entryId = d.strongs?.id ?: d.senses.first().id
        val isSaved = saved.any { it.entry == entryId }
        Row(horizontalArrangement = ChipSpacing, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onExpand, shape = CircleShape) { Text("Explore word") }
            d.strongs?.takeIf { it.verses > 0 }?.let { e ->
                OutlinedButton(onClick = { onOccurrences(e.num, "${e.lemma} · ${e.id}", e.language, null) }, shape = CircleShape) { Text("Occurrences") }
            }
            IconButton(onClick = { vm.toggleSaved(entryId, source, d.language, d.lemma, d.gloss, page.context?.verse) }) {
                Icon(
                    if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (isSaved) "Remove from saved words" else "Save word",
                    tint = if (isSaved) colors.primary else colors.onSurface,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SourceSwitch(source, vm::setSource)
        Spacer(Modifier.height(6.dp))

        val reduce = remember { reducedMotion() }
        AnimatedContent(
            targetState = source,
            transitionSpec = { if (reduce) ContentTransform(fadeIn(tween(0)), fadeOut(tween(0))) else fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "source",
        ) { src ->
            Column {
                when (src) {
                    StudySource.Strongs -> StrongsView(d, onOpenWord)
                    StudySource.Step -> StepView(d, page.fromVerse != null, hereSenses)
                    StudySource.Compare -> CompareView(d)
                }
            }
        }

        page.fromVerse?.let { v -> InThisVerse(here, hereMorph, page.num) { onInterlinear(v, page.num) } }

        // In the KJV: how often, where and how it is translated (from the KJV's own word tags).
        d.strongs?.let { e -> KjvUsage(e, d, onOccurrences) }
        RelatedWords(d, onOpenWord)
        SourceFooter(d, onAttributions)
    }
}

/** The verse a word was tapped in, with the word picked out, so the study never loses its place in Scripture. */
@Composable
private fun VerseContext(ctx: WordContext) {
    val colors = MaterialTheme.colorScheme
    val text = remember(ctx) {
        buildAnnotatedString {
            append(ctx.text.substring(0, ctx.start))
            withStyle(SpanStyle(background = colors.primary.copy(alpha = 0.18f), fontWeight = FontWeight.SemiBold)) {
                append(ctx.text.substring(ctx.start, ctx.end))
            }
            append(ctx.text.substring(ctx.end))
        }
    }
    Text(
        "${ctx.verse.display().uppercase()} · KJV",
        style = TextStyle(fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium),
        color = colors.primary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text,
        style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 15.sp, lineHeight = 22.sp),
        color = colors.onSurface.copy(alpha = 0.8f),
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun WordHeader(d: WordData, page: StudyPage.Word) {
    val colors = MaterialTheme.colorScheme
    val lang = d.language
    page.context?.let {
        Text(it.word.uppercase(), style = TextStyle(fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold), color = colors.secondary)
        Spacer(Modifier.height(4.dp))
    }
    Text(
        d.lemma,
        style = originalStyle(lang, 40.sp, FontWeight.Normal),
        color = colors.onSurface,
        modifier = Modifier.semantics { contentDescription = "${lang.label} word ${d.strongs?.xlit ?: d.senses.firstOrNull()?.xlit ?: ""}" },
    )
    val xlit = d.strongs?.xlit ?: d.senses.firstOrNull()?.xlit
    val pron = d.strongs?.pron
    Row(verticalAlignment = Alignment.CenterVertically) {
        xlit?.let { Text(it, style = TextStyle(fontSize = 18.sp, fontStyle = FontStyle.Italic), color = colors.onSurface.copy(alpha = 0.85f)) }
        pron?.let {
            Spacer(Modifier.width(10.dp))
            Text("say “$it”", style = MaterialTheme.typography.bodySmall, color = colors.secondary)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = ChipSpacing, verticalAlignment = Alignment.CenterVertically) {
        LanguageBadge(lang)
        d.strongs?.let { Text("Strong's ${it.id}", style = MaterialTheme.typography.labelLarge, color = StudyColors.strongs()) }
        d.senses.singleOrNull()?.let { Text("STEP ${it.id}", style = MaterialTheme.typography.labelLarge, color = StudyColors.step()) }
    }
    // The short definition: STEP's gloss when there is one sense, else Strong's own definition.
    val short = d.senses.singleOrNull()?.gloss ?: d.strongs?.definition
    short?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 18.sp, lineHeight = 26.sp), color = colors.onSurface, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/** A word the KJV's tags don't link to Hebrew or Greek: say so, and offer an English search instead. */
@Composable
private fun UntaggedWord(ctx: WordContext?, onSearch: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val word = ctx?.word ?: return
    Text(word, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 32.sp), color = colors.onSurface)
    Spacer(Modifier.height(10.dp))
    Text(
        "The KJV's word tags don't link “$word” here to a Hebrew or Greek word. Words in italics were supplied by the " +
            "translators, and some small words are part of a phrase tagged elsewhere in the verse.",
        style = MaterialTheme.typography.bodyMedium, color = colors.secondary,
    )
    Spacer(Modifier.height(14.dp))
    OutlinedButton(onClick = { onSearch(word) }, shape = CircleShape) { Text("Search “$word” in English") }
    Spacer(Modifier.height(4.dp))
    Text("An English search, not an original-language match.", style = MaterialTheme.typography.bodySmall, color = colors.secondary)
}

@Composable
private fun WordSkeleton() {
    Column(Modifier.padding(vertical = 8.dp)) {
        SkeletonLine(0.45f, 44)
        SkeletonLine(0.3f, 18)
        Spacer(Modifier.height(8.dp))
        SkeletonLine(0.25f, 20)
        Spacer(Modifier.height(10.dp))
        SkeletonLine(0.9f); SkeletonLine(0.8f); SkeletonLine(0.6f)
    }
}

// ---- sources ----------------------------------------------------------------------------------------------------

@Composable
private fun StrongsView(d: WordData, onOpenWord: (Int) -> Unit) {
    val e = d.strongs
    if (e == null) {
        MissingSource("Strong's has no entry for this word. STEP Bible numbers it beyond Strong's original list (for example a prefix or a word only in other manuscripts).")
        return
    }
    StudyHeading("Strong's definition")
    StudyCard(accent = StudyColors.strongs()) {
        e.partOfSpeech?.let { hebrewPartOfSpeech(it) }?.let {
            Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
        }
        e.definition?.let { Text(it, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 16.sp, lineHeight = 24.sp)) }
        e.derivation?.let {
            Spacer(Modifier.height(10.dp))
            Text("Derivation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            LinkedStrongs(it, onOpenWord)
        }
    }
    e.kjvDefinition?.let {
        StudyHeading("KJV renderings, as Strong listed them")
        Text(it, style = MaterialTheme.typography.bodyMedium)
        Text(
            "[idiom] marks idiomatic renderings in Strong's list.".takeIf { _ -> "[idiom]" in it } ?: "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/** Text in which Strong's ids can be tapped to open that word. */
@Composable
private fun LinkedStrongs(text: String, onOpenWord: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val annotated = remember(text, colors.primary) {
        buildAnnotatedString {
            for ((part, num) in strongsLinks(text)) {
                if (num == null) append(part)
                else withLink(
                    LinkAnnotation.Clickable(
                        tag = part,
                        styles = TextLinkStyles(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)),
                    ) { onOpenWord(num) },
                ) { append(part) }
            }
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun StepView(d: WordData, fromVerse: Boolean, usedHere: List<String>) {
    val colors = MaterialTheme.colorScheme
    if (d.senses.isEmpty()) {
        MissingSource("STEP Bible's lexicon has no sense listed under this Strong's number.")
        return
    }
    if (d.senses.size > 1) {
        StudyHeading("STEP Bible: ${d.senses.size} senses")
        Text(
            "STEP Bible divides ${d.strongs?.id ?: "this number"} into ${d.senses.size} distinct senses or people." +
                when {
                    !fromVerse -> ""
                    usedHere.isNotEmpty() -> " The Hebrew or Greek text of this verse uses ${usedHere.joinToString(" and ")}, marked below."
                    else -> " The original text of this verse doesn't say which one is meant here."
                },
            style = MaterialTheme.typography.bodySmall, color = colors.secondary,
        )
        Spacer(Modifier.height(8.dp))
    } else StudyHeading("STEP Bible")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The sense this verse uses comes first.
        for (s in d.senses.sortedBy { if (it.id in usedHere) 0 else 1 }) StepSenseCard(s, usedHere = s.id in usedHere)
    }
    if (d.senses.any { it.language != Language.Greek }) {
        Spacer(Modifier.height(10.dp))
        Text(
            "STEP Bible's longer Hebrew definitions come from Online Bible's Abridged BDB, which needs separate permission, " +
                "so VerseUp shows STEP's own glosses for Hebrew.",
            style = MaterialTheme.typography.bodySmall, color = colors.secondary,
        )
    }
}

@Composable
private fun StepSenseCard(s: StepEntry, usedHere: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember(s.id) { mutableStateOf(false) }
    StudyCard(accent = StudyColors.step()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.id, style = MaterialTheme.typography.labelLarge, color = StudyColors.step())
            s.label?.let { Text("  ·  $it ${s.uStrong ?: ""}".trimEnd(), style = MaterialTheme.typography.labelMedium, color = colors.secondary) }
            if (usedHere) {
                Spacer(Modifier.weight(1f))
                Text(
                    "USED IN THIS VERSE",
                    style = TextStyle(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
                    color = StudyColors.step(),
                    modifier = Modifier.clip(CircleShape).background(StudyColors.step().copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(s.lemma, style = originalStyle(s.language, 24.sp))
            s.xlit?.let { Spacer(Modifier.width(10.dp)); Text(it, style = TextStyle(fontSize = 15.sp, fontStyle = FontStyle.Italic), color = colors.onSurface.copy(alpha = 0.8f)) }
        }
        s.gloss?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 18.sp, fontWeight = FontWeight.Medium))
        }
        stepGrammar(s.morph)?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
        }
        s.meaning?.let { meaning ->
            Spacer(Modifier.height(10.dp))
            Text("Brief lexicon (Abbott-Smith, corrected by STEP)", style = MaterialTheme.typography.labelMedium, color = colors.secondary)
            Spacer(Modifier.height(4.dp))
            Text(
                markup(meaning),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Read the full entry") }
        }
    }
}

/**
 * Both sources side by side (stacked on a phone). Common ground and differences are worked out from the two
 * records themselves; nothing is inferred beyond what they say, and missing information is labelled as missing.
 */
@Composable
private fun CompareView(d: WordData) {
    val colors = MaterialTheme.colorScheme
    val e = d.strongs
    StudyHeading("Strong's")
    StudyCard(accent = StudyColors.strongs()) {
        if (e == null) Text("No Strong's entry.", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
        else {
            Text("${e.lemma}  ·  ${e.xlit ?: ""}", style = originalStyle(e.language, 18.sp).copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
            Spacer(Modifier.height(4.dp))
            Text(e.definition ?: "No definition in this edition.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    StudyHeading("STEP Bible", color = StudyColors.step())
    StudyCard(accent = StudyColors.step()) {
        if (d.senses.isEmpty()) Text("No STEP Bible sense.", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
        for ((i, s) in d.senses.withIndex()) {
            if (i > 0) Spacer(Modifier.height(10.dp))
            Text("${s.id}  ${s.lemma}  ·  ${s.xlit ?: ""}", style = originalStyle(s.language, 16.sp).copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
            Text(s.gloss ?: "No gloss.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
    val (common, different) = remember(d) { compare(d) }
    StudyHeading("Common ground")
    if (common.isEmpty()) Text("The two records share no field that can be compared directly.", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
    for (c in common) Bullet(c)
    StudyHeading("Differences")
    if (different.isEmpty()) Text("No differences between the records.", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
    for (c in different) Bullet(c)
}

/** Facts both records support, and the ways they differ. */
internal fun compare(d: WordData): Pair<List<String>, List<String>> {
    val e = d.strongs ?: return emptyList<String>() to listOf("Only STEP Bible lists this word.")
    if (d.senses.isEmpty()) return emptyList<String>() to listOf("Only Strong's lists this word.")
    val common = ArrayList<String>(); val diff = ArrayList<String>()
    val sameLemma = d.senses.filter { Fold.fold(it.lemma) == Fold.fold(e.lemma) }
    if (sameLemma.isNotEmpty()) common += "Both give the headword ${e.lemma}."
    else diff += "The headword is spelled ${e.lemma} in Strong's and ${d.senses.first().lemma} in STEP Bible."
    val defWords = Regex("\\p{L}+").findAll(Fold.fold(listOfNotNull(e.definition, e.kjvDefinition).joinToString(" "))).map { it.value }.toSet()
    val shared = d.senses.flatMap { s -> Regex("\\p{L}+").findAll(Fold.fold(s.gloss ?: "")).map { it.value }.filter { it.length > 2 && it in defWords } }.distinct()
    if (shared.isNotEmpty()) common += "Both use the words “${shared.joinToString("”, “")}” to define it."
    if (e.xlit != null && d.senses.first().xlit != null) diff += "Transliteration conventions differ: Strong's writes ${e.xlit}, STEP Bible ${d.senses.first().xlit}."
    if (d.senses.size > 1) diff += "STEP Bible separates ${d.senses.size} senses (${d.senses.joinToString("; ") { "${it.id}: ${it.gloss ?: "?"}" }}); Strong's has one entry."
    if (e.kjvDefinition != null) diff += "Strong's lists how the KJV renders the word; STEP Bible gives a gloss instead."
    if (d.senses.any { it.meaning != null }) diff += "STEP Bible adds a fuller lexicon entry (Abbott-Smith)."
    return common to diff
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MissingSource(text: String) {
    Spacer(Modifier.height(16.dp))
    StudyCard { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
}

// ---- usage in the KJV -------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KjvUsage(e: StrongsEntry, d: WordData, onOccurrences: (Int, String, Language, Int?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (e.verses == 0) return
    StudyHeading("In the King James Version")
    val ot = d.distribution.filter { Canon.book(it.book).testament == Testament.OLD }.sumOf { it.verses }
    val nt = e.verses - ot
    Text(
        "${e.words} times in ${e.verses} verses" + when {
            ot > 0 && nt > 0 -> " · $ot Old Testament, $nt New Testament"
            else -> ""
        },
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        "Counted from the KJV's own word tags (CrossWire). Other editions and counting rules give other totals.",
        style = MaterialTheme.typography.bodySmall, color = colors.secondary,
    )
    if (d.renderings.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Translated as", style = MaterialTheme.typography.labelMedium, color = colors.secondary)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = ChipSpacing, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in d.renderings.take(12)) {
                Row(
                    Modifier.clip(CircleShape).background(colors.onSurface.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(6.dp))
                    Text("${r.count}", style = MaterialTheme.typography.labelSmall, color = colors.secondary)
                }
            }
        }
    }
    if (d.distribution.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        BookChart(d.distribution) { book -> onOccurrences(e.num, "${e.lemma} · ${e.id}", e.language, book) }
    }
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = { onOccurrences(e.num, "${e.lemma} · ${e.id}", e.language, null) }) { Text("See all ${e.verses} verses") }
}

/** Verses per book as quiet bars; the most frequent books first, the rest behind "All books". Tap a book for its verses. */
@Composable
private fun BookChart(distribution: List<BookCount>, onBook: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var all by remember { mutableStateOf(false) }
    val sorted = remember(distribution) { distribution.sortedByDescending { it.verses } }
    val max = sorted.first().verses.toFloat()
    val shown = if (all) distribution else sorted.take(6)
    Text("Where it is used", style = MaterialTheme.typography.labelMedium, color = colors.secondary)
    Spacer(Modifier.height(6.dp))
    for (b in shown) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Show verses in ${Canon.book(b.book).name}") { onBook(b.book) }
                .padding(vertical = 5.dp, horizontal = 4.dp)
                .semantics { contentDescription = "${Canon.book(b.book).name}: ${b.verses} verses" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Canon.book(b.book).name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(112.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxWidth(b.verses / max).height(8.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.75f)))
            }
            Text("${b.verses}", style = MaterialTheme.typography.labelMedium, color = colors.secondary, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
    if (distribution.size > 6) TextButton(onClick = { all = !all }) { Text(if (all) "Most frequent books" else "All ${distribution.size} books, in Bible order") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedWords(d: WordData, onOpenWord: (Int) -> Unit) {
    if (d.derivedFrom.isEmpty() && d.derivatives.isEmpty()) return
    StudyHeading("Related words")
    Text(
        "Links Strong's own derivations document. They show where a word comes from, not that the meanings are the same.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
    )
    if (d.derivedFrom.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("Comes from", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = ChipSpacing, verticalArrangement = Arrangement.spacedBy(8.dp)) { for (r in d.derivedFrom) WordChip(r) { onOpenWord(r.num) } }
    }
    if (d.derivatives.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("Words formed from it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = ChipSpacing, verticalArrangement = Arrangement.spacedBy(8.dp)) { for (r in d.derivatives) WordChip(r) { onOpenWord(r.num) } }
    }
}

@Composable
fun WordChip(e: StrongsEntry, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).background(StudyColors.language(e.language).copy(alpha = 0.10f)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(e.lemma, style = originalStyle(e.language, 17.sp))
        Spacer(Modifier.width(8.dp))
        Text(e.id, style = MaterialTheme.typography.labelMedium, color = colors.secondary)
    }
}

@Composable
private fun SourceFooter(d: WordData, onAttributions: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Spacer(Modifier.height(28.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Attributions and licences", onClick = onAttributions).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(
                d.strongs?.let { if (it.language == Language.Greek) "Strong's Greek Dictionary (1890), public domain" else "Strong's Hebrew Dictionary (1894), public domain" },
                d.senses.takeIf { it.isNotEmpty() }?.let { "STEP Bible (STEPBible.org, Tyndale House), CC BY 4.0" },
                d.strongs?.takeIf { it.verses > 0 }?.let { "KJV word tags: CrossWire Bible Society" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = colors.secondary,
        )
    }
}

// ---- occurrences ------------------------------------------------------------------------------------------------

/** Every KJV verse tagged with a number, a page at a time, with the tagged words picked out. Filter by book. */
@Composable
private fun OccurrencesPage(vm: StudyViewModel, page: StudyPage.Occurrences, onOpenVerse: (VerseId) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var book by remember(page) { mutableStateOf(page.book) }
    var books by remember(page) { mutableStateOf<List<BookCount>>(emptyList()) }
    val rows = remember(page, book) { mutableStateListOf<Triple<VerseId, VerseText?, List<WordTag>>>() }
    var done by remember(page, book) { mutableStateOf(false) }
    var loading by remember(page, book) { mutableStateOf(false) }
    val list = rememberLazyListState()

    LaunchedEffect(page) { books = vm.store().distribution(page.num) }
    suspend fun more() {
        if (done || loading) return
        loading = true
        val ids = vm.store().occurrences(page.num, rows.lastOrNull()?.first, 30, book)
        val texts = vm.verses(ids)
        val tags = vm.tags(ids)
        rows.addAll(ids.map { Triple(it, texts[it], tags[it] ?: emptyList()) })
        if (ids.size < 30) done = true
        loading = false
    }
    LaunchedEffect(page, book) { more() }
    LaunchedEffect(list, page, book) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= rows.size - 5) more()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(page.title, style = originalStyle(page.language, 22.sp).copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
            val total = books.filter { book == null || it.book == book }.sumOf { it.verses }
            Text(
                if (book == null) "$total verses in the KJV" else "$total verses in ${Canon.book(book!!).name}",
                style = MaterialTheme.typography.bodySmall, color = colors.secondary,
            )
            Spacer(Modifier.height(10.dp))
        }
        LazyRow(
            horizontalArrangement = ChipSpacing,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
        ) {
            item { FilterChip(selected = book == null, onClick = { book = null }, label = { Text("All books") }, shape = RoundedCornerShape(50)) }
            items(books, key = { it.book }) { b ->
                FilterChip(selected = book == b.book, onClick = { book = b.book }, label = { Text("${Canon.book(b.book).name} ${b.verses}") }, shape = RoundedCornerShape(50))
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 2000.dp)) {
            items(rows, key = { it.first.value }) { (id, verse, tags) ->
                OccurrenceRow(id, verse, tags, page.num) { onOpenVerse(id) }
            }
            if (!done) item { Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) { SkeletonLine(0.3f); SkeletonLine(0.9f); SkeletonLine(0.7f) } }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun OccurrenceRow(id: VerseId, verse: VerseText?, tags: List<WordTag>, num: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val text: AnnotatedString = remember(verse, tags, num, colors.primary) {
        val t = verse?.text ?: ""
        buildAnnotatedString {
            append(t)
            for (tag in tags) if (num in tag.nums && tag.end <= t.length) {
                addStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold), tag.start, tag.end)
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${id.display()}", onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text(id.display(), style = MaterialTheme.typography.labelLarge, color = colors.primary)
        Spacer(Modifier.height(2.dp))
        Text(text, style = TextStyle(fontFamily = LocalScriptureFont.current, fontSize = 15.sp, lineHeight = 22.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
