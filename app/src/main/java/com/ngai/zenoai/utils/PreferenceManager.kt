package com.ngai.zenoai.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)

    var lastVisitedUrl: String
        get() = prefs.getString(Constants.PREF_LAST_URL, Constants.BASE_URL) ?: Constants.BASE_URL
        set(value) = prefs.edit { putString(Constants.PREF_LAST_URL, value) }

    var fcmToken: String?
        get() = prefs.getString(Constants.PREF_FCM_TOKEN, null)
        set(value) = prefs.edit { putString(Constants.PREF_FCM_TOKEN, value) }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(Constants.PREF_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit { putBoolean(Constants.PREF_NOTIFICATIONS_ENABLED, value) }

    fun clearSession() {
        prefs.edit {
            remove(Constants.PREF_LAST_URL)
        }
    }
}
