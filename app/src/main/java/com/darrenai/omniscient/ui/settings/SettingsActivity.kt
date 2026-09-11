package com.darrenai.omniscient.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.darrenai.omniscient.R
import com.darrenai.omniscient.ui.OmniViewModelFactory

/** Endpoint, API key, model fields + save; one-tap OmniRoute preset; voice toggle. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var vm: SettingsViewModel
    private lateinit var endpointField: EditText
    private lateinit var apiKeyField: EditText
    private lateinit var modelField: EditText
    private lateinit var voiceToggle: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        vm = ViewModelProvider(this, OmniViewModelFactory(application))[SettingsViewModel::class.java]

        endpointField = findViewById(R.id.settingsEndpoint) ?: run { finish(); return }
        apiKeyField = findViewById(R.id.settingsApiKey) ?: run { finish(); return }
        modelField = findViewById(R.id.settingsModel) ?: run { finish(); return }
        voiceToggle = findViewById(R.id.settingsVoiceOutput) ?: run { finish(); return }

        val state = vm.load()
        endpointField.setText(state.endpoint)
        apiKeyField.setText(state.apiKey)
        modelField.setText(state.model)
        voiceToggle.isChecked = state.speakReplies

        findViewById<Button>(R.id.settingsSave)?.setOnClickListener {
            vm.save(
                endpoint = endpointField.text.toString(),
                apiKey = apiKeyField.text.toString(),
                model = modelField.text.toString(),
                speakReplies = voiceToggle.isChecked
            )
            Toast.makeText(this, "Settings saved, boss.", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.settingsPreset)?.setOnClickListener {
            vm.applyOmniRoutePreset()
            val refreshed = vm.load()
            endpointField.setText(refreshed.endpoint)
            apiKeyField.setText(refreshed.apiKey)
            modelField.setText(refreshed.model)
            Toast.makeText(this, "OmniRoute preset applied, boss.", Toast.LENGTH_SHORT).show()
        }
    }
}
