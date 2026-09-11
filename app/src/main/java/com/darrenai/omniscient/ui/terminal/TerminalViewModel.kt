package com.darrenai.omniscient.ui.terminal

import androidx.lifecycle.ViewModel
import com.darrenai.omniscient.domain.AgentLog
import kotlinx.coroutines.flow.StateFlow

/** Command-terminal screen: live view of the agent's tool calls. */
class TerminalViewModel(private val log: AgentLog = AgentLog) : ViewModel() {
    val lines: StateFlow<List<String>> = log.lines
    fun clear() = log.clear()
}
