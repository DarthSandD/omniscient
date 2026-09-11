package com.darrenai.omniscient.data

import com.darrenai.omniscient.domain.ChatService
import com.darrenai.omniscient.domain.LlmRound
import com.darrenai.omniscient.domain.ToolCall
import com.darrenai.omniscient.domain.WireMsg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible /chat/completions transport. One round per call —
 * the loop lives in SendMessageUseCase. Zero extra deps.
 */
class OpenAiService(private val settings: SettingsStore) : ChatService {

    override fun authError(): String? {
        if (settings.apiKey.isBlank() && settings.endpoint.contains("api.openai.com")) {
            return "No API key set — open Settings and add one (or tap the OmniRoute preset for the local backend)."
        }
        return null
    }

    override suspend fun post(transcript: List<WireMsg>, toolsJson: String?): LlmRound =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                body.put("model", settings.model.ifBlank { SettingsStore.DEFAULT_MODEL })
                body.put("messages", toJson(transcript))
                body.put("temperature", 0.7)
                if (toolsJson != null) {
                    body.put("tools", JSONArray(toolsJson))
                    body.put("tool_choice", "auto")
                }
                val url = URL(settings.endpoint + "/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (settings.apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                    }
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
                    return@withContext LlmRound(null, emptyList(), "Server error ($code): ${apiMsg.take(300)}")
                }
                val msg = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val content = if (msg.isNull("content")) null else msg.optString("content")
                val calls = mutableListOf<ToolCall>()
                val arr = msg.optJSONArray("tool_calls")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val tc = arr.getJSONObject(i)
                        val fn = tc.getJSONObject("function")
                        calls.add(
                            ToolCall(
                                id = tc.optString("id", "call_$i"),
                                name = fn.optString("name"),
                                argsJson = fn.optString("arguments") ?: "{}"
                            )
                        )
                    }
                }
                LlmRound(content, calls, null)
            } catch (e: Exception) {
                LlmRound(null, emptyList(), "Network error: ${e.message ?: e.javaClass.simpleName}")
            }
        }

    private fun toJson(transcript: List<WireMsg>): JSONArray {
        val out = JSONArray()
        for (m in transcript) {
            val o = JSONObject().put("role", m.role)
            if (m.content != null) o.put("content", m.content)
            if (m.toolCalls.isNotEmpty()) {
                val arr = JSONArray()
                for (c in m.toolCalls) {
                    arr.put(
                        JSONObject()
                            .put("id", c.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject().put("name", c.name).put("arguments", c.argsJson)
                            )
                    )
                }
                o.put("tool_calls", arr)
            }
            if (m.toolCallId != null) o.put("tool_call_id", m.toolCallId)
            out.put(o)
        }
        return out
    }
}
