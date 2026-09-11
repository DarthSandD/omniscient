package com.darrenai.omniscient.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** SpeechRecognizer wrapper with graceful degradation when recognition is unavailable. */
class VoiceManager(private val context: Context) {

    interface Callback {
        fun onResult(text: String)
        fun onPartial(text: String)
        fun onError(message: String)
    }

    var callback: Callback? = null
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    val isListening: Boolean get() = listening

    fun startListening() {
        if (listening) return
        if (!isAvailable) {
            callback?.onError("Speech recognition not available on this device, boss — type instead.")
            return
        }
        stopListening()
        val rec = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (rec == null) {
            callback?.onError("Could not start the microphone, boss — type instead.")
            return
        }
        recognizer = rec
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                callback?.onError(friendlyError(error))
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()?.trim().orEmpty()
                if (text.isBlank()) callback?.onError("Didn't catch that, boss — try again or type.")
                else callback?.onResult(text)
            }
            override fun onPartialResults(partial: Bundle?) {
                val list = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                list?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { callback?.onPartial(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        runCatching { rec.startListening(intent) }.onFailure {
            listening = false
            callback?.onError("Microphone failed to start, boss — type instead.")
            return
        }
        listening = true
    }

    fun stopListening() {
        listening = false
        runCatching {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    companion object {
        fun friendlyError(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that, boss — try again or type."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission denied, boss — type instead, or allow it in system settings."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Voice service needs network, boss — type instead."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice engine busy, boss — wait a moment and retry."
            else -> "Voice input failed, boss — type instead."
        }
    }
}
