package com.darrenai.omniscient.ui.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TTS wrapper. Crash-safe: if TTS is unavailable, all operations are no-ops.
 * Call [shutdown] in onDestroy to release resources.
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var onDone: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            onDone?.invoke()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {}
                    })
                    ready.set(true)
                }
            }
        } catch (e: Exception) {
            tts = null
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!ready.get()) return
        onDone = onComplete
        try {
            tts?.speak(text.take(500), TextToSpeech.QUEUE_ADD, null, "omni_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            // Silently fail — TTS is optional
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {}
        tts = null
    }
}
