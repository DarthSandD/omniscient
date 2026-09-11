package com.darrenai.omniscient

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local long-term memory: user facts/preferences persisted as JSON, injected into the system prompt. */
class MemoryStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "memory.json")

    fun facts(): List<String> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONObject(file.readText()).optJSONArray("facts") ?: return emptyList()
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    fun remember(fact: String): String {
        val clean = fact.trim().take(300)
        if (clean.isEmpty()) return "Nothing to remember."
        val all = facts().toMutableList()
        if (all.any { it.equals(clean, ignoreCase = true) }) return "Already remembered: \"$clean\""
        all.add(clean)
        save(all)
        return "Remembered: \"$clean\""
    }

    fun forget(fact: String): String {
        val q = fact.trim()
        if (q.isEmpty()) return "Say what to forget."
        val all = facts().toMutableList()
        val hit = all.firstOrNull { it.contains(q, ignoreCase = true) }
        if (hit == null) return "No saved memory matching \"$q\"."
        all.remove(hit)
        save(all)
        return "Forgot: \"$hit\""
    }

    private fun save(all: List<String>) {
        val o = JSONObject()
        o.put("facts", JSONArray(all))
        file.writeText(o.toString())
    }
}
