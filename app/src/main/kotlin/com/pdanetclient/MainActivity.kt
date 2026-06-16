package com.pdanetclient

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings

// Launcher activity — no UI. Just ensures monitor service is running and VPN permission
// is granted. If permission is needed the system dialog appears immediately.
// Finishes as soon as it's done.
class MainActivity : Activity() {

    companion object {
        private const val VPN_REQUEST_CODE = 1
        private const val BATTERY_REQUEST_CODE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request battery optimization exemption first (needed for boot-started services)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, BATTERY_REQUEST_CODE)
                return
            }
        }

        // Then check VPN permission
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            PdaNetMonitorService.start(this)
            finish()
        }
    }

    @Deprecated("Deprecated in API 29")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            BATTERY_REQUEST_CODE -> {
                // After battery dialog, check VPN permission
                val vpnIntent = VpnService.prepare(this)
                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
                } else {
                    PdaNetMonitorService.start(this)
                    finish()
                }
            }
            VPN_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    PdaNetMonitorService.start(this)
                }
                finish()
            }
        }
    }
}
