package com.darrenai.omniscient.ui.home

import androidx.lifecycle.ViewModel
import com.darrenai.omniscient.data.SettingsStore

class HomeViewModel(private val settings: SettingsStore) : ViewModel() {

    fun isOnboarded(): Boolean = settings.onboarded

    fun getSettingsSummary(): String {
        val endpoint = settings.endpoint
        val hasKey = settings.apiKey.isNotBlank()
        return if (hasKey) "Endpoint: $endpoint (key set)" else "Endpoint: $endpoint (no key)"
    }
}
