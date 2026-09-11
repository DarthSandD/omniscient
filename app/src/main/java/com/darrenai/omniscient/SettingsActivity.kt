package com.darrenai.omniscient

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Uplink settings: endpoint URL, API key, model, OmniRoute preset, search API, voice toggle. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = Prefs(this)
        val endpoint = findViewById<EditText>(R.id.endpointInput)
        val apiKey = findViewById<EditText>(R.id.apiKeyInput)
        val model = findViewById<EditText>(R.id.modelInput)
        val searchEndpoint = findViewById<EditText>(R.id.searchEndpointInput)
        val searchKey = findViewById<EditText>(R.id.searchApiKeyInput)
        val ttsSwitch = findViewById<Switch>(R.id.ttsSwitch)
        val note = findViewById<TextView>(R.id.savedNote)

        fun refresh() {
            endpoint.setText(prefs.endpoint)
            apiKey.setText(prefs.apiKey)
            model.setText(prefs.model)
            searchEndpoint.setText(prefs.searchEndpoint)
            searchKey.setText(prefs.searchKey)
            ttsSwitch.isChecked = prefs.speakReplies
        }
        refresh()
        endpoint.hint = Prefs.DEFAULT_ENDPOINT
        apiKey.hint = "sk-… (or x for OmniRoute)"
        model.hint = Prefs.DEFAULT_MODEL
        searchEndpoint.hint = "https://… (empty = web search off)"

        findViewById<Button>(R.id.omniRouteButton).setOnClickListener {
            prefs.applyOmniRoutePreset()
            refresh()
            note.text = "◆ OMNIROUTE PRESET APPLIED"
            note.postDelayed({ note.text = "" }, 2500)
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val ep = endpoint.text.toString().trim().trimEnd('/').ifBlank { Prefs.DEFAULT_ENDPOINT }
            prefs.endpoint = ep
            prefs.apiKey = apiKey.text.toString().trim()
            prefs.model = model.text.toString().trim().ifBlank { Prefs.DEFAULT_MODEL }
            prefs.searchEndpoint = searchEndpoint.text.toString().trim().trimEnd('/')
            prefs.searchKey = searchKey.text.toString().trim()
            prefs.speakReplies = ttsSwitch.isChecked
            note.text = "◆ UPLINK SAVED"
            note.postDelayed({ note.text = "" }, 2500)
        }
    }
}
