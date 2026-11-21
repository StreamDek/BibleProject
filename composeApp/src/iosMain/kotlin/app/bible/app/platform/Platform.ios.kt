package app.bible.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.UIKit.UIApplication

// ---- app icon -----------------------------------------------------------------------------------------
// "AppIcon-White" is an alternate icon set in iosApp's asset catalog (see project.yml). Red is the primary icon.

private const val WHITE_ICON = "AppIcon-White"

actual fun appIconSupported(): Boolean = UIApplication.sharedApplication.supportsAlternateIcons

actual fun currentAppIcon(): AppIcon =
    if (UIApplication.sharedApplication.alternateIconName == WHITE_ICON) AppIcon.White else AppIcon.Red

actual fun selectAppIcon(icon: AppIcon) {
    val name = if (icon == AppIcon.White) WHITE_ICON else null
    if (UIApplication.sharedApplication.alternateIconName == name) return
    UIApplication.sharedApplication.setAlternateIconName(name) { _ -> }
}

actual fun appIconChangeNote(): String? = null

// ---- immersive view -----------------------------------------------------------------------------------
// SwiftUI owns the status bar, so ContentView.swift registers a listener and applies .statusBarHidden().

internal var statusBarListener: ((Boolean) -> Unit)? = null
    set(value) { field = value; value?.invoke(statusBarHidden) }
private var statusBarHidden = false

@Composable
actual fun SystemBarsEffect(hideStatusBar: Boolean) {
    LaunchedEffect(hideStatusBar) {
        statusBarHidden = hideStatusBar
        statusBarListener?.invoke(hideStatusBar)
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual fun reducedMotion(): Boolean = platform.UIKit.UIAccessibilityIsReduceMotionEnabled()
