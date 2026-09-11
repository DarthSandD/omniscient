package com.darrenai.omniscient

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Uplink settings: endpoint URL, API key, model, voice toggle. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = Prefs(this)
        val endpoint = findViewById<EditText>(R.id.endpointInput)
        val apiKey = findViewById<EditText>(R.id.apiKeyInput)
        val model = findViewById<EditText>(R.id.modelInput)
        val ttsSwitch = findViewById<Switch>(R.id.ttsSwitch)
        val note = findViewById<TextView>(R.id.savedNote)

        endpoint.setText(prefs.endpoint)
        endpoint.hint = Prefs.DEFAULT_ENDPOINT
        apiKey.setText(prefs.apiKey)
        apiKey.hint = "sk-…"
        model.setText(prefs.model)
        model.hint = Prefs.DEFAULT_MODEL
        ttsSwitch.isChecked = prefs.speakReplies

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val ep = endpoint.text.toString().trim().trimEnd('/').ifBlank { Prefs.DEFAULT_ENDPOINT }
            prefs.endpoint = ep
            prefs.apiKey = apiKey.text.toString().trim()
            prefs.model = model.text.toString().trim().ifBlank { Prefs.DEFAULT_MODEL }
            prefs.speakReplies = ttsSwitch.isChecked
            note.text = "◆ UPLINK SAVED"
            note.postDelayed({ note.text = "" }, 2500)
        }
    }
}
