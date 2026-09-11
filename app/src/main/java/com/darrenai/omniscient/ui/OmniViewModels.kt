package com.darrenai.omniscient.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.darrenai.omniscient.domain.AgentLog
import com.darrenai.omniscient.ui.chat.ChatViewModel
import com.darrenai.omniscient.ui.history.HistoryViewModel
import com.darrenai.omniscient.ui.home.HomeViewModel
import com.darrenai.omniscient.ui.onboarding.OnboardingViewModel
import com.darrenai.omniscient.ui.settings.SettingsViewModel
import com.darrenai.omniscient.ui.terminal.TerminalViewModel

/** Manual injection: every screen's ViewModel is built from the OmniApp root. */
class OmniViewModels(private val app: OmniApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        HomeViewModel::class.java -> HomeViewModel(app.sendMessage, app.conversations, app.memory, app.tools)
        ChatViewModel::class.java -> ChatViewModel(app.sendMessage, app.conversations, app.memory, app.tools)
        HistoryViewModel::class.java -> HistoryViewModel(app.conversations)
        TerminalViewModel::class.java -> TerminalViewModel(AgentLog)
        SettingsViewModel::class.java -> SettingsViewModel(app.settings)
        OnboardingViewModel::class.java -> OnboardingViewModel(app.settings)
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}

/** Shorthand: viewModels(factory) without ceremony in each screen. */
inline fun <reified T : ViewModel> AppCompatActivity.omniViewModel(): Lazy<T> =
    lazy { ViewModelProvider(this, OmniViewModels(omni()))[T::class.java] }
