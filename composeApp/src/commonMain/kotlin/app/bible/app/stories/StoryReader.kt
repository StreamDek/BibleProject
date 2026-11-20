package app.bible.app.stories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.app.feed.FeedCard
import app.bible.app.feed.FeedViewModel
import app.bible.app.feed.ReadingSettingsSheet
import app.bible.app.feed.ScriptureCard
import app.bible.app.feed.VerseActionTarget
import app.bible.app.feed.VerseActionsSheet
import app.bible.app.feed.flickFlingBehavior
import app.bible.app.platform.SystemBarsEffect
import app.bible.app.ui.topSafePadding
import app.bible.core.Canon
import app.bible.core.VerseId
import app.bible.stories.EditorialBlock
import app.bible.stories.Story
import app.bible.stories.StoryPage
import app.bible.stories.StoryPages

/**
 * Story Mode reader: the same vertical flick feed and Scripture cards as free reading, driven by a story.
 * Introductions and summaries sit on their own cards, labelled as not Scripture. When a step has parallel
 * accounts, tabs at the top switch between them; each account keeps its own wording.
 */
@Composable
fun StoryReader(
    vm: StoriesViewModel,
    feed: FeedViewModel,
    translation: String,
    onBack: () -> Unit,
    onOpenInBible: (VerseId) -> Unit,
    onOpenStory: (Story) -> Unit,
) {
    val state by vm.reader.collectAsStateWithLifecycle()
    val feedState by feed.state.collectAsStateWithLifecycle()
    val marksRevision by feed.marksRevision.collectAsStateWithLifecycle()
    val prefs = feedState.prefs
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<VerseActionTarget?>(null) }

    SystemBarsEffect(hideStatusBar = prefs.immersive)
    LaunchedEffect(prefs) { vm.setPrefs(prefs) }

    val story = state.story
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (story == null || state.loading || state.pages.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@Box
        }
        key(state.generation) {
            val pager = rememberPagerState(initialPage = state.page) { state.pages.size }
            LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { vm.onPageSettled(it) } }
            VerticalPager(
                state = pager,
                flingBehavior = flickFlingBehavior(pager),
                beyondViewportPageCount = 1,
                key = { state.pages[it].key },
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val toggle = { chromeVisible = !chromeVisible }
                when (val page = state.pages[index]) {
                    is StoryPage.Intro -> IntroCard(page.story, state.pages, onTap = toggle)
                    is StoryPage.Scripture -> {
                        val step = story.steps[page.step]
                        ScriptureCard(
                            card = FeedCard(page.first.value, page.first, page.last, page.continues),
                            prefs = prefs,
                            translation = translation,
                            vm = feed,
                            marksRevision = marksRevision,
                            onTap = toggle,
                            onVerseAction = { verse -> action = VerseActionTarget(page.range.verses().toList(), verse) },
                            label = if (page.stepStart && story.steps.size > 1) {
                                "${page.step + 1} of ${story.steps.size} · ${step.title ?: ""}"
                            } else null,
                            onReferenceTap = { onOpenInBible(page.first) },
                            // Under the controls, the story's title and any account tabs.
                            topInset = if (step.accounts.size > 1) 152.dp else 104.dp,
                        )
                    }
                    is StoryPage.Summary -> InterludeCard("Summary", page.story.summary!!, onTap = toggle)
                    is StoryPage.End -> EndCard(
                        story = page.story,
                        next = vm.home.value.library?.next(page.story.slug),
                        onRelated = onOpenInBible,
                        onNext = onOpenStory,
                        onAgain = { vm.open(page.story.slug, restart = true) },
                        onBack = onBack,
                    )
                }
            }

            val current = state.pages.getOrNull(pager.currentPage)
            AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                StoryChrome(
                    story = story,
                    page = current,
                    progress = if (state.pages.size <= 1) 1f else pager.currentPage / (state.pages.size - 1).toFloat(),
                    choices = state.choices,
                    onBack = onBack,
                    onSettings = { settings = true },
                    onChoose = vm::chooseAccount,
                    onOpenInBible = onOpenInBible,
                )
            }
        }
    }

    if (settings) ReadingSettingsSheet(prefs, feed, onDismiss = { settings = false })
    action?.let { VerseActionsSheet(feed, it, translation, onDismiss = { action = null }) }
}

