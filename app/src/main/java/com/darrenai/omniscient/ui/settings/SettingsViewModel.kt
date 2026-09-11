package com.darrenai.omniscient.ui.settings

import androidx.lifecycle.ViewModel
import com.darrenai.omniscient.data.SettingsStore

data class SettingsState(
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val speakReplies: Boolean
)

class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {

    fun load(): SettingsState = SettingsState(
        endpoint = settings.endpoint,
        apiKey = settings.apiKey,
        model = settings.model,
        speakReplies = settings.speakReplies
    )

    fun save(endpoint: String, apiKey: String, model: String, speakReplies: Boolean) {
        settings.endpoint = endpoint
        settings.apiKey = apiKey
        settings.model = model
        settings.speakReplies = speakReplies
    }

    fun applyOmniRoutePreset() {
        settings.applyOmniRoutePreset()
    }
}
