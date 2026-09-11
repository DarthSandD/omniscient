package com.darrenai.omniscient.ui.home

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darrenai.omniscient.data.tools.DeviceTools
import com.darrenai.omniscient.data.tools.OfflineIntents
import com.darrenai.omniscient.domain.Conversation
import com.darrenai.omniscient.domain.ConversationRepo
import com.darrenai.omniscient.domain.MemoryRepo
import com.darrenai.omniscient.domain.Message
import com.darrenai.omniscient.domain.OrbState
import com.darrenai.omniscient.domain.Persona
import com.darrenai.omniscient.domain.SendMessageUseCase
import com.darrenai.omniscient.domain.titleFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val orb: OrbState = OrbState.IDLE,
    val heard: String = "",
    val replySeq: Int = 0,
    val busy: Boolean = false
)

/** Home screen state: one quick Q&A turn per send, persisted to history. */
class HomeViewModel(
    private val sendMessage: SendMessageUseCase,
    private val conversations: ConversationRepo,
    private val memory: MemoryRepo,
    private val tools: DeviceTools
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var conv: Conversation? = null

    fun greeting(): String = Persona.greeting()

    fun markSpeaking(speaking: Boolean) {
        _state.update { it.copy(orb = if (speaking) OrbState.SPEAKING else OrbState.IDLE) }
    }

    fun markListening(listening: Boolean) {
        _state.update { it.copy(orb = if (listening) OrbState.LISTENING else OrbState.IDLE) }
    }

    fun showHeard(text: String) {
        _state.update { it.copy(heard = text) }
    }

    fun send(activity: AppCompatActivity, prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _state.value.busy) return
        viewModelScope.launch {
            var c = conv ?: conversations.new().also { conv = it }
            c.messages.add(Message("user", clean))
            _state.update { it.copy(orb = OrbState.THINKING, busy = true, heard = "“$clean”") }
            val res = sendMessage.run(
                history = c.messages.toList(),
                facts = memory.facts(),
                offline = { OfflineIntents.tryHandle(activity, tools, clean) },
                exec = { name, args ->
                    _state.update { it.copy(orb = OrbState.ACTING) }
                    tools.run(activity, name, args)
                }
            )
            c.messages.add(Message("assistant", res.text))
            conv = conversations.save(c.copy(title = titleFor(c.messages)))
            _state.update { it.copy(orb = OrbState.IDLE, busy = false, heard = res.text, replySeq = it.replySeq + 1) }
        }
    }
}
