package com.darrenai.omniscient.data.notify

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.ArrayDeque

/** Captures posted notifications so the agent's read_notifications tool has something real to return. */
class OmniscientNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val line = "${sbn.packageName}: ${title.ifBlank { "(no title)" }} — ${text.ifBlank { "(no text)" }}".take(250)
        synchronized(recent) {
            recent.addLast(line)
            while (recent.size > MAX) recent.removeFirst()
        }
    }

    companion object {
        private const val MAX = 30
        private val recent: ArrayDeque<String> = ArrayDeque()

        fun snapshot(): List<String> = synchronized(recent) { recent.toList() }
    }
}
