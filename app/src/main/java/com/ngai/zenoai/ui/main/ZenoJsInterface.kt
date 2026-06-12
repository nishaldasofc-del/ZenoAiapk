package com.ngai.zenoai.ui.main

import android.content.Context
import android.webkit.JavascriptInterface
import com.ngai.zenoai.utils.Constants
import com.ngai.zenoai.utils.HapticUtils

class ZenoJsInterface(
    private val context: Context,
    private val onShare: (String, String) -> Unit,
    private val onGetFcmToken: () -> String?
) {

    @JavascriptInterface
    fun shareContent(text: String, title: String) {
        onShare(text, title)
    }

    @JavascriptInterface
    fun hapticFeedback(type: String) {
        when (type) {
            "light" -> HapticUtils.vibrateLight(context)
            "medium" -> HapticUtils.vibrateMedium(context)
            "heavy" -> HapticUtils.vibrateHeavy(context)
            else -> HapticUtils.vibrateLight(context)
        }
    }

    @JavascriptInterface
    fun getFcmToken(): String {
        return onGetFcmToken() ?: ""
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    @JavascriptInterface
    fun getPlatform(): String = "android"

    @JavascriptInterface
    fun isNativeApp(): Boolean = true
}
