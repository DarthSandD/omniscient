package com.darrenai.omniscient.ui.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/** TextToSpeech wrapper: lazy init, never crashes the host activity on failure. */
class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    var onStart: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.let {
                        it.language = Locale.getDefault()
                        it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(id: String) { onStart?.invoke() }
                            override fun onDone(id: String) { onDone?.invoke() }
                            override fun onError(id: String) { onDone?.invoke() }
                        })
                    }
                }
            }
        }
    }

    val isReady: Boolean get() = ready

    fun speak(text: String) {
        val engine = tts
        if (!ready || engine == null) return
        runCatching {
            engine.stop()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), UUID.randomUUID().toString())
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
    }
}
