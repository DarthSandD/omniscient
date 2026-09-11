package com.darrenai.omniscient.data.tools

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Runtime permission helper. Requests permissions only when a tool needs them,
 * never at startup. Activities must forward onRequestPermissionsResult to
 * [handleResult] — otherwise the coroutine will time out (5s) and return false.
 */
object PermissionGate {

    private val requestId = AtomicInteger(2000)
    private val pending = ConcurrentHashMap<Int, (Boolean) -> Unit>()

    /**
     * Ensures a permission is granted. Returns true if already granted or
     * successfully requested; false if denied or the activity is invalid.
     * Times out after 5 seconds to avoid leaks if the callback never fires.
     */
    suspend fun ensure(activity: AppCompatActivity, permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) return true

        val code = requestId.getAndIncrement()
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                pending[code] = { granted ->
                    if (cont.isActive) cont.resume(granted)
                }
                try {
                    activity.requestPermissions(arrayOf(permission), code)
                } catch (e: Exception) {
                    pending.remove(code)
                    if (cont.isActive) cont.resume(false)
                }
                // Safety timeout: if the callback never fires, resolve to false.
                cont.invokeOnCancellation { pending.remove(code) }
            }
        }
    }

    /**
     * Call from Activity.onRequestPermissionsResult. Returns true if the
     * request code was ours and was handled.
     */
    fun handleResult(requestCode: Int, grantResults: IntArray): Boolean {
        val cb = pending.remove(requestCode) ?: return false
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        cb(granted)
        return true
    }
}
