package com.darrenai.omniscient.ui.onboarding

import androidx.lifecycle.ViewModel
import com.darrenai.omniscient.data.SettingsStore

/** First-run screen: explains the butler, offers the OmniRoute preset. */
class OnboardingViewModel(private val settings: SettingsStore) : ViewModel() {
    val alreadyDone: Boolean get() = settings.onboarded
    fun complete() {
        settings.onboarded = true
    }
    fun applyPreset() = settings.applyOmniRoutePreset()
}
