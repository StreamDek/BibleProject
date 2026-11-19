package app.bible.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Desktop preview of the shared UI, sized like a phone. Used for fast iteration and screenshots. */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "VerseUp", state = rememberWindowState(width = 412.dp, height = 892.dp)) {
        App()
    }
}
