package com.darrenai.omniscient.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.darrenai.omniscient.data.FileConversations
import com.darrenai.omniscient.data.FileMemory
import com.darrenai.omniscient.data.OpenAiService
import com.darrenai.omniscient.data.SettingsStore
import com.darrenai.omniscient.data.tools.DeviceTools
import com.darrenai.omniscient.data.tools.ToolCatalog
import com.darrenai.omniscient.domain.AgentLog
import com.darrenai.omniscient.domain.SendMessageUseCase
import com.darrenai.omniscient.ui.chat.ChatViewModel
import com.darrenai.omniscient.ui.history.HistoryViewModel
import com.darrenai.omniscient.ui.home.HomeViewModel
import com.darrenai.omniscient.ui.onboarding.OnboardingViewModel
import com.darrenai.omniscient.ui.settings.SettingsViewModel
import com.darrenai.omniscient.ui.terminal.TerminalViewModel

/**
 * Single factory for all ViewModels. Constructs each one with its dependencies
 * using plain ViewModelProvider(this, factory)[X::class.java] — no reified
 * generics, no lazy delegates.
 */
class OmniViewModelFactory(private val app: Application) : ViewModelProvider.Factory {

    private val settings by lazy { SettingsStore(app) }
    private val memory by lazy { FileMemory(app) }
    private val conversations by lazy { FileConversations(app) }
    private val tools by lazy { DeviceTools(settings, memory) }
    private val catalog by lazy { ToolCatalog() }
    private val chat by lazy { OpenAiService(settings) }
    private val sendMessage by lazy { SendMessageUseCase(chat, catalog, AgentLog) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            OnboardingViewModel::class.java -> OnboardingViewModel(settings)
            HomeViewModel::class.java -> HomeViewModel(settings)
            ChatViewModel::class.java -> ChatViewModel(sendMessage, conversations, memory, tools)
            HistoryViewModel::class.java -> HistoryViewModel(conversations)
            TerminalViewModel::class.java -> TerminalViewModel()
            SettingsViewModel::class.java -> SettingsViewModel(settings)
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
