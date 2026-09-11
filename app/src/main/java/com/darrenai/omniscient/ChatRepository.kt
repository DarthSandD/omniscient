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

/** OpenAI-compatible /chat/completions client over HttpURLConnection (zero extra deps). */
class ChatRepository(private val prefs: Prefs) {

    suspend fun complete(history: List<Message>): ChatResult = withContext(Dispatchers.IO) {
        val key = prefs.apiKey
        if (key.isBlank()) {
            return@withContext ChatResult.Err("No API key set — open Settings and add one.")
        }
        try {
            val url = URL(prefs.endpoint + "/chat/completions")
            val body = JSONObject()
            body.put("model", prefs.model.ifBlank { Prefs.DEFAULT_MODEL })
            val arr = JSONArray()
            for (m in history) {
                val o = JSONObject()
                o.put("role", m.role)
                o.put("content", m.content)
                arr.put(o)
            }
            body.put("messages", arr)
            body.put("temperature", 0.7)

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader().use { it.readText() }
            conn.disconnect()
            if (code !in 200..299) {
                val apiMsg = runCatching {
                    JSONObject(text).optString("error", text).take(300)
                }.getOrNull() ?: "HTTP $code"
                return@withContext ChatResult.Err("Server error ($code): $apiMsg")
            }
            val content = runCatching {
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }.getOrNull()?.trim()
            if (content.isNullOrBlank()) ChatResult.Err("Empty reply from server.")
            else ChatResult.Ok(content)
        } catch (e: Exception) {
            ChatResult.Err("Network error: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
