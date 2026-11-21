package app.bible.app.platform

/**
 * The device's own text-to-speech voice (Android TextToSpeech, iOS AVSpeechSynthesizer): free, offline, and
 * nothing leaves the phone.
 */
expect object Speech {
    /** False where the platform has no voice (the desktop preview). */
    val available: Boolean

    /** Reads [text] aloud, replacing anything being read, and calls [onDone] on the main thread when it ends. */
    fun speak(text: String, onDone: () -> Unit)

    /** Says one word slowly (a Hebrew or Greek word's pronunciation), stopping anything being read. */
    fun pronounce(text: String)

    /** Stops reading at once. The pending [speak] callback is dropped. */
    fun stop()
}
