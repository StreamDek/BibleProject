package app.bible.app.stories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.app.platform.PlatformBackHandler
import app.bible.app.ui.topSafePadding
import app.bible.app.ui.SettingsButton
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import app.bible.app.ui.glass
import app.bible.app.ui.glassSource
import app.bible.app.ui.rememberGlassState
import app.bible.stories.Period
import app.bible.stories.Story

/**
 * Story discovery (proposal §4.2): Continue, shelves, offline search, and "Journey through the Bible", every story
 * in order. Everything works offline from the bundled catalogue.
 */
@Composable
fun StoriesScreen(vm: StoriesViewModel, onOpen: (Story) -> Unit, onBible: () -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var journey by rememberSaveable { mutableStateOf(false) }
    // A shelf opened in full ("View all"), by its title.
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    PlatformBackHandler(enabled = journey) { journey = false }
    PlatformBackHandler(enabled = section != null && !journey) { section = null }

    val glass = rememberGlassState()
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    // One scroll state per list, so the header knows when content has scrolled up under it.
    val homeList = rememberLazyListState()
    val searchList = rememberLazyListState()
    val journeyList = rememberLazyListState()
    val sectionGrid = rememberLazyGridState()
    val openShelf = remember(section, home.library) { section?.let { t -> home.library?.shelves()?.firstOrNull { it.title == t } } }
    val activeList = when {
        journey -> journeyList
        query.isNotBlank() -> searchList
        else -> homeList
    }
    val scrolledUnder = if (openShelf != null && !journey) sectionGrid.canScrollBackward else activeList.canScrollBackward
    val glassVisibility by animateFloatAsState(if (scrolledUnder) 1f else 0f, label = "glass")
    // Journey: the period each list row belongs to, so the current period's heading can stay pinned under the
    // page title while its stories scroll past.
    val journeyPeriods = remember(home.library) {
        buildList {
            var period: Period? = null
            for (s in home.library?.stories.orEmpty()) {
                if (s.period != period) { period = s.period; add(s.period.title) }
                add(s.period.title)
            }
        }
    }
    val pinnedPeriod by remember(journeyPeriods) {
        derivedStateOf {
            val i = journeyList.firstVisibleItemIndex
            // Pinned once the list has moved at all: the in-list heading is then under the header.
            if (i == 0 && journeyList.firstVisibleItemScrollOffset == 0) null else journeyPeriods.getOrNull(i)
        }
    }
    var pinnedHeight by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        val library = home.library
        // The lists start below the glass header and scroll under it.
        // The pinned period heading is not counted, so the list doesn't jump when it appears.
        val bottom = PaddingValues(top = with(density) { (headerHeight - pinnedHeight).coerceAtLeast(0).toDp() }, bottom = 32.dp)
        val listModifier = Modifier.fillMaxSize().glassSource(glass).navigationBarsPadding()
        if (library == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            when {
                journey -> LazyColumn(listModifier, state = journeyList, contentPadding = bottom) {
                    var period: Period? = null
                    for (s in library.stories) {
                        if (s.period != period) {
                            period = s.period
                            val title = s.period.title
                            item(key = "h-$title") { PeriodHeader(title) }
                        }
                        item(key = s.slug) { StoryRow(s, s.slug in home.finished, onUnmark = { vm.unmarkFinished(s) }) { onOpen(s) } }
                    }
                }
                openShelf != null -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = listModifier,
                    state = sectionGrid,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = bottom.calculateTopPadding() + 8.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(openShelf.stories, key = { it.slug }) { s ->
                        StoryTile(
                            s, s.slug in home.finished, onUnmark = { vm.unmarkFinished(s) },
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                        ) { onOpen(s) }
                    }
                }
                query.isNotBlank() -> {
                    val results = remember(query, library) { library.search(query) }
                    LazyColumn(listModifier, state = searchList, contentPadding = bottom) {
                        if (results.isEmpty()) item {
                            Text(
                                "No stories found. Try a name, a place, or a reference like \"Luke 2\".",
                                color = colors.secondary,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                        items(results, key = { it.slug }) { s -> StoryRow(s, s.slug in home.finished, onUnmark = { vm.unmarkFinished(s) }) { onOpen(s) } }
                    }
                }
                else -> LazyColumn(listModifier, state = homeList, contentPadding = bottom) {
                    home.last?.let { last ->
                        if (last.slug !in home.finished) item(key = "continue") { ContinueCard(last) { onOpen(last) } }
                    }
                    item(key = "journey") {
                        JourneyCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) { journey = true }
                    }
                    for (shelf in library.shelves()) {
                        item(key = "shelf-${shelf.title}") {
                            Column {
                                SectionHeader("${shelf.title}  ·  ${shelf.stories.size}", onViewAll = { section = shelf.title })
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(shelf.stories, key = { it.slug }) { s -> StoryTile(s, s.slug in home.finished, onUnmark = { vm.unmarkFinished(s) }) { onOpen(s) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Ambient glass header, shown once the list scrolls up under it; its bottom fades into the list (the
        // bottom padding is room for the fade).
        Column(
            Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }.glass(glass, glassVisibility)
                .topSafePadding().padding(bottom = 12.dp),
        ) {
            if (journey) {
                // Journey: the way back at the top left, then the page's title below it as a heading.
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { journey = false }) { Text("‹ Stories", color = colors.onBackground) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onBible) { Text("Bible ›", color = colors.onBackground) }
                    SettingsButton()
                }
                Text(
                    "Journey through the Bible",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                )
                // The current period's heading, held just below the title while you scroll through its stories.
                var lastPinned by remember { mutableStateOf("") }
                pinnedPeriod?.let { lastPinned = it }
                AnimatedVisibility(
                    visible = pinnedPeriod != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                    modifier = Modifier.onSizeChanged { pinnedHeight = it.height },
                ) {
                    AnimatedContent(
                        targetState = lastPinned,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "pinned period",
                    ) { title ->
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
                        )
                    }
                }
            } else if (openShelf != null) {
                // A shelf in full: the way back at the top left, then the shelf's name as the page heading.
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { section = null }) { Text("‹ Stories", color = colors.onBackground) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onBible) { Text("Bible ›", color = colors.onBackground) }
                    SettingsButton()
                }
                Text(
                    openShelf.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                )
                Text(
                    "${openShelf.stories.size} stories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Stories",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onBible) { Text("Bible ›", color = colors.onBackground) }
                    SettingsButton()
                }
            }
            if (library != null && !journey && openShelf == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search stories, people or a reference") },
                    singleLine = true,
                    shape = CircleShape, // search fields are pill-shaped: no sharp corners
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * The way into "Journey through the Bible": a tinted card with an icon and a chevron, so it reads clearly as
 * something to tap.
 */
@Composable
private fun JourneyCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(colors.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Route, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Journey through the Bible",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Text(
                "Every story in order, from Creation to Revelation",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onBackground.copy(alpha = 0.7f),
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.primary)
    }
}

/** A period heading in "Journey through the Bible": larger and bold, so each era stands out. */
@Composable
private fun PeriodHeader(title: String) {
    val colors = MaterialTheme.colorScheme
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = colors.onBackground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionHeader(title: String, onViewAll: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        // Opens the whole shelf as a page, instead of swiping through a long row.
        if (onViewAll != null) TextButton(onClick = onViewAll) {
            Text("View all", color = colors.primary, style = MaterialTheme.typography.labelLarge)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ContinueCard(story: Story, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(120.dp)
            .clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        StoryArt(story, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))))
        Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) {
            Text("CONTINUE", fontSize = 11.sp, letterSpacing = 1.6.sp, color = Color.White.copy(alpha = 0.8f))
            Text(story.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = TitleShadow))
            Text(story.sourcesLine(), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun StoryTile(
    story: Story,
    finished: Boolean,
    onUnmark: () -> Unit,
    modifier: Modifier = Modifier.width(148.dp).height(222.dp),
    onClick: () -> Unit,
) {
    // Portrait posters at 2:3, a little taller than before. The art is anchored towards the top to keep faces in.
    Box(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        StoryArt(story, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)),
            ),
        )
        if (finished) {
            FinishedBadge(Modifier.align(Alignment.TopEnd).padding(4.dp), size = 26.dp, onClick = onUnmark)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                story.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp, style = TextStyle(shadow = TitleShadow),
            )
            Spacer(Modifier.height(4.dp))
            Text(tileSubtitle(story), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun tileSubtitle(story: Story): String {
    val accounts = story.steps.maxOf { it.accounts.size }
    return when {
        story.steps.size > 1 -> "${story.steps.size} parts"
        accounts > 1 -> "$accounts accounts"
        else -> story.steps.first().primary.passage.display()
    }
}

@Composable
private fun StoryRow(story: Story, finished: Boolean, onUnmark: () -> Unit, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryArt(story, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(story.title, color = colors.onBackground, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(story.sourcesLine(), color = colors.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (finished) FinishedBadge(size = 24.dp, onClick = onUnmark)
    }
}

/** Green finished-story badge: a white check in a green circle, ringed and shadowed so it reads on any art. */
@Composable
private fun FinishedBadge(modifier: Modifier = Modifier, size: Dp = 24.dp, onClick: () -> Unit) {
    // Tapping the check marks the story as not read. The tap area is padded beyond the badge so it is easy to hit.
    Box(
        modifier
            .clip(CircleShape)
            .clickable(onClickLabel = "Mark as not read", onClick = onClick)
            .padding(4.dp)
            .size(size)
            .shadow(3.dp, CircleShape)
            .background(FinishedGreen, CircleShape)
            .border(1.5.dp, Color.White, CircleShape)
            .semantics { contentDescription = "Finished" },
    ) {
        Canvas(Modifier.fillMaxSize().padding(size * 0.26f)) {
            val w = this.size.width
            val h = this.size.height
            val check = Path().apply {
                moveTo(w * 0.04f, h * 0.54f)
                lineTo(w * 0.38f, h * 0.86f)
                lineTo(w * 0.96f, h * 0.16f)
            }
            drawPath(check, Color.White, style = Stroke(width = w * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

internal val FinishedGreen = Color(0xFF22A355)

/** A soft shadow under white titles laid over story art, so they read on bright images. */
private val TitleShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 6f)
