package com.darrenai.omniscient

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/** Runtime-permission bridge: activities forward onRequestPermissionsResult here. */
object PermissionGate {
    private var next = 400
    private val pending = mutableMapOf<Int, (Boolean) -> Unit>()

    suspend fun ensure(activity: Activity, perm: String): Boolean {
        if (ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED) return true
        return suspendCancellableCoroutine { cont ->
            val code = next++
            pending[code] = { granted -> if (cont.isActive) cont.resume(granted) }
            ActivityCompat.requestPermissions(activity, arrayOf(perm), code)
        }
    }

    /** Returns true if the code belonged to us. Call from every activity's onRequestPermissionsResult. */
    fun onResult(code: Int, granted: Boolean): Boolean {
        val cb = pending.remove(code) ?: return false
        cb(granted)
        return true
    }
}

private suspend fun askConfirm(activity: Activity, title: String, message: String): Boolean =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Yes") { _, _ -> if (cont.isActive) cont.resume(true) }
                .setNegativeButton("No") { _, _ -> if (cont.isActive) cont.resume(false) }
                .setOnCancelListener { if (cont.isActive) cont.resume(false) }
                .show()
        }
    }

/** Real device-action tools executed by the agent loop. Every function returns a short result string. */
class AgentTools(private val prefs: Prefs, private val memory: MemoryStore) {

    suspend fun run(activity: AppCompatActivity, name: String, args: JSONObject): String = when (name) {
        "get_time" -> getTime()
        "get_battery" -> getBattery(activity)
        "get_location" -> getLocation(activity)
        "open_app" -> openApp(activity, args.optString("app"))
        "set_alarm" -> setAlarm(activity, args)
        "set_timer" -> setTimer(activity, args)
        "toggle_flashlight" -> toggleFlashlight(activity, args.optString("state"))
        "toggle_wifi" -> toggleWifi(activity, args.optString("state"))
        "toggle_bluetooth" -> toggleBluetooth(activity, args.optString("state"))
        "make_call" -> makeCall(activity, args)
        "send_sms" -> sendSms(activity, args)
        "read_notifications" -> readNotifications(activity)
        "remember" -> memory.remember(args.optString("fact"))
        "forget" -> memory.forget(args.optString("fact"))
        "web_search" -> webSearch(args.optString("query"))
        else -> "Unknown tool: $name"
    }

    private fun getTime(): String {
        val fmt = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        return "Device time: ${fmt.format(Date())}"
    }

    private fun getBattery(ctx: Context): String {
        val bm = ctx.applicationContext.getSystemService(BatteryManager::class.java)
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val intent = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val charging = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val state = if (charging != 0) "charging" else "on battery"
        return if (pct >= 0) "Battery: $pct% ($state)." else "Battery level unavailable."
    }

    private suspend fun getLocation(activity: AppCompatActivity): String {
        if (!PermissionGate.ensure(activity, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return "Location permission denied — I can only describe places, not locate this phone."
        }
        return try {
            val lm = activity.applicationContext.getSystemService(LocationManager::class.java)
            val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc == null) "No cached location fix (GPS off or never locked). This is coarse, may be stale."
            else "Coarse last-known location: %.4f, %.4f (accuracy ~%.0fm, age unknown).".format(loc.latitude, loc.longitude, loc.accuracy)
        } catch (e: SecurityException) {
            "Location blocked: ${e.message}"
        }
    }

    private suspend fun openApp(activity: AppCompatActivity, query: String): String {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return "No app name given."
        val pm = activity.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launch, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: apps.firstOrNull { it.activityInfo.packageName.lowercase().contains(q.replace(" ", "")) }
            ?: return "No installed app matching \"$query\" found."
        val pkg = match.activityInfo.packageName
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return "Found ${match.loadLabel(pm)} but it has no launch entry."
        withContext(Dispatchers.Main) { activity.startActivity(intent) }
        return "Opened ${match.loadLabel(pm)}."
    }

