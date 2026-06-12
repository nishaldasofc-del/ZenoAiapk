package com.ngai.zenoai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Legacy network receiver kept for compatibility.
 * Actual network monitoring is handled via NetworkLiveData in the ViewModel.
 */
class NetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handled by NetworkLiveData
    }
}
