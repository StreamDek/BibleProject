package app.bible.app.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.core.Canon

/**
 * The shared Scripture feed: a full-screen vertical pager of cards. Every reading mode will drive this same
 * composable through a different source; this first version reads one book at a time (free reading).
 */
@Composable
fun ScriptureFeed(vm: FeedViewModel, translation: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (state.loading && state.cards.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@Box
        }
        // Re-key on each rebuild so the pager jumps to the anchor card after density/font/book changes.
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
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val card = state.cards[page]
                ScriptureCard(
                    card = card,
                    prefs = state.prefs,
                    translation = translation,
                    loadText = vm::textFor,
                    loadHeading = vm::heading,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { chromeVisible = !chromeVisible },
                )
            }
            val current = state.cards.getOrNull(pager.currentPage)
            AnimatedVisibility(chromeVisible && current != null, enter = fadeIn(), exit = fadeOut()) {
                Chrome(
                    title = current?.let { "${Canon.book(it.first.book).name} ${it.first.chapter}" } ?: "",
                    progress = if (state.cards.size <= 1) 1f else pager.currentPage / (state.cards.size - 1f),
                    onOpenJump = { sheet = Sheet.Jump },
                    onOpenSettings = { sheet = Sheet.Settings },
                )
            }
        }
    }

    when (sheet) {
        Sheet.Settings -> ReadingSettingsSheet(state.prefs, vm, onDismiss = { sheet = null })
        Sheet.Jump -> JumpSheet(onPick = { vm.open(it); sheet = null }, onDismiss = { sheet = null })
        null -> Unit
    }
}

private enum class Sheet { Settings, Jump }

/**
 * A flick turns the card: a short, slow drag past 15% of the screen also counts (the default needs half the
 * screen), any fling moves exactly one card, and the settle animation is quick and does not bounce.
 */
@Composable
private fun flickFlingBehavior(pager: PagerState) = PagerDefaults.flingBehavior(
    state = pager,
    pagerSnapDistance = PagerSnapDistance.atMost(1),
    snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
    snapPositionalThreshold = 0.15f,
)

@Composable
private fun Chrome(title: String, progress: Float, onOpenJump: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenJump) { Text(title, color = colors.onBackground) }
            TextButton(onClick = onOpenSettings) { Text("Aa", color = colors.onBackground) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp)) {
            // Quiet reading progress through the book.
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.primary.copy(alpha = 0.7f),
                trackColor = Color.Transparent.copy(alpha = 0f).compositeOverDefault(colors.onBackground.copy(alpha = 0.12f)),
            )
        }
    }
}

private fun Color.compositeOverDefault(fallback: Color): Color = if (alpha == 0f) fallback else this
