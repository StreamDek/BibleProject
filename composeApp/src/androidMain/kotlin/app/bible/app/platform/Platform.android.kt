package app.bible.app.platform

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import app.bible.app.data.appContext

// ---- app icon -----------------------------------------------------------------------------------------
// Each icon is an <activity-alias> in androidApp's manifest (app.bible.android.LauncherRed / LauncherWhite).
// Switching aliases while the app is in front can close it on some launchers, so the choice is saved here
// and applied by applyPendingAppIcon() when the reader leaves the app (MainActivity.onStop).

private const val ICON_PREFS = "app_icon"
private const val KEY_PENDING = "pending"

private fun alias(icon: AppIcon) = ComponentName(appContext.packageName, "app.bible.android.Launcher${icon.name}")

private fun prefs() = appContext.getSharedPreferences(ICON_PREFS, Context.MODE_PRIVATE)

private fun isEnabled(pm: PackageManager, icon: AppIcon): Boolean =
    when (pm.getComponentEnabledSetting(alias(icon))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.Red // the manifest enables only Red
        else -> false
    }

actual fun appIconSupported(): Boolean = true

actual fun currentAppIcon(): AppIcon {
    prefs().getString(KEY_PENDING, null)?.let { name -> AppIcon.entries.firstOrNull { it.name == name } }?.let { return it }
    val pm = appContext.packageManager
    return AppIcon.entries.firstOrNull { isEnabled(pm, it) } ?: AppIcon.Red
}

actual fun selectAppIcon(icon: AppIcon) {
    prefs().edit().putString(KEY_PENDING, icon.name).apply()
}

actual fun appIconChangeNote(): String? = "Your home screen icon changes after you leave VerseUp."

/** Applies a pending icon choice. Call when the app goes to the background. */
fun applyPendingAppIcon() {
    val name = prefs().getString(KEY_PENDING, null) ?: return
    val chosen = AppIcon.entries.firstOrNull { it.name == name } ?: AppIcon.Red
    val pm = appContext.packageManager
    // Enable the new alias before disabling the old one so there is always a launcher entry.
    pm.setComponentEnabledSetting(alias(chosen), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    for (other in AppIcon.entries - chosen) {
        pm.setComponentEnabledSetting(alias(other), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
    prefs().edit().remove(KEY_PENDING).apply()
}

// ---- immersive view -----------------------------------------------------------------------------------

@Composable
actual fun SystemBarsEffect(hideStatusBar: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, hideStatusBar) {
        view.context.findActivity()?.window?.let { setStatusBarHidden(it, hideStatusBar) }
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setStatusBarHidden(window: Window, hidden: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController ?: return
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.statusBars())
        } else {
            controller.show(WindowInsets.Type.statusBars())
        }
    } else {
        @Suppress("DEPRECATION")
        val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val decor = window.decorView
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = if (hidden) decor.systemUiVisibility or flags else decor.systemUiVisibility and flags.inv()
    }
}
