package com.darrenai.omniscient

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Message(val role: String, val content: String)

data class Conversation(
    val id: Long,
    val title: String,
    val messages: MutableList<Message>,
    val updatedAt: Long
)

/** Minimal JSON-file conversation store (internal storage, no database needed). */
class ConversationStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "conversations").apply { mkdirs() }

    fun list(): List<Conversation> =
        dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { runCatching { read(it) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    fun load(id: Long): Conversation? {
        val f = File(dir, "$id.json")
        return if (f.exists()) runCatching { read(f) }.getOrNull() else null
    }

    fun save(conv: Conversation): Conversation {
        val updated = conv.copy(updatedAt = System.currentTimeMillis())
        val o = JSONObject()
        o.put("id", updated.id)
        o.put("title", updated.title)
        o.put("updatedAt", updated.updatedAt)
        val arr = JSONArray()
        for (m in updated.messages) {
            val mo = JSONObject()
            mo.put("role", m.role)
            mo.put("content", m.content)
            arr.put(mo)
        }
        o.put("messages", arr)
        File(dir, "${updated.id}.json").writeText(o.toString())
        return updated
    }

    fun newConversation(): Conversation =
        Conversation(System.currentTimeMillis(), "New conversation", mutableListOf(), System.currentTimeMillis())

    fun delete(id: Long) {
        File(dir, "$id.json").delete()
    }

    private fun read(f: File): Conversation {
        val o = JSONObject(f.readText())
        val msgs = mutableListOf<Message>()
        val arr = o.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val mo = arr.getJSONObject(i)
            msgs.add(Message(mo.optString("role", "user"), mo.optString("content", "")))
        }
        val firstUser = msgs.firstOrNull { it.role == "user" }?.content
        return Conversation(
            o.optLong("id", f.nameWithoutExtension.toLongOrNull() ?: 0L),
            o.optString("title", firstUser?.take(40) ?: "Conversation"),
            msgs,
            o.optLong("updatedAt", f.lastModified())
        )
    }

    companion object {
        fun titleFor(messages: List<Message>): String =
            messages.firstOrNull { it.role == "user" }?.content
                ?.replace("\n", " ")?.trim()?.take(42)?.ifBlank { null }
                ?: "Conversation"
    }
}
