package com.darrenai.omniscient.ui.onboarding

import androidx.lifecycle.ViewModel
import com.darrenai.omniscient.data.SettingsStore

class OnboardingViewModel(private val settings: SettingsStore) : ViewModel() {

    fun completeOnboarding() {
        settings.onboarded = true
    }

    fun isOnboarded(): Boolean = settings.onboarded
}
