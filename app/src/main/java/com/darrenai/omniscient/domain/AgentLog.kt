package com.darrenai.omniscient.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-wide agent transcript. Every user turn and every tool call lands here,
 * so the terminal screen shows the agent's work live.
 * In-memory ring buffer (300 lines) — the phone, not the cloud, owns it.
 */
object AgentLog {

    private const val MAX = 300
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private fun stamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    fun add(line: String) {
        val stamped = "[${stamp()}] $line"
        _lines.update { (it + stamped).takeLast(MAX) }
    }

    fun user(text: String) = add("◈ BOSS ❯ ${text.take(200)}")

    fun reply(text: String) = add("◈ OMNISCIENT ❯ ${text.take(220)}")

    fun toolStart(name: String, args: String) = add("› $name ${args.take(160)} …")

    fun toolResult(name: String, result: String) =
        add("  ↳ $name → ${result.take(220).replace("\n", " | ")}")

    fun info(msg: String) = add("◆ $msg")

    fun clear() {
        _lines.value = emptyList()
    }
}
