package com.bountyradar.app.messaging

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bountyradar.app.MainActivity
import com.bountyradar.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives pushes from the poller. The poller sends BOTH a `notification` block
 * (so it shows even when the app is killed) and a `data` block (doc_id, url) so a
 * tap can deep-link straight to the program / target.
 */
class RadarMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "New bug bounty program"
        val body = message.notification?.body ?: data["body"].orEmpty()
        val url = data["url"].orEmpty()
        val docId = data["doc_id"].orEmpty()

        showNotification(title, body, url, docId)
    }

    private fun showNotification(title: String, body: String, url: String, docId: String) {
        // Tapping opens the app on the program; long links open the target directly.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("doc_id", docId)
            putExtra("url", url)
            if (url.isNotEmpty()) data = Uri.parse(url)
        }
        val pending = PendingIntent.getActivity(
            this, docId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, getString(R.string.default_channel_id))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(docId.hashCode().and(0xFFFFFF), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted yet — the in-app list still updates.
        }
    }

    override fun onNewToken(token: String) {
        // Topic-based delivery means we don't need to upload this token anywhere.
    }
}
