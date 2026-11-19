package app.bible.app.stories

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bible.app.platform.PlatformBackHandler
import app.bible.app.ui.topSafePadding
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
    val colors = MaterialTheme.colorScheme
    PlatformBackHandler(enabled = journey) { journey = false }

    Column(Modifier.fillMaxSize().background(colors.background).topSafePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (journey) TextButton(onClick = { journey = false }) { Text("‹ Stories", color = colors.onBackground) }
            Text(
                if (journey) "Journey through the Bible" else "Stories",
                style = if (journey) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBible) { Text("Bible ›", color = colors.onBackground) }
        }
        val library = home.library
        if (library == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        if (!journey) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search stories, people or a reference") },
                singleLine = true,
                shape = CircleShape, // search fields are pill-shaped: no sharp corners
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        val bottom = PaddingValues(bottom = 32.dp)
        when {
            journey -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = bottom) {
                var period: Period? = null
                for (s in library.stories) {
                    if (s.period != period) {
                        period = s.period
                        val title = s.period.title
                        item(key = "h-$title") { SectionHeader(title) }
                    }
                    item(key = s.slug) { StoryRow(s, s.slug in home.finished) { onOpen(s) } }
                }
            }
            query.isNotBlank() -> {
                val results = remember(query, library) { library.search(query) }
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = bottom) {
                    if (results.isEmpty()) item {
                        Text(
                            "No stories found. Try a name, a place, or a reference like \"Luke 2\".",
                            color = colors.secondary,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    items(results, key = { it.slug }) { s -> StoryRow(s, s.slug in home.finished) { onOpen(s) } }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = bottom) {
                home.last?.let { last ->
                    if (last.slug !in home.finished) item(key = "continue") { ContinueCard(last) { onOpen(last) } }
                }
                item(key = "journey") {
                    TextButton(onClick = { journey = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Journey through the Bible: every story in order ›", color = colors.primary)
                    }
                }
                for (shelf in library.shelves()) {
                    item(key = "shelf-${shelf.title}") {
                        Column {
                            SectionHeader("${shelf.title}  ·  ${shelf.stories.size}")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(shelf.stories, key = { it.slug }) { s -> StoryTile(s, s.slug in home.finished) { onOpen(s) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
    )
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
            Text(story.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(story.sourcesLine(), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun StoryTile(story: Story, finished: Boolean, onClick: () -> Unit) {
    Box(Modifier.width(148.dp).height(196.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        StoryArt(story, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)),
            ),
        )
        if (finished) {
            Text("✓", color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                story.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp,
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
private fun StoryRow(story: Story, finished: Boolean, onClick: () -> Unit) {
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
        if (finished) Text("✓", color = colors.primary)
    }
}
