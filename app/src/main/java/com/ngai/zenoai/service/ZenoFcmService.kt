package com.ngai.zenoai.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ngai.zenoai.R
import com.ngai.zenoai.ui.main.MainActivity
import com.ngai.zenoai.utils.Constants
import com.ngai.zenoai.utils.PreferenceManager

class ZenoFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PreferenceManager(applicationContext).fcmToken = token
        // TODO: send token to your backend
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val prefManager = PreferenceManager(applicationContext)
        if (!prefManager.notificationsEnabled) return

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: getString(R.string.app_name)

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: return

        val deepLinkUrl = remoteMessage.data["url"]
        val notificationType = remoteMessage.data["type"] ?: "general"

        sendNotification(title, body, deepLinkUrl, notificationType)
    }

    private fun sendNotification(
        title: String,
        messageBody: String,
        deepLinkUrl: String?,
        notificationType: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (deepLinkUrl != null) {
                putExtra(Constants.EXTRA_URL, deepLinkUrl)
                data = android.net.Uri.parse(deepLinkUrl)
            }
            putExtra(Constants.EXTRA_NOTIFICATION_TYPE, notificationType)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = when (notificationType) {
            "alert", "urgent" -> Constants.CHANNEL_ALERTS
            else -> Constants.CHANNEL_GENERAL
        }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .setContentIntent(pendingIntent)
            .setPriority(
                if (channelId == Constants.CHANNEL_ALERTS)
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setColor(getColor(R.color.primary))
            .setColorized(false)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notificationBuilder.build()
        )
    }
}
