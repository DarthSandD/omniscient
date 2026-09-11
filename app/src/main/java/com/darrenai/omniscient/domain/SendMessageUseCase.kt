package com.darrenai.omniscient.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One user turn, orchestrated: offline intents first (no key, no network),
 * then the agentic tool-calling loop (max 5 model rounds). Every step is
 * logged to [AgentLog] so the terminal screen shows the work live.
 * Butler persona wraps action replies and failures.
 */
class SendMessageUseCase(
    private val chat: ChatService,
    private val registry: ToolRegistry,
    private val log: AgentLog = AgentLog
) {
    suspend fun run(
        history: List<Message>,
        facts: List<String>,
        offline: (suspend () -> String?)? = null,
        exec: (suspend (name: String, argsJson: String) -> String)? = null
    ): TurnResult = withContext(Dispatchers.IO) {
        history.lastOrNull { it.role == "user" }?.let { log.user(it.content) }

        // 1. Offline intents: fully on-device, work before any setup.
        if (offline != null) {
            val out = try {
                offline()
            } catch (e: Exception) {
                "Local action failed: ${e.message ?: e.javaClass.simpleName}"
            }
            if (out != null) {
                val wrapped = Persona.announce(out)
                log.reply(wrapped)
                return@withContext TurnResult(wrapped, 0, true)
            }
        }

        val runTool = exec
            ?: return@withContext TurnResult(Persona.excuse("my tools are offline."), 0, false)

        // 2. No-key gate: default cloud endpoint needs a key, local ones don't.
        val gate = chat.authError()
        if (gate != null) {
            log.info("auth gate: $gate")
            return@withContext TurnResult(Persona.excuse(gate), 0, false)
        }

        // 3. Agent loop over a structured transcript (no org.json in domain).
        val transcript = mutableListOf(WireMsg("system", registry.systemPrompt(facts)))
        for (m in history) transcript.add(WireMsg(m.role, m.content))

        var toolsJson: String? = registry.specsJson()
        var rounds = 0
        var toolsUsed = 0
        var lastText: String? = null
        while (rounds < 5) {
            val r = chat.post(transcript, toolsJson)
            if (r.error != null) {
                // Backend without tool support → retry once as plain chat.
                if (toolsJson != null && (r.error.contains("tool", true) || r.error.contains("function", true))) {
                    toolsJson = null
                    continue
                }
                log.info("backend error: ${r.error}")
                return@withContext TurnResult(Persona.excuse(r.error), toolsUsed, false)
            }
            rounds++
            if (r.calls.isEmpty()) {
                val t = r.content?.trim()
                if (t.isNullOrBlank()) {
                    return@withContext TurnResult(
                        Persona.excuse("the uplink returned an empty reply."),
                        toolsUsed, false
                    )
                }
                val wrapped = if (toolsUsed > 0) Persona.announce(t) else t
                log.reply(wrapped)
                return@withContext TurnResult(wrapped, toolsUsed, false)
            }
            log.add("◈ plan: ${r.calls.size} tool call(s)")
            transcript.add(WireMsg("assistant", r.content, r.calls))
            for (c in r.calls) {
                toolsUsed++
                log.toolStart(c.name, c.argsJson)
                val result = try {
                    runTool(c.name, c.argsJson)
                } catch (e: Exception) {
                    "Tool error: ${e.message ?: e.javaClass.simpleName}"
                }
                log.toolResult(c.name, result)
                transcript.add(WireMsg("tool", result.take(4000), toolCallId = c.id))
            }
            lastText = r.content
        }
        val done = lastText?.takeIf { it.isNotBlank() } ?: "Done — actions completed."
        val wrapped = Persona.announce(done)
        log.reply(wrapped)
        TurnResult(wrapped, toolsUsed, false)
    }
}
