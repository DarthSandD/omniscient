package com.darrenai.omniscient.data.tools

import com.darrenai.omniscient.domain.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool specs + system prompt. The butler persona lives here: the model is
 * instructed to address the user as "boss" and keep spoken replies short.
 */
class ToolCatalog : ToolRegistry {

    override fun systemPrompt(facts: List<String>): String {
        val mem = if (facts.isEmpty()) "(none saved yet)" else facts.joinToString("\n") { "- $it" }
        return """
            You are Omniscient, a loyal butler in the phone — part Jarvis, part Alfred.
            Address the user as "boss". Acknowledge orders crisply ("Right away, boss").
            You have function tools: get_time, get_battery, get_location, open_app, set_alarm, set_timer,
            toggle_flashlight, toggle_wifi, toggle_bluetooth, make_call, send_sms, read_notifications,
            remember, forget, web_search.
            Rules:
            - Use tools when the user asks for a device action or something you cannot know (time, battery, apps).
            - remember: save durable user facts/preferences ("my wife's name is…", "I prefer…"). forget: remove one.
            - make_call/send_sms are irreversible: first ask the user for explicit confirmation in chat,
              then call the tool with confirm=true. Never invent confirm=true.
            - Keep replies short (1-3 sentences); the reply may be read aloud.
            - Never claim to see the screen or press on-screen buttons — you act through tools only.
            Things remembered about the user:
            $mem
        """.trimIndent()
    }

    override fun specsJson(): String = specs().toString()

    companion object {
        /** OpenAI-compatible function specs for the agent loop. */
        fun specs(): JSONArray {
            fun t(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject {
                val fn = JSONObject()
                fn.put("name", name)
                fn.put("description", desc)
                val params = JSONObject()
                params.put("type", "object")
                params.put("properties", props)
                params.put("required", JSONArray(required))
                fn.put("parameters", params)
                return JSONObject().put("type", "function").put("function", fn)
            }
            fun s(desc: String) = JSONObject().put("type", "string").put("description", desc)
            fun e(desc: String, vararg values: String) =
                JSONObject().put("type", "string").put("description", desc).put("enum", JSONArray(values.toList()))
            fun b(desc: String) = JSONObject().put("type", "boolean").put("description", desc)
            fun i(desc: String) = JSONObject().put("type", "integer").put("description", desc)
            val arr = JSONArray()
            arr.put(t("get_time", "Current date/time on the device.", JSONObject(), emptyList()))
            arr.put(t("get_battery", "Battery level and charging state.", JSONObject(), emptyList()))
            arr.put(t("get_location", "Coarse last-known device location (permission-gated).", JSONObject(), emptyList()))
            arr.put(t("open_app", "Open an installed app by name.", JSONObject().put("app", s("App name, e.g. Spotify")), listOf("app")))
            arr.put(t("set_alarm", "Set a clock alarm.", JSONObject().put("hour", i("Hour 0-23")).put("minute", i("Minute 0-59")).put("label", s("Alarm label")), listOf("hour", "minute")))
            arr.put(t("set_timer", "Set a countdown timer.", JSONObject().put("seconds", i("Duration in seconds")).put("label", s("Timer label")), listOf("seconds")))
            arr.put(t("toggle_flashlight", "Turn the flashlight on or off.", JSONObject().put("state", e("on or off", "on", "off")), listOf("state")))
            arr.put(t("toggle_wifi", "Turn Wi-Fi on/off (Android 10+ opens the settings panel instead).", JSONObject().put("state", e("on or off", "on", "off")), listOf("state")))
            arr.put(t("toggle_bluetooth", "Turn Bluetooth on/off.", JSONObject().put("state", e("on or off", "on", "off")), listOf("state")))
            arr.put(t("make_call", "Place a phone call. Requires prior user confirmation in chat + confirm=true.", JSONObject().put("number", s("Phone number")).put("confirm", b("true only after the user said yes")), listOf("number")))
            arr.put(t("send_sms", "Send an SMS. Requires prior user confirmation in chat + confirm=true.", JSONObject().put("to", s("Phone number")).put("message", s("Message text")).put("confirm", b("true only after the user said yes")), listOf("to", "message")))
            arr.put(t("read_notifications", "Read recent notifications (needs Notification access enabled).", JSONObject(), emptyList()))
            arr.put(t("remember", "Save a durable fact/preference about the user.", JSONObject().put("fact", s("Fact to remember")), listOf("fact")))
            arr.put(t("forget", "Forget a saved fact matching this text.", JSONObject().put("fact", s("Text matching the fact to forget")), listOf("fact")))
            arr.put(t("web_search", "Web search via the configured SEARCH API (disabled unless configured).", JSONObject().put("query", s("Search query")), listOf("query")))
            return arr
        }
    }
}
