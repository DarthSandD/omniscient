package com.darrenai.omniscient.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darrenai.omniscient.domain.Conversation
import com.darrenai.omniscient.domain.ConversationRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryState(val items: List<Conversation> = emptyList())

class HistoryViewModel(private val conversations: ConversationRepo) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { conversations.list() }
            _state.value = HistoryState(list)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { conversations.delete(id) }
            refresh()
        }
    }
}
