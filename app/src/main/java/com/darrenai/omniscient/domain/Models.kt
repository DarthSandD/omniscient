package com.darrenai.omniscient.domain

/** Core models: UI, data, and transport types all map through these. */

data class Message(val role: String, val content: String)

data class Conversation(
    val id: Long,
    var title: String,
    val messages: MutableList<Message>,
    var updatedAt: Long
)

/** One function call requested by the model. argsJson is the raw arguments object. */
data class ToolCall(val id: String, val name: String, val argsJson: String)

/**
 * One transcript entry for the wire. Assistant entries may carry tool calls;
 * tool entries carry the result of one call (toolCallId links them).
 */
data class WireMsg(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null
)

/** One backend round: either text, tool calls, or an error. */
data class LlmRound(val content: String?, val calls: List<ToolCall>, val error: String?)

/** Final outcome of one user turn. */
data class TurnResult(val text: String, val toolCalls: Int, val offline: Boolean)

fun titleFor(messages: List<Message>): String =
    messages.firstOrNull { it.role == "user" }?.content
        ?.replace("\n", " ")?.trim()?.take(42)?.ifBlank { null }
        ?: "Conversation"
