package com.darrenai.omniscient.ui.settings

import androidx.lifecycle.ViewModel
import com.darrenai.omniscient.data.SettingsStore

data class SettingsState(
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val searchEndpoint: String = "",
    val searchKey: String = "",
    val speakReplies: Boolean = true
)

/** Uplink settings: endpoint, key, model, OmniRoute preset, search API, voice. */
class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {

    fun load(): SettingsState = SettingsState(
        endpoint = settings.endpoint,
        apiKey = settings.apiKey,
        model = settings.model,
        searchEndpoint = settings.searchEndpoint,
        searchKey = settings.searchKey,
        speakReplies = settings.speakReplies
    )

    fun save(s: SettingsState) {
        settings.endpoint = s.endpoint.ifBlank { SettingsStore.DEFAULT_ENDPOINT }
        settings.apiKey = s.apiKey
        settings.model = s.model.ifBlank { SettingsStore.DEFAULT_MODEL }
        settings.searchEndpoint = s.searchEndpoint
        settings.searchKey = s.searchKey
        settings.speakReplies = s.speakReplies
    }

    fun applyPreset(): SettingsState {
        settings.applyOmniRoutePreset()
        return load()
    }
}
