package com.darrenai.omniscient.ui.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Voice input via SpeechRecognizer with typed-text fallback.
 * Crash-safe: if speech recognition is unavailable, the callback is never invoked.
 */
class VoiceManager(private val activity: AppCompatActivity) {

    interface Callback {
        fun onVoiceResult(text: String)
    }

    var callback: Callback? = null
    private var launcher: ActivityResultLauncher<Intent>? = null

    init {
        try {
            launcher = activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data
                    val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    val text = matches?.firstOrNull().orEmpty().trim()
                    if (text.isNotEmpty()) {
                        callback?.onVoiceResult(text)
                    }
                }
            }
        } catch (e: Exception) {
            launcher = null
        }
    }

    fun startListening() {
        if (launcher == null) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak, boss…")
            }
            launcher?.launch(intent)
        } catch (e: Exception) {
            // Silently fail — voice is optional
        }
    }
}
