package com.pdanetclient

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import tun.proxy.service.Tun2HttpVpnService

// Transparent one-shot activity whose only job is to trigger the system
// VPN permission dialog. Has no UI of its own. Finishes immediately after.
class VpnPermissionActivity : Activity() {

    companion object {
        private const val VPN_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            // Already granted — re-trigger the service so it retries the tunnel
            Tun2HttpVpnService.start(this)
            finish()
        }
    }

    @Deprecated("Deprecated in API 29")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            Tun2HttpVpnService.start(this)
        }
        finish()
    }
}
