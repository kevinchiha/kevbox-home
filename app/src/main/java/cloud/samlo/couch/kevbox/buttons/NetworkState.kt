package cloud.samlo.couch.kevbox.buttons

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

enum class Link { WIFI, ETHERNET, NONE }

/** [level] is 0..4 and only means something for WIFI. */
data class NetState(val link: Link, val level: Int, val validated: Boolean, val vpn: Boolean)

/**
 * The box's network as the Wi-Fi and VPN buttons show it. A VPN rides on top of a real link, so
 * it is reported beside that link rather than instead of it.
 */
object NetworkState {
    /** Builds a [NetState] from every network the box has right now. */
    fun read(context: Context): NetState {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetState(Link.NONE, 0, false, false)
        @Suppress("DEPRECATION")
        val caps = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }
        val vpn = caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
        val real = caps.filterNot { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
        val ethernet = real.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }
        val wifi = real.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        return when {
            ethernet != null -> NetState(Link.ETHERNET, 0, ethernet.validated(), vpn)
            wifi != null -> NetState(Link.WIFI, wifiLevel(context), wifi.validated(), vpn)
            else -> NetState(Link.NONE, 0, false, vpn)
        }
    }

    /**
     * Calls [onChange] on the main thread with a fresh [NetState] whenever a network comes, goes
     * or changes, VPNs included: the request drops NOT_VPN, which every default request carries.
     */
    fun watch(context: Context, onChange: (NetState) -> Unit): ConnectivityManager.NetworkCallback? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val request = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChange(read(context))
            override fun onLost(network: Network) = onChange(read(context))
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onChange(read(context))
        }
        cm.registerNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
        return callback
    }

    fun unwatch(context: Context, callback: ConnectivityManager.NetworkCallback) {
        try {
            context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            // Already unregistered.
        }
    }

    private fun NetworkCapabilities.validated() = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    /** The Wi-Fi RSSI on 0..4. The old calls stay because minSdk is 28. */
    private fun wifiLevel(context: Context): Int {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return 0
        @Suppress("DEPRECATION")
        val rssi = wifi.connectionInfo?.rssi ?: return 0
        @Suppress("DEPRECATION")
        return WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4)
    }
}
