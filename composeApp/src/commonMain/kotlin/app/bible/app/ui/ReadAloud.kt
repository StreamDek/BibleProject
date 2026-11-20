package app.bible.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.bible.app.platform.Speech
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Whether a screen is reading aloud. */
@Stable
class ReadAloudState {
    var playing by mutableStateOf(false)
}

/**
 * Reads the page aloud while [ReadAloudState.playing]: the text from [text] for the page named by [page], then
 * [advance] to the next page (return false at the end, which stops). Turning the page by hand reads the new page.
 */
@Composable
fun rememberReadAloud(page: Any?, text: suspend () -> String?, advance: suspend () -> Boolean): ReadAloudState {
    val state = remember { ReadAloudState() }
    val currentText by rememberUpdatedState(text)
    val currentAdvance by rememberUpdatedState(advance)
    LaunchedEffect(state.playing, page) {
        if (!state.playing) {
            Speech.stop()
            return@LaunchedEffect
        }
        val words = currentText()?.let(::speakable)
        if (!words.isNullOrBlank()) speak(words)
        if (!currentAdvance()) state.playing = false
    }
    DisposableEffect(Unit) { onDispose { Speech.stop() } }
    return state
}

private suspend fun speak(text: String) = suspendCancellableCoroutine { cont ->
    Speech.speak(text) { if (cont.isActive) cont.resume(Unit) }
    cont.invokeOnCancellation { Speech.stop() }
}

/** Drops the KJV's paragraph marks and tidies spacing, so only words are read. */
private fun speakable(text: String): String = text.replace("¶", " ").replace(Regex("\\s+"), " ").trim()

/** Play / pause for reading aloud, beside "Aa". [overArt] draws it white with a soft shadow, for use on images. */
@Composable
fun ReadAloudButton(state: ReadAloudState, overArt: Boolean = false) {
    if (!Speech.available) return
    val icon = if (state.playing) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle
    IconButton(onClick = { state.playing = !state.playing }) {
        if (overArt) Icon(icon, null, tint = Color.Black.copy(alpha = 0.35f), modifier = Modifier.size(26.dp).padding(top = 1.dp))
        Icon(
            icon,
            contentDescription = if (state.playing) "Pause reading aloud" else "Read aloud",
            tint = if (overArt) Color.White else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(26.dp),
        )
    }
}
