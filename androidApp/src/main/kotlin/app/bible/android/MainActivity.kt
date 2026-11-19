package app.bible.android

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.bible.app.App
import app.bible.app.data.appContext
import app.bible.app.platform.applyPendingAppIcon

class BibleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    override fun onStop() {
        super.onStop()
        // A new home screen icon is applied once the reader has left the app (see Aa > App icon).
        applyPendingAppIcon()
    }
}
