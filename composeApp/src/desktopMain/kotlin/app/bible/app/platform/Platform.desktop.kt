package app.bible.app.platform

import androidx.compose.runtime.Composable

actual fun appIconSupported(): Boolean = false
actual fun currentAppIcon(): AppIcon = AppIcon.Red
actual fun selectAppIcon(icon: AppIcon) = Unit
actual fun appIconChangeNote(): String? = null

@Composable
actual fun SystemBarsEffect(hideStatusBar: Boolean) = Unit
