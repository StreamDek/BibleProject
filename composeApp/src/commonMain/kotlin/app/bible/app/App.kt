package app.bible.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import app.bible.app.ui.BibleTheme
import app.bible.data.user.UserDatabase
import app.bible.data.user.UserStore
import org.jetbrains.compose.resources.painterResource

/** The app's long-lived data sources, opened once at start-up. */
private class AppData(val bible: BibleRepository, val user: UserStore)

@Composable
fun App() {
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
    val state by vm.state.collectAsStateWithLifecycle()
    BibleTheme(dark = state.prefs.dark) {
        Reader(vm, translation = d.bible.translationCode)
    }
}
