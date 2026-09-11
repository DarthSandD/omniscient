package com.darrenai.omniscient.data

import android.content.Context
import com.darrenai.omniscient.domain.Conversation
import com.darrenai.omniscient.domain.ConversationRepo
import com.darrenai.omniscient.domain.Message
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** JSON-file conversation store (internal storage, no database needed). */
class FileConversations(context: Context) : ConversationRepo {
    private val dir = File(context.applicationContext.filesDir, "conversations").apply { mkdirs() }

    override fun list(): List<Conversation> =
        dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { runCatching { read(it) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

    override fun load(id: Long): Conversation? {
        val f = File(dir, "$id.json")
        return if (f.exists()) runCatching { read(f) }.getOrNull() else null
    }

    override fun save(conv: Conversation): Conversation {
        val updated = conv.copy(updatedAt = System.currentTimeMillis())
        val o = JSONObject()
        o.put("id", updated.id)
        o.put("title", updated.title)
        o.put("updatedAt", updated.updatedAt)
        val arr = JSONArray()
        for (m in updated.messages) {
            arr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        o.put("messages", arr)
        File(dir, "${updated.id}.json").writeText(o.toString())
        return updated
    }

    override fun new(): Conversation =
        Conversation(System.currentTimeMillis(), "New conversation", mutableListOf(), System.currentTimeMillis())

    override fun delete(id: Long) {
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
}
