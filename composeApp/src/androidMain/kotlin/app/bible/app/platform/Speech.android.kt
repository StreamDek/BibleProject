package app.bible.app.platform

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.bible.app.data.appContext
import java.util.Locale

actual object Speech {
    actual val available: Boolean = true

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var callback: (() -> Unit)? = null
    private var lastId: String? = null
    private var counter = 0

    private fun engine(): TextToSpeech = tts ?: TextToSpeech(appContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            val engine = tts
            if (ready && engine != null) {
                // The text is English: use an English voice even when the phone is set to another language.
                if (Locale.getDefault().language != "en") engine.language = Locale.US
                pending?.let { pending = null; speakNow(it) }
            } else {
                pending = null
                finish()
            }
        }
    }.also { engine ->
        tts = engine
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                main.post { if (utteranceId != null && utteranceId == lastId) finish() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onDone(utteranceId)
        })
    }

    private fun finish() {
        val done = callback
        callback = null
        done?.invoke()
    }

    actual fun speak(text: String, onDone: () -> Unit) {
        stop()
        callback = onDone
        engine()
        if (ready) speakNow(text) else pending = text
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        val chunks = chunk(text, TextToSpeech.getMaxSpeechInputLength() - 100)
        val run = ++counter
        chunks.forEachIndexed { i, part ->
            val id = "verseup-$run-$i"
            if (i == chunks.lastIndex) lastId = id
            engine.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        if (chunks.isEmpty()) finish()
    }

    actual fun stop() {
        callback = null
        pending = null
        lastId = null
        tts?.stop()
    }

    /** Splits long text at sentence ends so each piece fits the engine's input limit. */
    private fun chunk(text: String, max: Int): List<String> {
        val out = ArrayList<String>()
        var rest = text.trim()
        while (rest.length > max) {
            val cut = rest.lastIndexOfAny(charArrayOf('.', ';', ':', '?', '!'), max).takeIf { it > max / 2 }
                ?: rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
            out += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }
}
