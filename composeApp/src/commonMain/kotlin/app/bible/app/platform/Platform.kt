package app.bible.app.platform

import androidx.compose.runtime.Composable

/** The home screen icons the reader can choose from. Red is the default. */
enum class AppIcon(val label: String) { Red("Red"), White("White") }

/** Whether this platform can change the home screen icon. */
expect fun appIconSupported(): Boolean

/** The icon the reader has chosen (it may still be waiting to be applied). */
expect fun currentAppIcon(): AppIcon

/** Chooses the home screen icon. */
expect fun selectAppIcon(icon: AppIcon)

/** A short line for the settings sheet about when the change shows up, or null. */
expect fun appIconChangeNote(): String?

/** Hides or shows the status bar while this composable is on screen (immersive view). */
@Composable
expect fun SystemBarsEffect(hideStatusBar: Boolean)

/** Handles the system back gesture or button while [enabled] (Android). Other platforms use on-screen buttons. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** True when the reader asked the system to reduce motion; animations then cut instead of slide. */
expect fun reducedMotion(): Boolean
