package com.darrenai.omniscient.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.darrenai.omniscient.R
import com.darrenai.omniscient.data.SettingsStore
import com.darrenai.omniscient.ui.omniViewModel

/** Uplink settings: endpoint URL, API key, model, OmniRoute preset, search API, voice toggle. */
class SettingsActivity : AppCompatActivity() {

    private val vm: SettingsViewModel by omniViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val endpoint = findViewById<EditText>(R.id.endpointInput)
        val apiKey = findViewById<EditText>(R.id.apiKeyInput)
        val model = findViewById<EditText>(R.id.modelInput)
        val searchEndpoint = findViewById<EditText>(R.id.searchEndpointInput)
        val searchKey = findViewById<EditText>(R.id.searchApiKeyInput)
        val ttsSwitch = findViewById<Switch>(R.id.ttsSwitch)
        val note = findViewById<TextView>(R.id.savedNote)

        fun refresh(s: SettingsState = vm.load()) {
            endpoint.setText(s.endpoint)
            apiKey.setText(s.apiKey)
            model.setText(s.model)
            searchEndpoint.setText(s.searchEndpoint)
            searchKey.setText(s.searchKey)
            ttsSwitch.isChecked = s.speakReplies
        }
        refresh()
        endpoint.hint = SettingsStore.DEFAULT_ENDPOINT
        apiKey.hint = "sk-… (or x for OmniRoute)"
        model.hint = SettingsStore.DEFAULT_MODEL
        searchEndpoint.hint = "https://… (empty = web search off)"

        fun flash(msg: String) {
            note.text = msg
            note.postDelayed({ note.text = "" }, 2500)
        }

        findViewById<Button>(R.id.omniRouteButton).setOnClickListener {
            refresh(vm.applyPreset())
            flash("◆ OMNIROUTE PRESET APPLIED, BOSS")
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            vm.save(
                SettingsState(
                    endpoint = endpoint.text.toString().trim().trimEnd('/'),
                    apiKey = apiKey.text.toString().trim(),
                    model = model.text.toString().trim(),
                    searchEndpoint = searchEndpoint.text.toString().trim().trimEnd('/'),
                    searchKey = searchKey.text.toString().trim(),
                    speakReplies = ttsSwitch.isChecked
                )
            )
            flash("◆ UPLINK SAVED")
        }
    }
}
