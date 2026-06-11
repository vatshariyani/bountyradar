package com.bountyradar.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

/**
 * App entry. Two jobs at startup:
 *  1. Create the high-importance notification channel (Android 8+ requires it).
 *  2. Subscribe to the FCM topic the poller publishes to — this is why we never
 *     have to register/store device tokens server-side.
 */
class BountyRadarApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        Firebase.messaging.subscribeToTopic(TOPIC_NEW_PROGRAMS)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.default_channel_id),
                getString(R.string.default_channel_name),
                NotificationManager.IMPORTANCE_HIGH, // pops as a heads-up: "act now"
            ).apply { description = "Alerts when a new bug bounty program launches" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val TOPIC_NEW_PROGRAMS = "new_programs"
    }
}
