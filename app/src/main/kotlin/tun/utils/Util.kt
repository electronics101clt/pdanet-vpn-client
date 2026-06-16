package tun.utils

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build

object Util {

    // Static JNI binding — must remain tun.utils.Util.jni_getprop
    // to match Java_tun_utils_Util_jni_1getprop in tun2http.c
    @JvmStatic
    private external fun jni_getprop(name: String): String

    @JvmStatic
    fun getDefaultDNS(context: Context): List<String> {
        var dns1: String? = null
        var dns2: String? = null

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val lp = network?.let { cm.getLinkProperties(it) }
            val servers = lp?.dnsServers
            if (servers != null) {
                if (servers.size > 0) dns1 = servers[0].hostAddress
                if (servers.size > 1) dns2 = servers[1].hostAddress
            }
        } else {
            @Suppress("DEPRECATION")
            dns1 = jni_getprop("net.dns1")
            @Suppress("DEPRECATION")
            dns2 = jni_getprop("net.dns2")
        }

        return listOf(
            if (dns1.isNullOrEmpty()) "8.8.8.8" else dns1,
            if (dns2.isNullOrEmpty()) "8.8.4.4" else dns2
        )
    }
}
