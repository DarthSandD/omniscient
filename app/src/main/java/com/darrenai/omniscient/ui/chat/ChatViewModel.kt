package com.darrenai.omniscient.ui.chat

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darrenai.omniscient.data.tools.DeviceTools
import com.darrenai.omniscient.data.tools.OfflineIntents
import com.darrenai.omniscient.domain.Conversation
import com.darrenai.omniscient.domain.ConversationRepo
import com.darrenai.omniscient.domain.MemoryRepo
import com.darrenai.omniscient.domain.Message
import com.darrenai.omniscient.domain.SendMessageUseCase
import com.darrenai.omniscient.domain.titleFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatState(
    val messages: List<Message> = emptyList(),
    val busy: Boolean = false,
    val replySeq: Int = 0
)

/** Full session screen: owns the conversation, streams turns through the use-case. */
class ChatViewModel(
    private val sendMessage: SendMessageUseCase,
    private val conversations: ConversationRepo,
    private val memory: MemoryRepo,
    private val tools: DeviceTools
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private lateinit var conv: Conversation

    fun open(convId: Long) {
        if (this::conv.isInitialized) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                if (convId > 0) conversations.load(convId) else null
            } ?: conversations.new()
            conv = loaded
            _state.update { it.copy(messages = conv.messages.toList()) }
        }
    }

    fun lastAssistantReply(): String? =
        _state.value.messages.lastOrNull { it.role == "assistant" }?.content

    fun send(activity: AppCompatActivity, prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _state.value.busy || !this::conv.isInitialized) return
        viewModelScope.launch {
            conv.messages.add(Message("user", clean))
            _state.update { it.copy(messages = conv.messages.toList(), busy = true) }
            val res = sendMessage.run(
                history = conv.messages.toList(),
                facts = memory.facts(),
                offline = { OfflineIntents.tryHandle(activity, tools, clean) },
                exec = { name, args -> tools.run(activity, name, args) }
            )
            conv.messages.add(Message("assistant", res.text))
            conv = conversations.save(conv.copy(title = titleFor(conv.messages)))
            _state.update { it.copy(messages = conv.messages.toList(), busy = false, replySeq = it.replySeq + 1) }
        }
    }
}
