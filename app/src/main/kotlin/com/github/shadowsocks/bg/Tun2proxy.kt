package com.github.shadowsocks.bg

import android.util.Log

/**
 * Kotlin wrapper for tun2proxy native library.
 *
 * tun2proxy provides HTTP/SOCKS proxy routing through a TUN interface.
 * This is a Rust-based implementation with proper HTTP CONNECT support.
 */
object Tun2proxy {
    private const val TAG = "Tun2proxy"

    enum class Verbosity(val value: Int) {
        OFF(0),
        ERROR(1),
        WARN(2),
        INFO(3),
        DEBUG(4),
        TRACE(5)
    }

    enum class DnsStrategy(val value: Int) {
        VIRTUAL(0),      // Fake-IP mode
        OVER_TCP(1),     // DNS over TCP
        DIRECT(2)        // No DNS handling
    }

    init {
        try {
            System.loadLibrary("tun2proxy")
            Log.i(TAG, "tun2proxy native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load tun2proxy native library", e)
            throw e
        }
    }

    /**
     * Run tun2proxy with a file descriptor.
     *
     * @param proxyUrl Proxy URL (e.g., "http://192.168.49.1:8000", "socks5://127.0.0.1:1080")
     * @param tunFd TUN file descriptor from VpnService
     * @param closeFdOnDrop Whether to close the FD when tun2proxy stops
     * @param tunMtu TUN interface MTU
     * @param verbosity Logging verbosity level
     * @param dnsStrategy DNS handling strategy
     * @return 0 on success, non-zero on error
     */
    @JvmStatic
    external fun run(
        proxyUrl: String,
        tunFd: Int,
        closeFdOnDrop: Boolean,
        tunMtu: Int,
        verbosity: Int,
        dnsStrategy: Int
    ): Int

    /**
     * Stop the running tun2proxy instance.
     *
     * @return 0 on success, non-zero on error
     */
    @JvmStatic
    external fun stop(): Int

    /**
     * Convenience method with enum parameters.
     */
    fun start(
        proxyUrl: String,
        tunFd: Int,
        closeFdOnDrop: Boolean = true,
        tunMtu: Int = 1500,
        verbosity: Verbosity = Verbosity.INFO,
        dnsStrategy: DnsStrategy = DnsStrategy.DIRECT
    ): Int {
        Log.i(TAG, "Starting tun2proxy: proxyUrl=$proxyUrl, tunFd=$tunFd, mtu=$tunMtu")
        return run(proxyUrl, tunFd, closeFdOnDrop, tunMtu, verbosity.value, dnsStrategy.value)
    }

    /**
     * Convenience method to stop.
     */
    fun shutdown(): Int {
        Log.i(TAG, "Stopping tun2proxy")
        return stop()
    }
}
