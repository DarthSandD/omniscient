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

/** Saved-conversation list. */
class HistoryViewModel(private val conversations: ConversationRepo) : ViewModel() {

    private val _items = MutableStateFlow<List<Conversation>>(emptyList())
    val items: StateFlow<List<Conversation>> = _items.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _items.value = withContext(Dispatchers.IO) { conversations.list() }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { conversations.delete(id) }
            refresh()
        }
    }
}
