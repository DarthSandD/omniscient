package com.darrenai.omniscient.ui.terminal

import androidx.lifecycle.ViewModel
import com.darrenai.omniscient.domain.AgentLog
import kotlinx.coroutines.flow.StateFlow

class TerminalViewModel : ViewModel() {
    val lines: StateFlow<List<String>> = AgentLog.lines
}