    private suspend fun setAlarm(activity: AppCompatActivity, args: JSONObject): String {
        val hour = args.optInt("hour", -1)
        val minute = args.optInt("minute", 0)
        if (hour !in 0..23 || minute !in 0..59) return "Need hour (0-23) and minute (0-59)."
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "Omniscient alarm"))
        return try {
            withContext(Dispatchers.Main) { activity.startActivity(intent) }
            "Alarm request sent for %02d:%02d — confirm it in the clock app if shown.".format(hour, minute)
        } catch (e: Exception) {
            "No clock app handled the alarm request: ${e.message}"
        }
    }

    private suspend fun setTimer(activity: AppCompatActivity, args: JSONObject): String {
        val seconds = args.optInt("seconds", 0)
        if (seconds <= 0) return "Need seconds (> 0)."
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "Omniscient timer"))
        return try {
            withContext(Dispatchers.Main) { activity.startActivity(intent) }
            "Timer request sent for $seconds seconds — confirm it in the clock app if shown."
        } catch (e: Exception) {
            "No clock app handled the timer request: ${e.message}"
        }
    }

    private suspend fun toggleFlashlight(activity: AppCompatActivity, state: String): String {
        if (!PermissionGate.ensure(activity, Manifest.permission.CAMERA)) {
            return "Camera permission denied — cannot control the flashlight."
        }
        return try {
            val cm = activity.applicationContext.getSystemService(CameraManager::class.java)
            val id = cm?.cameraIdList?.firstOrNull() ?: return "No camera found."
            val on = state.equals("on", ignoreCase = true)
            withContext(Dispatchers.Main) { cm.setTorchMode(id, on) }
            if (on) "Flashlight ON." else "Flashlight OFF."
        } catch (e: Exception) {
            "Flashlight failed: ${e.message}"
        }
    }

    private suspend fun toggleWifi(activity: AppCompatActivity, state: String): String {
        // Android 10+ forbids apps from toggling Wi-Fi directly — open the system panel instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                withContext(Dispatchers.Main) { activity.startActivity(Intent(Settings.Panel.ACTION_WIFI)) }
                "Android 10+ blocks apps from toggling Wi-Fi directly — opened the Wi-Fi settings panel for you to flip it."
            } catch (e: Exception) {
                "Could not open Wi-Fi settings: ${e.message}"
            }
        }
        return try {
            @Suppress("DEPRECATION")
            val wm = activity.applicationContext.getSystemService(WifiManager::class.java)
            val on = state.equals("on", ignoreCase = true)
            @Suppress("DEPRECATION")
            val ok = wm?.setWifiEnabled(on) ?: false
            if (ok) "Wi-Fi turned ${if (on) "ON" else "OFF"}." else "Wi-Fi toggle failed on this device."
        } catch (e: Exception) {
            "Wi-Fi toggle failed: ${e.message}"
        }
    }

    private suspend fun toggleBluetooth(activity: AppCompatActivity, state: String): String {
        val on = state.equals("on", ignoreCase = true)
        return try {
            val adapter = activity.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return "No Bluetooth adapter on this device."
            if (on) {
                withContext(Dispatchers.Main) {
                    activity.startActivity(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                "Bluetooth enable requested — confirm the system prompt if shown."
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !PermissionGate.ensure(activity, Manifest.permission.BLUETOOTH_CONNECT)
                ) return "Bluetooth permission denied — cannot disable Bluetooth."
                @Suppress("MissingPermission")
                val ok = adapter.disable()
                if (ok) "Bluetooth turning OFF." else "Bluetooth disable refused by the system on this device."
            }
        } catch (e: Exception) {
            "Bluetooth toggle failed: ${e.message}"
        }
    }

    private suspend fun makeCall(activity: AppCompatActivity, args: JSONObject): String {
        val number = args.optString("number").trim()
        if (number.isEmpty()) return "No phone number given."
        if (!args.optBoolean("confirm", false)) {
            return "CONFIRM_REQUIRED: placing a call to $number needs the user's explicit yes. Ask them, then re-call with confirm=true."
        }
        if (!askConfirm(activity, "Place call?", "Call $number now?")) return "Call cancelled by user."
        if (!PermissionGate.ensure(activity, Manifest.permission.CALL_PHONE)) {
            return "Call permission denied — cannot place the call."
        }
        return try {
            withContext(Dispatchers.Main) { activity.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))) }
            "Calling $number…"
        } catch (e: Exception) {
            "Call failed: ${e.message}"
        }
    }

    private suspend fun sendSms(activity: AppCompatActivity, args: JSONObject): String {
        val to = args.optString("to").trim()
        val message = args.optString("message").trim()
        if (to.isEmpty() || message.isEmpty()) return "Need both 'to' and 'message'."
        if (!args.optBoolean("confirm", false)) {
            return "CONFIRM_REQUIRED: sending an SMS to $to needs the user's explicit yes. Read the text back, then re-call with confirm=true."
        }
        if (!askConfirm(activity, "Send SMS?", "To $to:\n\n$message")) return "SMS cancelled by user."
        if (!PermissionGate.ensure(activity, Manifest.permission.SEND_SMS)) {
            return "SMS permission denied — cannot send."
        }
        return try {
            @Suppress("DEPRECATION")
            SmsManager.getDefault().sendTextMessage(to, null, message, null, null)
            "SMS sent to $to."
        } catch (e: Exception) {
            "SMS failed: ${e.message}"
        }
    }

    private fun readNotifications(ctx: Context): String {
        val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        if (!enabled.contains(ctx.packageName)) {
            return "Notification access is OFF. To enable: Settings → Apps → Special app access → Notification access → turn on Omniscient. " +
                "Return here and ask again after enabling."
        }
        val items = OmniscientNotificationService.snapshot().takeLast(10)
        if (items.isEmpty()) return "Notification access is on, but nothing captured since the service started."
        return "Recent notifications:\n" + items.joinToString("\n") { "• $it" }
    }

    private fun webSearch(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "No search query given."
        val base = prefs.searchEndpoint.trim()
        if (base.isEmpty()) {
            return "Web search is not configured (Settings → SEARCH API is empty) — answering from model knowledge instead."
        }
        return try {
            val url = URL("$base?q=${URLEncoder.encode(q, "UTF-8")}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                val k = prefs.searchKey.trim()
                if (k.isNotEmpty()) setRequestProperty("Authorization", "Bearer $k")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader().use { it.readText() }
            conn.disconnect()
            if (code !in 200..299) "Search API error ($code): ${text.take(300)}"
            else "Search results for \"$q\":\n${text.take(1500)}"
        } catch (e: Exception) {
            "Search failed: ${e.message}"
        }
    }

    companion object {
        fun systemPrompt(facts: List<String>): String {
            val mem = if (facts.isEmpty()) "(none saved yet)" else facts.joinToString("\n") { "- $it" }
            return """
                You are Omniscient, a voice-first on-device assistant with real phone control.
                You have function tools: get_time, get_battery, get_location, open_app, set_alarm, set_timer,
                toggle_flashlight, toggle_wifi, toggle_bluetooth, make_call, send_sms, read_notifications,
                remember, forget, web_search.
                Rules:
                - Use tools when the user asks for a device action or something you cannot know (time, battery, apps).
                - remember: save durable user facts/preferences ("my wife's name is…", "I prefer…"). forget: remove one.
                - make_call/send_sms are irreversible: first ask the user for explicit confirmation in chat,
                  then call the tool with confirm=true. Never invent confirm=true.
                - Keep spoken replies short (1-3 sentences); the reply is read aloud.
                Things remembered about the user:
                $mem
            """.trimIndent()
        }

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
