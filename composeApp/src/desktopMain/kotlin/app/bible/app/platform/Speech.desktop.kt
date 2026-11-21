package app.bible.app.platform

actual object Speech {
    actual val available: Boolean = false
    actual fun speak(text: String, onDone: () -> Unit) = onDone()
    actual fun pronounce(text: String) = Unit
    actual fun stop() = Unit
}
