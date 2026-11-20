package app.bible.app.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import app.bible.app.ui.GlassState
import app.bible.app.ui.glass
import app.bible.app.ui.glassSource
import app.bible.app.ui.rememberGlassState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AssistChip
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
            VerticalPager(
                state = pager,
                flingBehavior = flickFlingBehavior(pager),
                beyondViewportPageCount = 1, // compose the neighbours so swipes never show a blank card
                key = { state.cards[it].key },
                modifier = Modifier.fillMaxSize().glassSource(glass),
            ) { page ->
                val card = state.cards[page]
                ScriptureCard(
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
            val page = pager.currentPage
            val current = state.cards.getOrNull(page)
            AnimatedVisibility(chromeVisible && current != null, enter = fadeIn(), exit = fadeOut()) {
                val bookRange = current?.let { state.bookCards[it.first.book] }
                val progress = if (bookRange == null || bookRange.last == bookRange.first) 1f
                else (page - bookRange.first) / (bookRange.last - bookRange.first).toFloat()
                val story = remember(current?.key, links) { current?.let { links?.storyFor?.invoke(it.first) } }
                Chrome(
                    glass = glass,
                    title = current?.let { "${Canon.book(it.first.book).name} ${it.first.chapter}" } ?: "",
                    progress = progress,
                    onOpenSheet = onOpenSheet,
                    onClassic = { vm.setMode(ReaderMode.Classic) },
                    links = links,
                    story = story,
                    onOpenStory = { s -> current?.let { links?.onOpenStory?.invoke(s, it.first) } },
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
    title: String,
    progress: Float,
    onOpenSheet: (Sheet) -> Unit,
    onClassic: () -> Unit,
    links: StoryLinks?,
    story: Story?,
    onOpenStory: (Story) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        // The header is ambient glass: the card behind shows through, blurred.
        Column(Modifier.fillMaxWidth().glass(glass).topSafePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                TextButton(onClick = { onOpenSheet(Sheet.Settings) }) { Text("Aa", color = colors.onBackground) }
            }
            // Story -> Bible: a way back to the story. Bible -> Story: the story this passage belongs to.
            val back = links?.backTo
            when {
                back != null && links != null -> AssistChip(
                    onClick = links.onBackToStory,
                    label = { Text("‹ Back to ${back.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                story != null -> AssistChip(
                    onClick = { onOpenStory(story) },
                    label = { Text("Part of: ${story.title} ›", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { onOpenSheet(Sheet.Search) }) { Text("Search", color = colors.onBackground) }
                TextButton(onClick = { onOpenSheet(Sheet.Saved) }) { Text("Saved", color = colors.onBackground) }
                if (links != null) TextButton(onClick = links.onStories) { Text("Stories", color = colors.onBackground) }
                TextButton(onClick = onClassic) { Text("Chapter", color = colors.onBackground) }
            }
            Text(
                "Hold a verse to highlight, bookmark, note or copy",
                fontSize = 11.sp,
                color = colors.secondary.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

