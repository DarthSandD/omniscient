package com.darrenai.omniscient.ui

import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import com.darrenai.omniscient.data.FileConversations
import com.darrenai.omniscient.data.FileMemory
import com.darrenai.omniscient.data.OpenAiService
import com.darrenai.omniscient.data.SettingsStore
import com.darrenai.omniscient.data.tools.DeviceTools
import com.darrenai.omniscient.data.tools.ToolCatalog
import com.darrenai.omniscient.domain.AgentLog
import com.darrenai.omniscient.domain.ChatService
import com.darrenai.omniscient.domain.ConversationRepo
import com.darrenai.omniscient.domain.MemoryRepo
import com.darrenai.omniscient.domain.SendMessageUseCase
import com.darrenai.omniscient.domain.ToolRegistry

/**
 * Composition root. All wiring lives here — screens receive collaborators
 * through their ViewModel factories, never by constructing them.
 */
class OmniApp : Application() {
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val conversations: ConversationRepo by lazy { FileConversations(this) }
    val memory: MemoryRepo by lazy { FileMemory(this) }
    val chat: ChatService by lazy { OpenAiService(settings) }
    val registry: ToolRegistry by lazy { ToolCatalog() }
    val tools: DeviceTools by lazy { DeviceTools(settings, memory) }
    val sendMessage: SendMessageUseCase by lazy { SendMessageUseCase(chat, registry, AgentLog) }
}

/** Shorthand for screens to reach the composition root. */
fun AppCompatActivity.omni(): OmniApp = application as OmniApp
