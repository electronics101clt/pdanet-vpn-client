package com.pdanetclient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("BootReceiver", "Boot completed — starting PdaNet monitor service")
        // Start monitor service which will watch for PdaNet WiFi and spawn VPN service
        PdaNetMonitorService.start(context)
    }
}
