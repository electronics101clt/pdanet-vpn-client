package tun.proxy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import com.github.shadowsocks.bg.Tun2proxy
import tun.utils.Util
import java.io.IOException

class Tun2HttpVpnService : VpnService() {

    companion object {
        private const val TAG = "PdaNetVpn"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val PDANET_GATEWAY = "192.168.49.1"
        private const val PROXY_PORT = 8000
        private const val TUN_MTU = 1500
        private const val CHANNEL_ID = "pdanet_vpn"
        private const val NOTIFICATION_ID = 2

        fun start(context: Context) {
            val intent = Intent(context, Tun2HttpVpnService::class.java)
            intent.action = ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, Tun2HttpVpnService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }
    }

    private var vpn: ParcelFileDescriptor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_START -> {
                if (vpn == null) {
                    if (prepare(this) != null) {
                        Log.w(TAG, "VPN permission not granted")
                        stopSelf()
                    } else {
                        startTunnel()
                    }
                }
            }
            ACTION_STOP -> {
                stopTunnel()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "VPN service destroyed")
        stopTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN permission revoked")
        stopTunnel()
        stopSelf()
        super.onRevoke()
    }

    // Called from native code — must not be renamed or removed
    @Keep
    @Suppress("unused")
    private fun nativeExit(reason: String?) {
        Log.w(TAG, "Native exit reason=$reason")
    }

    // Called from native code — must not be renamed or removed
    @Keep
    @Suppress("unused")
    private fun nativeError(error: Int, message: String?) {
        Log.w(TAG, "Native error $error: $message")
    }

    private fun startTunnel() {
        try {
            val builder = Builder()
            builder.setSession("PdaNet Client")
            builder.addAddress("10.1.10.1", 32)

            // Route all traffic EXCEPT 192.168.0.0/16 to avoid routing loop
            builder.addRoute("0.0.0.0", 1)      // 0.0.0.0 - 127.255.255.255
            builder.addRoute("128.0.0.0", 2)    // 128.0.0.0 - 191.255.255.255
            builder.addRoute("192.0.0.0", 9)    // 192.0.0.0 - 192.127.255.255
            builder.addRoute("192.128.0.0", 11) // 192.128.0.0 - 192.159.255.255
            builder.addRoute("192.160.0.0", 13) // 192.160.0.0 - 192.167.255.255
            // Skip 192.168.0.0/16 entirely (includes 192.168.49.0/24 proxy subnet)
            builder.addRoute("192.169.0.0", 16)
            builder.addRoute("192.170.0.0", 15)
            builder.addRoute("192.172.0.0", 14)
            builder.addRoute("192.176.0.0", 12)
            builder.addRoute("192.192.0.0", 10)
            builder.addRoute("193.0.0.0", 8)
            builder.addRoute("194.0.0.0", 7)
            builder.addRoute("196.0.0.0", 6)
            builder.addRoute("200.0.0.0", 5)
            builder.addRoute("208.0.0.0", 4)
            builder.addRoute("224.0.0.0", 3)

            val dnsList = Util.getDefaultDNS(applicationContext)
            for (dns in dnsList) {
                Log.i(TAG, "Adding DNS: $dns")
                try { builder.addDnsServer(dns) } catch (e: Exception) {
                    Log.w(TAG, "Skipping DNS $dns: $e")
                }
            }

            Log.i(TAG, "MTU=$TUN_MTU")
            builder.setMtu(TUN_MTU)

            val pfd = builder.establish()
                ?: throw IllegalStateException("VPN establish() returned null")

            vpn = pfd

            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PdaNetVPN:wl")
            wakeLock?.acquire(24 * 60 * 60 * 1000L)

            val proxyUrl = "http://$PDANET_GATEWAY:$PROXY_PORT"
            Log.i(TAG, "Tunnel started on fd=${pfd.fd} with proxy $proxyUrl")

            Thread({
                val result = Tun2proxy.start(
                    proxyUrl = proxyUrl,
                    tunFd = pfd.fd,
                    closeFdOnDrop = false,
                    tunMtu = TUN_MTU,
                    verbosity = Tun2proxy.Verbosity.INFO,
                    dnsStrategy = Tun2proxy.DnsStrategy.OVER_TCP
                )
                Log.i(TAG, "tun2proxy exited with code $result")
            }, "tun2proxy").apply { isDaemon = true; start() }
        } catch (e: Exception) {
            Log.e(TAG, "startTunnel failed: $e")
            stopTunnel()
            stopSelf()
        }
    }

    private fun stopTunnel() {
        vpn?.let { pfd ->
            try {
                Tun2proxy.shutdown()
            } catch (e: Throwable) {
                Log.e(TAG, "Tun2proxy shutdown failed: $e")
            }
            try { pfd.close() } catch (e: IOException) {
                Log.e(TAG, "close vpn fd: $e")
            }
            vpn = null
        }
        wakeLock?.release()
        wakeLock = null
        Log.i(TAG, "Tunnel stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PdaNet VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "VPN tunnel status"
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PdaNet VPN")
            .setContentText("Tunnel active")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
