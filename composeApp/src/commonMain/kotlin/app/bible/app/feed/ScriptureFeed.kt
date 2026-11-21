package app.bible.app.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import app.bible.app.ui.cardBackground
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import app.bible.app.ui.topSafePadding
import app.bible.app.ui.NavIcons
import app.bible.app.ui.NavItem
import app.bible.app.ui.SettingsButton
import app.bible.app.ui.ReadAloudButton
import app.bible.app.ui.ReadAloudState
import app.bible.app.ui.rememberReadAloud
import app.bible.app.ui.GlassState
import app.bible.app.ui.glass
import app.bible.app.ui.glassSource
import app.bible.app.ui.rememberGlassState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.app.platform.SystemBarsEffect
import app.bible.core.Canon
import app.bible.core.VerseId
import app.bible.stories.Story

/** What a verse-actions sheet works on: the verses on screen around the one the reader held. */
data class VerseActionTarget(val verses: List<VerseId>, val initial: VerseId)

private enum class Sheet { Settings, Jump, Search, Saved }

/** Links between the Bible and Story Mode, shown in the reader's controls. */
class StoryLinks(
    /** The story that tells the verse being read, if any ("Part of: ..."). */
    val storyFor: (VerseId) -> Story?,
    val onOpenStory: (Story, VerseId) -> Unit,
    val onStories: () -> Unit,
    /** The story the reader came from via "Open in Bible", to go back to it. */
    val backTo: Story?,
    val onBackToStory: () -> Unit,
    /** Opens the Study page (concordance and word studies). */
    val onStudy: (() -> Unit)? = null,
)

/**
 * The reader: the Scripture feed or the classic chapter reader, plus the shared sheets (reading settings,
 * jump, search, saved items, verse actions). Both views share one reading position.
 */
@Composable
fun Reader(vm: FeedViewModel, translation: String, links: StoryLinks? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var action by remember { mutableStateOf<VerseActionTarget?>(null) }

    SystemBarsEffect(hideStatusBar = state.prefs.immersive)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.mode) {
            ReaderMode.Feed -> ScriptureFeed(
                vm = vm,
                translation = translation,
                onOpenSheet = { sheet = it },
                onVerseAction = { action = it },
                links = links,
            )
            ReaderMode.Classic -> ClassicReader(
                vm = vm,
                start = state.anchor,
                onClose = { vm.closeClassic() },
                onJump = { sheet = Sheet.Jump },
                onSettings = { sheet = Sheet.Settings },
                onVerseAction = { action = it },
            )
        }
    }

    when (sheet) {
        Sheet.Settings -> ReadingSettingsSheet(state.prefs, vm, onDismiss = { sheet = null })
        Sheet.Jump -> JumpSheet(current = state.anchor, onPick = { vm.open(it); sheet = null }, onDismiss = { sheet = null })
        Sheet.Search -> SearchSheet(vm, onPick = { vm.open(it); sheet = null }, onDismiss = { sheet = null })
        Sheet.Saved -> SavedSheet(vm, onPick = { vm.open(it); sheet = null }, onDismiss = { sheet = null })
        null -> Unit
    }
    action?.let { target ->
        VerseActionsSheet(vm, target, translation, onDismiss = { action = null })
    }
}

/**
 * The shared Scripture feed: a full-screen vertical pager of cards from Genesis to Revelation. Every reading
 * mode will drive this same composable through a different source.
 */
