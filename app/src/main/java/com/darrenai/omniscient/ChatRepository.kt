package com.darrenai.omniscient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface ChatResult {
    data class Ok(val text: String) : ChatResult
    data class Err(val message: String) : ChatResult
}

/** OpenAI-compatible /chat/completions client + agentic tool-calling loop (zero extra deps). */
class ChatRepository(private val prefs: Prefs) {

    private data class Round(
        val content: String?,
        val toolCalls: JSONArray?,
        val error: String?
    )

    /** Single-shot chat, no tools (kept for compatibility). */
    suspend fun complete(history: List<Message>): ChatResult = withContext(Dispatchers.IO) {
        val gate = authGate()
        if (gate != null) return@withContext gate
        val msgs = JSONArray()
        for (m in history) msgs.put(JSONObject().put("role", m.role).put("content", m.content))
        when (val r = postRound(msgs, null)) {
            null -> ChatResult.Err("Empty reply from server.")
            else -> if (r.error != null) ChatResult.Err(r.error)
            else if (r.content.isNullOrBlank()) ChatResult.Err("Empty reply from server.")
            else ChatResult.Ok(r.content.trim())
        }
    }

    /**
     * Agentic loop: sends tools, executes tool_calls via [exec], feeds results back.
     * Up to 5 model rounds. Falls back to plain chat if the model rejects tools.
     */
    suspend fun runAgent(
        history: List<Message>,
        system: String,
        exec: suspend (name: String, args: JSONObject) -> String
    ): ChatResult = withContext(Dispatchers.IO) {
        val gate = authGate()
        if (gate != null) return@withContext gate
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        for (m in history) msgs.put(JSONObject().put("role", m.role).put("content", m.content))

        var useTools: JSONArray? = AgentTools.specs()
        var rounds = 0
        var lastText: String? = null
        while (rounds < 5) {
            val r = postRound(msgs, useTools)
            if (r.error != null) {
                if (useTools != null && (r.error.contains("tool", true) || r.error.contains("function", true))) {
                    useTools = null // model/proxy without tool support → plain chat
                    continue
                }
                return@withContext ChatResult.Err(r.error)
            }
            rounds++
            val calls = r.toolCalls
            if (calls == null || calls.length() == 0) {
                val t = r.content?.trim()
                return@withContext if (t.isNullOrBlank()) ChatResult.Err("Empty reply from server.")
                else ChatResult.Ok(t)
            }
            val asst = JSONObject().put("role", "assistant")
            if (!r.content.isNullOrBlank()) asst.put("content", r.content)
            asst.put("tool_calls", calls)
            msgs.put(asst)
            for (i in 0 until calls.length()) {
                val tc = calls.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                val name = fn.optString("name")
                val args = runCatching { JSONObject(fn.optString("arguments") ?: "{}") }.getOrDefault(JSONObject())
                val id = tc.optString("id", "call_$i")
                val result = try {
                    exec(name, args)
                } catch (e: Exception) {
                    "Tool error: ${e.message ?: e.javaClass.simpleName}"
                }
                msgs.put(
                    JSONObject().put("role", "tool")
                        .put("tool_call_id", id)
                        .put("content", result.take(4000))
                )
            }
            lastText = r.content
        }
        ChatResult.Ok(lastText?.takeIf { it.isNotBlank() } ?: "Done — actions completed.")
    }

    /**
     * No-key policy: the default OpenAI endpoint needs a key (guided error);
     * a custom endpoint (e.g. Darren's LAN OmniRoute proxy) is tried without an
     * Authorization header so keyless local backends just work.
     */
    private fun authGate(): ChatResult.Err? {
        if (prefs.apiKey.isBlank() && prefs.endpoint.contains("api.openai.com")) {
            return ChatResult.Err("No API key set — open Settings and add one (or tap the OmniRoute preset for Darren's local backend).")
        }
        return null
    }

    private fun postRound(msgs: JSONArray, tools: JSONArray?): Round {
        try {
            val url = URL(prefs.endpoint + "/chat/completions")
            val body = JSONObject()
            body.put("model", prefs.model.ifBlank { Prefs.DEFAULT_MODEL })
            body.put("messages", msgs)
            body.put("temperature", 0.7)
            if (tools != null) {
                body.put("tools", tools)
                body.put("tool_choice", "auto")
            }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (prefs.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${prefs.apiKey}")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader().use { it.readText() }
            conn.disconnect()
            if (code !in 200..299) {
                val apiMsg = runCatching {
                    val o = JSONObject(text)
                    val e = o.opt("error")
                    if (e is JSONObject) e.optString("message", text) else o.optString("error", text)
                }.getOrNull() ?: "HTTP $code"
                return Round(null, null, "Server error ($code): ${apiMsg.take(300)}")
            }
            val msg = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val content = if (msg.isNull("content")) null else msg.optString("content")
            val calls = msg.optJSONArray("tool_calls")
            return Round(content, calls, null)
        } catch (e: Exception) {
            return Round(null, null, "Network error: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
