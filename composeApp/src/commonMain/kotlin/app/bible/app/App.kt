package app.bible.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bible.app.data.BibleRepository
import app.bible.app.data.openBundledBible
import app.bible.app.feed.FeedViewModel
import app.bible.app.feed.ScriptureFeed
import app.bible.app.ui.BibleTheme

@Composable
fun App() {
    var repo by remember { mutableStateOf<BibleRepository?>(null) }
    LaunchedEffect(Unit) { repo = BibleRepository(openBundledBible()) }
    val r = repo
    if (r == null) {
        BibleTheme(dark = true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        return
    }
    val vm = viewModel { FeedViewModel(r) }
    val state by vm.state.collectAsStateWithLifecycle()
    BibleTheme(dark = state.prefs.dark) {
        ScriptureFeed(vm, translation = r.translationCode)
    }
}