@Composable
private fun StoryChrome(
    story: Story,
    page: StoryPage?,
    progress: Float,
    choices: Map<Int, Int>,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onChoose: (step: Int, account: Int) -> Unit,
    onOpenInBible: (VerseId) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().topSafePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // On the intro card the controls sit on the story's art: white with a soft shadow, over a dark scrim.
            val overArt = page is StoryPage.Intro
            val navColor = if (overArt) Color.White else colors.onBackground
            val navShadow = if (overArt) Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 6f) else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("‹ Stories", color = navColor, fontWeight = FontWeight.Medium, style = TextStyle(shadow = navShadow))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSettings) {
                    Text("Aa", color = navColor, fontWeight = FontWeight.Medium, style = TextStyle(shadow = navShadow))
                }
            }
            // The story's title as the page heading, under the way back to the stories. The intro and end cards
            // already show it large, so it is left off there.
            if (page is StoryPage.Scripture || page is StoryPage.Summary) Text(
                story.title,
                color = colors.onBackground,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            )
            // Parallel accounts: one tab per Gospel (or book). Switching keeps the step.
            val scripture = page as? StoryPage.Scripture
            val step = scripture?.let { story.steps[it.step] }
            if (scripture != null && step != null && step.accounts.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val selected = choices[scripture.step] ?: 0
                    step.accounts.forEachIndexed { i, account ->
                        FilterChip(
                            selected = i == selected,
                            onClick = { onChoose(scripture.step, i) },
                            label = { Text(account.label) },
                        )
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 8.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(2.dp),
                color = colors.primary.copy(alpha = 0.7f),
                trackColor = colors.onBackground.copy(alpha = 0.12f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val scripture = page as? StoryPage.Scripture
                if (scripture != null) {
                    TextButton(onClick = { onOpenInBible(scripture.first) }) {
                        Text("Open in Bible · ${Canon.book(scripture.first.book).name} ${scripture.first.chapter}", color = colors.onBackground)
                    }
                } else {
                    Text(
                        story.sourcesLine(3),
                        fontSize = 12.sp,
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}

/** Label used on every card that is not Scripture. */
@Composable
private fun EditorialLabel(kind: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = colors.primary.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
            Text(
                kind.uppercase(),
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.padding(start = 8.dp))
        Text("Written by our editors · not Scripture", fontSize = 11.sp, color = colors.secondary)
    }
}

@Composable
private fun IntroCard(story: Story, pages: List<StoryPage>, onTap: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val cards = pages.count { it is StoryPage.Scripture }
    val minutes = remember(story) {
        // Without the text loaded, estimate from the number of verses (about 130 characters each in the KJV).
        val verses = story.steps.sumOf { s -> s.primary.passage.ranges.sumOf { it.verseCount } }
        StoryPages.minutes(verses * 130, listOfNotNull(story.intro, story.summary).size)
    }
    Box(Modifier.fillMaxSize().background(colors.background).pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
        StoryArt(story, Modifier.fillMaxWidth().fillMaxHeight(0.5f), alignment = Alignment.TopCenter)
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to colors.background)),
        )
        // A dark scrim at the top so the controls stay legible over bright art (sky, light, clouds).
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.22f)
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent)),
        )
        Column(
            Modifier.fillMaxSize().topSafePadding().navigationBarsPadding()
                .padding(horizontal = 28.dp).padding(top = 140.dp, bottom = 72.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(story.period.title.uppercase(), fontSize = 12.sp, letterSpacing = 1.6.sp, color = colors.primary)
            Spacer(Modifier.height(8.dp))
            Text(story.title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Spacer(Modifier.height(10.dp))
            Text(story.accounts.map { it.passage.display() }.distinct().joinToString(" · "), fontSize = 14.sp, color = colors.secondary)
            Spacer(Modifier.height(4.dp))
            val parts = buildList {
                if (story.steps.size > 1) add("${story.steps.size} parts")
                val accounts = story.steps.maxOf { it.accounts.size }
                if (accounts > 1) add("$accounts accounts")
                add("$cards cards")
                add("about $minutes min")
            }
            Text(parts.joinToString(" · "), fontSize = 13.sp, color = colors.secondary)
            if (story.arranged) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Arranged in order by our editors from " +
                        story.accounts.map { it.label }.distinct().joinToString(" and "),
                    fontSize = 13.sp,
                    color = colors.secondary,
                )
            }
            publishedArt(story)?.let { art ->
                // Imagery policy §5: say what the picture is.
                Spacer(Modifier.height(8.dp))
                Text(
                    "Image: AI-generated illustration · ${art.depiction.label} · Reviewed by our editors",
                    fontSize = 12.sp,
                    color = colors.secondary,
                )
            }
            story.intro?.let { intro ->
                Spacer(Modifier.height(24.dp))
                EditorialBody("Introduction", intro)
            }
            Spacer(Modifier.height(24.dp))
            Text("Swipe up to begin", fontSize = 13.sp, color = colors.onBackground.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun EditorialBody(kind: String, block: EditorialBlock) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            EditorialLabel(kind)
            Spacer(Modifier.height(12.dp))
            // Sans-serif on purpose: Scripture alone is set in the serif.
            Text(block.text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        }
    }
}

@Composable
private fun InterludeCard(kind: String, block: EditorialBlock, onTap: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.widthIn(max = 640.dp).padding(horizontal = 24.dp).topSafePadding().navigationBarsPadding()) {
            EditorialBody(kind, block)
        }
    }
}

@Composable
private fun EndCard(
    story: Story,
    next: Story?,
    onRelated: (VerseId) -> Unit,
    onNext: (Story) -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).topSafePadding().navigationBarsPadding()
            .padding(horizontal = 28.dp).padding(top = 96.dp, bottom = 72.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("YOU'VE FINISHED", fontSize = 12.sp, letterSpacing = 1.6.sp, color = colors.primary)
        Spacer(Modifier.height(8.dp))
        Text(story.title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
        if (story.related.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Related passages", style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
            for (r in story.related) {
                Column(
                    Modifier.fillMaxWidth().clickable { onRelated(r.passage.first) }.padding(vertical = 8.dp),
                ) {
                    Text("${r.passage.display()}  ›", color = colors.primary, fontWeight = FontWeight.Medium)
                    Text(
                        listOfNotNull(r.relation.label, r.note).joinToString(" · "),
                        fontSize = 13.sp,
                        color = colors.secondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        next?.let {
            OutlinedButton(onClick = { onNext(it) }, modifier = Modifier.fillMaxWidth()) {
                Text("Next: ${it.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onAgain) { Text("Read again") }
            TextButton(onClick = onBack) { Text("Back to stories") }
        }
    }
}
