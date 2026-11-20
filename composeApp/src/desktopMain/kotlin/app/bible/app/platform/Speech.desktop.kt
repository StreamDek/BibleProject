package app.bible.app.platform

actual object Speech {
    actual val available: Boolean = false
    actual fun speak(text: String, onDone: () -> Unit) = onDone()
    actual fun stop() = Unit
}
