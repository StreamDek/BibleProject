package app.bible.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.bible.app.feed.AppSettingsSheet
import app.bible.app.ui.LocalOpenSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bible.app.data.BibleRepository
import app.bible.app.data.openBundledBibleDriver
import app.bible.app.data.openUserDriver
import app.bible.app.feed.FeedViewModel
import app.bible.app.resources.Res
import app.bible.app.resources.verseup_wordmark
import app.bible.app.feed.Reader
import app.bible.app.feed.StoryLinks
import app.bible.app.platform.PlatformBackHandler
import app.bible.app.stories.LocalStoryArt
import app.bible.app.stories.StoriesScreen
import app.bible.app.stories.StoriesViewModel
import app.bible.app.stories.StoryReader
import app.bible.core.VerseId
import app.bible.app.ui.BibleTheme
import app.bible.data.user.UserDatabase
import app.bible.data.user.UserStore
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import org.jetbrains.compose.resources.painterResource

/** The app's long-lived data sources, opened once at start-up. */
private class AppData(val bible: BibleRepository, val user: UserStore)

@Composable
fun App() {
    // Story art is fetched from the CDN and kept in Coil's memory and disk caches.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.crossfade(true).build()
    }
    var data by remember { mutableStateOf<AppData?>(null) }
    LaunchedEffect(Unit) {
        data = AppData(BibleRepository(openBundledBibleDriver()), UserStore(UserDatabase(openUserDriver())))
    }
    val d = data
    if (d == null) {
        // Start screen while the databases open (first launch copies the bundled Bible).
        BibleTheme(dark = true) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(Res.drawable.verseup_wordmark),
                    contentDescription = "VerseUp",
                    modifier = Modifier.fillMaxWidth(0.72f),
                )
            }
        }
        return
    }
    val vm = viewModel { FeedViewModel(d.bible, d.user) }
    val stories = viewModel { StoriesViewModel(d.bible, d.user) }
    val state by vm.state.collectAsStateWithLifecycle()
    val home by stories.home.collectAsStateWithLifecycle()
    // A small back stack: the Bible is the root; Stories and a story sit on top of it.
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Bible)) }
    fun openStory(slug: String, at: VerseId? = null) {
        stories.open(slug, at)
        stack = stack.takeWhile { it !is Screen.Story } + Screen.Story(slug)
    }
    PlatformBackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }
    val translation = d.bible.translationCode

    var settingsOpen by remember { mutableStateOf(false) }
    val storiesState = rememberSaveableStateHolder()

    BibleTheme(dark = state.prefs.dark) {
      CompositionLocalProvider(LocalStoryArt provides home.art, LocalOpenSettings provides { settingsOpen = true }) {
        when (val top = stack.last()) {
            Screen.Bible -> {
                val from = (stack.getOrNull(stack.lastIndex - 1) as? Screen.Story)?.let { home.library?.get(it.slug) }
                Reader(
                    vm,
                    translation = translation,
                    links = StoryLinks(
                        storyFor = stories::storyFor,
                        onOpenStory = { s, verse -> openStory(s.slug, verse) },
                        onStories = { stack = stack.takeWhile { it != Screen.Stories } + Screen.Stories },
                        backTo = from,
                        onBackToStory = { stack = stack.dropLast(1) },
                    ),
                )
            }
            // Kept in a saveable holder, so coming back from a story returns to the same place: the list's scroll,
            // each shelf's sideways scroll, the Journey list and any search.
            Screen.Stories -> storiesState.SaveableStateProvider("stories") {
                StoriesScreen(
                    stories,
                    onOpen = { openStory(it.slug) },
                    onBible = { stack = listOf(Screen.Bible) },
                )
            }
            is Screen.Story -> StoryReader(
                vm = stories,
                feed = vm,
                translation = translation,
                onBack = {
                    stack = stack.dropLast(1)
                    if (stack.lastOrNull() != Screen.Stories) stack = stack + Screen.Stories
                },
                onOpenInBible = { verse ->
                    vm.open(verse)
                    stack = stack + Screen.Bible
                },
                onOpenStory = { openStory(it.slug) },
            )
        }
        if (settingsOpen) AppSettingsSheet(state.prefs, vm, onDismiss = { settingsOpen = false })
      }
    }
}

private sealed interface Screen {
    data object Bible : Screen
    data object Stories : Screen
    data class Story(val slug: String) : Screen
}
