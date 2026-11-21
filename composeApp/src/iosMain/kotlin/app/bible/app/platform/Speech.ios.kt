package app.bible.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual object Speech {
    actual val available: Boolean = true

    private val synth = AVSpeechSynthesizer()
    private var current: AVSpeechUtterance? = null
    private var callback: (() -> Unit)? = null

    // Held here because the synthesizer keeps only a weak reference to its delegate.
    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
            if (didFinishSpeechUtterance == current) {
                current = null
                val done = callback
                callback = null
                done?.invoke()
            }
        }
    }

    init {
        synth.delegate = delegate
    }

    actual fun speak(text: String, onDone: () -> Unit) = utter(text, 1f, onDone)

    private fun utter(text: String, rate: Float, onDone: () -> Unit) {
        stop()
        // Play even with the ring/silent switch on, like any audio the reader asked for.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        AVAudioSession.sharedInstance().setActive(true, null)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        val lang = AVSpeechSynthesisVoice.currentLanguageCode().takeIf { it.startsWith("en") } ?: "en-US"
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(lang)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate * rate
        current = utterance
        callback = onDone
        synth.speakUtterance(utterance)
    }

    actual fun pronounce(text: String) = utter(text, 0.7f) {}

    actual fun stop() {
        callback = null
        current = null
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}
