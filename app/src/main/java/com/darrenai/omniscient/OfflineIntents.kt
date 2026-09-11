package com.darrenai.omniscient

import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * Offline rule-based intents: flashlight, time/date, battery, open-app, memory.
 * Runs fully on-device with no API key and no network — the app stays useful
 * on any phone before setup, and when the backend is unreachable.
 * Returns the reply text, or null when nothing matched (caller falls through to the LLM agent).
 */
object OfflineIntents {

    suspend fun tryHandle(activity: AppCompatActivity, tools: AgentTools, text: String): String? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null

        // flashlight: "flashlight on", "turn off the torch", …
        val flash = Regex("""(flashlight|torch).*?\b(on|off)\b|\b(on|off).*?(flashlight|torch)""").find(t)
        if (flash != null) {
            val on = flash.groupValues.any { it == "on" }
            return tools.run(activity, "toggle_flashlight", JSONObject().put("state", if (on) "on" else "off"))
        }
        // time/date: "what time is it", "current time", "today's date", "what day is it"
        if (Regex("""\btime\b.*\?|what.*time|current time|the time\b|\bdate\b|what day|today'?s date""").containsMatchIn(t)) {
            return tools.run(activity, "get_time", JSONObject())
        }
        // battery: "battery?", "battery status/level"
        if (t.contains("battery")) {
            return tools.run(activity, "get_battery", JSONObject())
        }
        // open app: "open spotify", "launch calculator"
        val open = Regex("""^(?:open|launch|start)\s+(.+)$""").find(t)
        if (open != null) {
            val app = open.groupValues[1].trim().removeSuffix("app").trim()
            if (app.isNotEmpty()) return tools.run(activity, "open_app", JSONObject().put("app", app))
        }
        // memory is local too: "remember …" / "forget …"
        val remember = Regex("""^remember\s+(.+)$""").find(text.trim())
        if (remember != null) {
            return tools.run(activity, "remember", JSONObject().put("fact", remember.groupValues[1].trim()))
        }
        val forget = Regex("""^forget\s+(.+)$""").find(text.trim())
        if (forget != null) {
            return tools.run(activity, "forget", JSONObject().put("fact", forget.groupValues[1].trim()))
        }
        return null
    }
}
