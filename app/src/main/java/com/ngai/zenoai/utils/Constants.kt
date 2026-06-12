package com.ngai.zenoai.utils

object Constants {
    // App URL
    const val BASE_URL = "https://zenoai-bot.vercel.app/"

    // Notification channels
    const val CHANNEL_GENERAL = "zenoai_general"
    const val CHANNEL_ALERTS = "zenoai_alerts"

    // Shared preferences
    const val PREF_NAME = "zenoai_prefs"
    const val PREF_LAST_URL = "last_url"
    const val PREF_FCM_TOKEN = "fcm_token"
    const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"

    // WebView
    const val WEBVIEW_STATE_KEY = "webview_state"
    const val JS_INTERFACE_NAME = "ZenoAndroid"

    // Request codes
    const val REQUEST_CAMERA = 1001
    const val REQUEST_LOCATION = 1002
    const val REQUEST_STORAGE = 1003
    const val REQUEST_NOTIFICATION = 1004
    const val REQUEST_FILE_CHOOSER = 1005
    const val REQUEST_CAPTURE_IMAGE = 1006
    const val REQUEST_MICROPHONE = 1007

    // Intent extras
    const val EXTRA_URL = "extra_url"
    const val EXTRA_NOTIFICATION_TYPE = "notification_type"

    // Timeouts
    const val CONNECTION_TIMEOUT_MS = 10_000L
    const val SPLASH_DELAY_MS = 2000L

    // Cache
    const val CACHE_SIZE_MB = 50L
    const val CACHE_SIZE_BYTES = CACHE_SIZE_MB * 1024 * 1024
}
