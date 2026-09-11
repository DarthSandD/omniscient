package com.darrenai.omniscient.data.tools

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.darrenai.omniscient.data.SettingsStore
import com.darrenai.omniscient.data.notify.OmniscientNotificationService
import com.darrenai.omniscient.domain.MemoryRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

private suspend fun askConfirm(activity: AppCompatActivity, title: String, message: String): Boolean =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            try {
                AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Yes") { _, _ -> if (cont.isActive) cont.resume(true) }
                    .setNegativeButton("No") { _, _ -> if (cont.isActive) cont.resume(false) }
                    .setOnCancelListener { if (cont.isActive) cont.resume(false) }
                    .show()
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

/**
 * The 15 real device-action tools executed by the agent loop.
 * Every function returns a short result string. Dangerous actions
 * (call/SMS) need chat confirmation AND an on-device dialog.
 */
class DeviceTools(private val settings: SettingsStore, private val memory: MemoryRepo) {

    /** Entry point used by the agent loop: args arrive as a raw JSON string. */
    suspend fun run(activity: AppCompatActivity, name: String, argsJson: String): String {
        val args = runCatching { JSONObject(argsJson) }.getOrDefault(JSONObject())
        return runArgs(activity, name, args)
    }

    suspend fun runArgs(activity: AppCompatActivity, name: String, args: JSONObject): String =
        when (name) {
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
            return "Location permission denied, boss — I can describe places, but not locate this phone."
        }
        return try {
            val lm = activity.applicationContext.getSystemService(LocationManager::class.java)
            val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc == null) "No cached location fix (GPS off or never locked). This is coarse, may be stale."
            else "Coarse last-known location: %.4f, %.4f (accuracy ~%.0fm, age unknown).".format(
                loc.latitude, loc.longitude, loc.accuracy
            )
        } catch (e: SecurityException) {
            "Location blocked: ${e.message}"
        }
    }

    private suspend fun openApp(activity: AppCompatActivity, query: String): String {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return "No app name given, boss."
        val pm = activity.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launch, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: apps.firstOrNull { it.activityInfo.packageName.lowercase().contains(q.replace(" ", "")) }
            ?: return "No installed app matching \"$query\" found, boss."
        val pkg = match.activityInfo.packageName
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return "Found ${match.loadLabel(pm)} but it has no launch entry."
        withContext(Dispatchers.Main) { activity.startActivity(intent) }
        return "Opened ${match.loadLabel(pm)}, boss."
    }

    private suspend fun setAlarm(activity: AppCompatActivity, args: JSONObject): String {
        val hour = args.optInt("hour", -1)
        val minute = args.optInt("minute", 0)
        if (hour !in 0..23 || minute !in 0..59) return "I need an hour (0-23) and minute (0-59), boss."
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "Omniscient alarm"))
        return try {
            withContext(Dispatchers.Main) { activity.startActivity(intent) }
            "Alarm request sent for %02d:%02d — confirm it in the clock app if shown, boss.".format(hour, minute)
        } catch (e: Exception) {
            "No clock app handled the alarm request: ${e.message}"
        }
    }

    private suspend fun setTimer(activity: AppCompatActivity, args: JSONObject): String {
        val seconds = args.optInt("seconds", 0)
        if (seconds <= 0) return "I need a duration in seconds (> 0), boss."
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "Omniscient timer"))
        return try {
            withContext(Dispatchers.Main) { activity.startActivity(intent) }
            "Timer request sent for $seconds seconds — confirm it in the clock app if shown, boss."
        } catch (e: Exception) {
            "No clock app handled the timer request: ${e.message}"
        }
    }

    private suspend fun toggleFlashlight(activity: AppCompatActivity, state: String): String {
        if (!PermissionGate.ensure(activity, Manifest.permission.CAMERA)) {
            return "Camera permission denied, boss — I cannot reach the flashlight."
        }
        return try {
            val cm = activity.applicationContext.getSystemService(CameraManager::class.java)
            val id = cm?.cameraIdList?.firstOrNull() ?: return "No camera found on this device."
            val on = state.equals("on", ignoreCase = true)
            withContext(Dispatchers.Main) { cm.setTorchMode(id, on) }
            if (on) "Flashlight ON, boss." else "Flashlight OFF, boss."
        } catch (e: Exception) {
            "Flashlight failed: ${e.message}"
        }
    }

    private suspend fun toggleWifi(activity: AppCompatActivity, state: String): String {
        // Android 10+ forbids apps from toggling Wi-Fi directly — open the system panel instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                withContext(Dispatchers.Main) { activity.startActivity(Intent(Settings.Panel.ACTION_WIFI)) }
                "Android 10+ blocks apps from touching Wi-Fi directly, boss — I've opened the Wi-Fi panel for you to flip it."
            } catch (e: Exception) {
                "Could not open Wi-Fi settings: ${e.message}"
            }
        }
        return try {
            @Suppress("DEPRECATION")
            val wm = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val on = state.equals("on", ignoreCase = true)
            @Suppress("DEPRECATION")
            val ok = wm?.isWifiEnabled != on && wm?.setWifiEnabled(on) == true
            if (ok) "Wi-Fi turned ${if (on) "ON" else "OFF"}, boss." else "Wi-Fi toggle failed on this device."
        } catch (e: Exception) {
            "Wi-Fi toggle failed: ${e.message}"
        }
    }

    private suspend fun toggleBluetooth(activity: AppCompatActivity, state: String): String {
        val on = state.equals("on", ignoreCase = true)
        return try {
            val adapter = activity.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return "No Bluetooth adapter on this device, boss."
            if (on) {
                withContext(Dispatchers.Main) {
                    activity.startActivity(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                "Bluetooth enable requested, boss — confirm the system prompt if shown."
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !PermissionGate.ensure(activity, Manifest.permission.BLUETOOTH_CONNECT)
                ) return "Bluetooth permission denied, boss — I cannot switch it off."
                @Suppress("MissingPermission")
                val ok = adapter.disable()
                if (ok) "Bluetooth turning OFF, boss." else "Bluetooth disable refused by the system on this device."
            }
        } catch (e: Exception) {
            "Bluetooth toggle failed: ${e.message}"
        }
    }

    private suspend fun makeCall(activity: AppCompatActivity, args: JSONObject): String {
        val number = args.optString("number").trim()
        if (number.isEmpty()) return "No phone number given, boss."
        if (!args.optBoolean("confirm", false)) {
            return "CONFIRM_REQUIRED: placing a call to $number needs your explicit yes, boss. Ask them, then re-call with confirm=true."
        }
        if (!askConfirm(activity, "Place call?", "Call $number now?")) {
            return "Call cancelled — standing by, boss."
        }
        if (!PermissionGate.ensure(activity, Manifest.permission.CALL_PHONE)) {
            return "Call permission denied, boss — I cannot place the call."
        }
        return try {
            withContext(Dispatchers.Main) {
                activity.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
            }
            "Calling $number, boss…"
        } catch (e: Exception) {
            "Call failed: ${e.message}"
        }
    }

    private suspend fun sendSms(activity: AppCompatActivity, args: JSONObject): String {
        val to = args.optString("to").trim()
        val message = args.optString("message").trim()
        if (to.isEmpty() || message.isEmpty()) return "I need both a recipient and a message, boss."
        if (!args.optBoolean("confirm", false)) {
            return "CONFIRM_REQUIRED: sending an SMS to $to needs your explicit yes, boss. Read the text back, then re-call with confirm=true."
        }
        if (!askConfirm(activity, "Send SMS?", "To $to:\n\n$message")) {
            return "SMS cancelled — standing by, boss."
        }
        if (!PermissionGate.ensure(activity, Manifest.permission.SEND_SMS)) {
            return "SMS permission denied, boss — I cannot send it."
        }
        return try {
            @Suppress("DEPRECATION")
            SmsManager.getDefault().sendTextMessage(to, null, message, null, null)
            "SMS sent to $to, boss."
        } catch (e: Exception) {
            "SMS failed: ${e.message}"
        }
    }

    private fun readNotifications(ctx: Context): String {
        val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        if (!enabled.contains(ctx.packageName)) {
            return "Notification access is OFF, boss. To enable: Settings → Apps → Special app access → Notification access → turn on Omniscient."
        }
        val items = OmniscientNotificationService.snapshot().takeLast(10)
        if (items.isEmpty()) return "Notification access is on, but nothing captured since the service started, boss."
        return "Recent notifications:\n" + items.joinToString("\n") { "• $it" }
    }

    private fun webSearch(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "No search query given, boss."
        val base = settings.searchEndpoint.trim()
        if (base.isEmpty()) {
            return "Web search is not configured (Settings → SEARCH API is empty), boss — answering from model knowledge instead."
        }
        return try {
            val url = URL("$base?q=${URLEncoder.encode(q, "UTF-8")}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                val k = settings.searchKey.trim()
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
}
