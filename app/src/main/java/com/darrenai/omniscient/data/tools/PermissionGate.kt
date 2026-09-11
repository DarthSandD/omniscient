package com.darrenai.omniscient.data.tools

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Runtime-permission bridge. Activities forward onRequestPermissionsResult here.
 * Main-safe: the request itself is always issued on the UI thread, so tools
 * may call [ensure] from any dispatcher.
 */
object PermissionGate {
    private var next = 400
    private val pending = mutableMapOf<Int, (Boolean) -> Unit>()

    suspend fun ensure(activity: Activity, perm: String): Boolean {
        if (ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED) return true
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val code = next++
                pending[code] = { granted -> if (cont.isActive) cont.resume(granted) }
                ActivityCompat.requestPermissions(activity, arrayOf(perm), code)
            }
        }
    }

    /** Returns true if the code belonged to us. Call from every activity's onRequestPermissionsResult. */
    fun onResult(code: Int, granted: Boolean): Boolean {
        val cb = pending.remove(code) ?: return false
        cb(granted)
        return true
    }
}
