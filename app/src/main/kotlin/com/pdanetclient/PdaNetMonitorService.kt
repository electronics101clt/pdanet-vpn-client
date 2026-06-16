package com.pdanetclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import tun.proxy.service.Tun2HttpVpnService

class PdaNetMonitorService : Service() {

    companion object {
        private const val TAG = "PdaNetMonitor"
        private const val PDANET_GATEWAY = "192.168.49.1"
        private const val CHANNEL_ID = "pdanet_monitor"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, PdaNetMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var vpnServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for PdaNet…"))
        registerNetworkCallback()
        Log.i(TAG, "Monitor service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "Monitor service destroyed")
        unregisterNetworkCallback()
        stopVpnService()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        if (networkCallback != null) {
            recheckNetwork()
            return
        }

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                checkNetwork(cm, network)
            }

            override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
                checkNetwork(cm, network)
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "Network lost")
                stopVpnService()
                updateNotification("Waiting for PdaNet…")
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        try {
            cm.registerNetworkCallback(request, cb)
            networkCallback = cb
            Log.i(TAG, "Network callback registered")
            // Check immediately if already on PdaNet
            cm.activeNetwork?.let { checkNetwork(cm, it) }
        } catch (e: Exception) {
            Log.e(TAG, "registerNetworkCallback failed: $e")
        }
    }

    private fun recheckNetwork() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetwork?.let { checkNetwork(cm, it) }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb ->
            try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.e(TAG, "unregisterNetworkCallback failed: $e")
            }
            networkCallback = null
        }
    }

    private fun checkNetwork(cm: ConnectivityManager, network: Network) {
        val lp = cm.getLinkProperties(network) ?: return
        val gateway = lp.routes
            .firstOrNull { it.isDefaultRoute }
            ?.gateway?.hostAddress

        Log.i(TAG, "Gateway detected: $gateway")

        if (gateway == PDANET_GATEWAY) {
            if (!vpnServiceRunning) {
                Log.i(TAG, "PdaNet detected — starting VPN service")
                startVpnService()
                updateNotification("Connected via PdaNet")
            }
        } else {
            if (vpnServiceRunning) {
                Log.i(TAG, "Not on PdaNet — stopping VPN service")
                stopVpnService()
                updateNotification("Waiting for PdaNet…")
            }
        }
    }

    private fun startVpnService() {
        try {
            Tun2HttpVpnService.start(this)
            vpnServiceRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN service: $e")
        }
    }

    private fun stopVpnService() {
        if (!vpnServiceRunning) return
        try {
            Tun2HttpVpnService.stop(this)
            vpnServiceRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop VPN service: $e")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PdaNet Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "PdaNet connection monitoring"
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("PdaNet Client")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