@Composable
private fun ScriptureFeed(
    vm: FeedViewModel,
    translation: String,
    onOpenSheet: (Sheet) -> Unit,
    onVerseAction: (VerseActionTarget) -> Unit,
    links: StoryLinks?,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val marksRevision by vm.marksRevision.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val glass = rememberGlassState()
    // The header's measured height (below the status bar), so card text starts a clear gap below it.
    var headerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val scrolls = remember { mutableStateMapOf<Int, ScrollState>() }

    Box(Modifier.fillMaxSize()) {
        if (state.loading && state.cards.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@Box
        }
        // Re-key on each rebuild so the pager jumps to the anchor card after density/font/jump changes.
        key(state.generation) {
            val pager = rememberPagerState(initialPage = state.initialPage) { state.cards.size }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { vm.onPageSettled(it) }
            }
            // Read aloud: the card on screen, then on to the next card.
            val readAloud = rememberReadAloud(
                page = pager.settledPage,
                text = { state.cards.getOrNull(pager.settledPage)?.let { c -> vm.textFor(c).joinToString(" ") { it.text } } },
                advance = {
                    val next = pager.settledPage + 1
                    if (next < state.cards.size) { pager.animateScrollToPage(next); true } else false
                },
            )
            // The background stays still and only the text swipes. It changes (crossfading) when the book does.
            // Background and cards together are what the glass header blurs.
            val book = state.cards.getOrNull(pager.currentPage)?.first?.book ?: 1
            Box(Modifier.fillMaxSize().glassSource(glass)) {
                Crossfade(targetState = book, label = "card background") { b ->
                    Box(Modifier.fillMaxSize().background(cardBackground(b, state.prefs.dark)))
                }
                VerticalPager(
                    state = pager,
                    flingBehavior = flickFlingBehavior(pager),
                    beyondViewportPageCount = 1, // compose the neighbours so swipes never show a blank card
                    key = { state.cards[it].key },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val card = state.cards[page]
                    val scroll = rememberScrollState()
                    DisposableEffect(page, scroll) {
                        scrolls[page] = scroll
                        onDispose { scrolls.remove(page) }
                    }
                    ScriptureCard(
                        drawBackground = false,
                        scroll = scroll,
                        topInset = if (headerHeight > 0.dp) headerHeight + HeaderGap else 72.dp,
                        card = card,
                        prefs = state.prefs,
                        translation = translation,
                        vm = vm,
                        marksRevision = marksRevision,
                        onTap = { chromeVisible = !chromeVisible },
                        onVerseAction = { verse ->
                            onVerseAction(VerseActionTarget(card.range.verses().toList(), verse))
                        },
                    )
                }
            }
            val page = pager.currentPage
            val current = state.cards.getOrNull(page)
            // The glass shows only while something is under the header: cards moving, or a long card's text
            // scrolled up. At rest the header is clear.
            val textUnder = (scrolls[page]?.value ?: 0) > 0
            val glassVisibility by animateFloatAsState(if (pager.isScrollInProgress || textUnder) 1f else 0f, label = "glass")
            AnimatedVisibility(chromeVisible && current != null, enter = fadeIn(), exit = fadeOut()) {
                val bookRange = current?.let { state.bookCards[it.first.book] }
                val progress = if (bookRange == null || bookRange.last == bookRange.first) 1f
                else (page - bookRange.first) / (bookRange.last - bookRange.first).toFloat()
                val story = remember(current?.key, links) { current?.let { links?.storyFor?.invoke(it.first) } }
                Chrome(
                    glass = glass,
                    glassVisibility = glassVisibility,
                    onHeight = { headerHeight = with(density) { it.toDp() } },
                    title = current?.let { "${Canon.book(it.first.book).name} ${it.first.chapter}" } ?: "",
                    progress = progress,
                    onOpenSheet = onOpenSheet,
                    onClassic = { vm.setMode(ReaderMode.Classic) },
                    links = links,
                    story = story,
                    onOpenStory = { s -> current?.let { links?.onOpenStory?.invoke(s, it.first) } },
                    readAloud = readAloud.takeIf { state.prefs.readAloud },
                )
            }
        }
    }
}

/**
 * A flick turns the card: a short, slow drag past 15% of the screen also counts (the default needs half the
 * screen), any fling moves exactly one card, and the settle animation is quick and does not bounce.
 */
@Composable
internal fun flickFlingBehavior(pager: PagerState) = PagerDefaults.flingBehavior(
    state = pager,
    pagerSnapDistance = PagerSnapDistance.atMost(1),
    snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
    snapPositionalThreshold = 0.15f,
)

@Composable
private fun Chrome(
    glass: GlassState,
    glassVisibility: Float,
    onHeight: (Int) -> Unit,
    title: String,
    progress: Float,
    onOpenSheet: (Sheet) -> Unit,
    onClassic: () -> Unit,
    links: StoryLinks?,
    story: Story?,
    onOpenStory: (Story) -> Unit,
    readAloud: ReadAloudState?,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        // The header is ambient glass while cards scroll under it: the card shows through, blurred, and the
        // bottom of the glass fades into the card (the extra bottom padding is room for that fade).
        Column(
            Modifier.fillMaxWidth().glass(glass, glassVisibility).topSafePadding()
                .onSizeChanged { onHeight(it.height) }
                .padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The book and chapter being read, bold and prominent; tapping it opens the book list.
                TextButton(onClick = { onOpenSheet(Sheet.Jump) }) {
                    Text(
                        "$title ▾",
                        color = colors.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    readAloud?.let { ReadAloudButton(it) }
                    TextButton(onClick = { onOpenSheet(Sheet.Settings) }) { Text("Aa", color = colors.onBackground) }
                    SettingsButton()
                }
            }
            // Story -> Bible: a way back to the story. Bible -> Story: the story this passage belongs to.
            val back = links?.backTo
            when {
                back != null && links != null -> StoryPill("‹ Back to ${back.title}", links.onBackToStory)
                story != null -> StoryPill("Part of: ${story.title} ›") { onOpenStory(story) }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 8.dp),
        ) {
            // Quiet reading progress through the book.
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(2.dp),
                color = colors.primary.copy(alpha = 0.7f),
                trackColor = colors.onBackground.copy(alpha = 0.12f),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                NavItem(NavIcons.Search, "Search", onClick = { onOpenSheet(Sheet.Search) })
                NavItem(NavIcons.Saved, "Saved", onClick = { onOpenSheet(Sheet.Saved) })
                if (links != null) NavItem(NavIcons.Stories, "Stories", onClick = links.onStories)
                links?.onStudy?.let { NavItem(NavIcons.Study, "Study", onClick = it) }
                NavItem(NavIcons.Chapter, "Chapter", onClick = onClassic)
            }
            Text(
                "Hold a verse to highlight, note, copy or study a word",
                fontSize = 11.sp,
                color = colors.secondary.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}


/** Space between the bottom of a reader's header and the first line of text. */
internal val HeaderGap = 12.dp

/** The story link in the reader's header: no border, just a slightly darker rounded background. */
@Composable
private fun StoryPill(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        color = colors.onBackground,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.onBackground.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
