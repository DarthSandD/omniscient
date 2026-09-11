package com.darrenai.omniscient.data

import android.content.Context
import com.darrenai.omniscient.domain.MemoryRepo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local long-term memory: facts/preferences as JSON, injected into the system prompt. */
class FileMemory(context: Context) : MemoryRepo {
    private val file = File(context.applicationContext.filesDir, "memory.json")

    override fun facts(): List<String> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONObject(file.readText()).optJSONArray("facts") ?: return emptyList()
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    override fun remember(fact: String): String {
        val clean = fact.trim().take(300)
        if (clean.isEmpty()) return "Nothing to remember, boss."
        val all = facts().toMutableList()
        if (all.any { it.equals(clean, ignoreCase = true) }) return "Already remembered, boss: \"$clean\""
        all.add(clean)
        save(all)
        return "Committed to memory, boss: \"$clean\""
    }

    override fun forget(fact: String): String {
        val q = fact.trim()
        if (q.isEmpty()) return "Say what to forget, boss."
        val all = facts().toMutableList()
        val hit = all.firstOrNull { it.contains(q, ignoreCase = true) }
        if (hit == null) return "No saved memory matching \"$q\", boss."
        all.remove(hit)
        save(all)
        return "Struck from the records, boss: \"$hit\""
    }

    private fun save(all: List<String>) {
        file.writeText(JSONObject().put("facts", JSONArray(all)).toString())
    }
}
