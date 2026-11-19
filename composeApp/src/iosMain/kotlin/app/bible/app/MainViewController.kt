package app.bible.app

import androidx.compose.ui.window.ComposeUIViewController
import app.bible.app.platform.statusBarListener
import platform.UIKit.UIViewController

/** Entry point used by iosApp (ContentView.swift). */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

/** ContentView.swift registers this once; it is called whenever Immersive view hides or shows the status bar. */
fun setStatusBarHiddenListener(listener: (Boolean) -> Unit) {
    statusBarListener = listener
}
