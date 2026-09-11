package com.darrenai.omniscient.ui.chat

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
import androidx.appcompat.app.AppCompatActivity
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

    fun open(id: Long) {
        conv = conversations.load(id) ?: conversations.new()
        _state.update { it.copy(messages = conv.messages.toList()) }
    }

    fun send(activity: AppCompatActivity, text: String) {
        if (text.isBlank()) return
        val userMsg = Message("user", text)
        conv.messages.add(userMsg)
        _state.update { it.copy(messages = conv.messages.toList(), busy = true) }

        viewModelScope.launch {
            val facts = memory.facts()
            val result = sendMessage.run(
                history = conv.messages.toList(),
                facts = facts,
                offline = { OfflineIntents.tryHandle(activity, tools, text) },
                exec = { name, argsJson -> tools.run(activity, name, argsJson) }
            )
            val assistantMsg = Message("assistant", result.text)
            conv.messages.add(assistantMsg)
            conv.title = titleFor(conv.messages)
            withContext(Dispatchers.IO) { conversations.save(conv) }
            _state.update { it.copy(messages = conv.messages.toList(), busy = false, replySeq = it.replySeq + 1) }
        }
    }

    fun getId(): Long = if (::conv.isInitialized) conv.id else -1L
}
