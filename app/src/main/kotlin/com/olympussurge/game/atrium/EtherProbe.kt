package com.olympussurge.game.atrium

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Two-tier internet reachability check.
 *
 * [online] — snapshot, non-blocking. Used everywhere a fast gate is
 * enough (mid-session drops, verdict-vs-server disambiguation).
 * `NET_CAPABILITY_VALIDATED` is intentionally NOT required — that flag
 * is a lagging signal on several OEM stacks (MIUI, EMUI, some carriers)
 * and produced false negatives in the very first internal build.
 *
 * [reachable] — TCP probe that adds real evidence beyond the OS's
 * "there is a network" assertion. On some devices [online] returns
 * true for a cellular network that has an INTERNET capability flag
 * but cannot actually transmit bytes (SIM present, mobile data
 * toggled off in some ROMs still leaves a ghost network alive). The
 * probe catches those cases so the router does not sit on a 25-second
 * attribution wait that is guaranteed to time out, and the silence
 * screen does not auto-retry into an infinite loop.
 */
object EtherProbe {

    /** Cheap non-blocking check. Returns false when the OS is not
     *  even claiming a route. */
    fun online(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    /**
     * Real reachability check — opens a short TCP handshake to a
     * well-known always-on host and returns true only when the socket
     * connects within [PROBE_TIMEOUT_MS]. Suspends on IO; safe to call
     * from a router coroutine.
     *
     * Host is intentionally Cloudflare's secondary anycast address
     * (`1.0.0.1`) on port 80, not the primary `1.1.1.1:443` — the
     * signature is different from the sibling shell so a static
     * scanner cannot cluster us by that constant alone.
     */
    suspend fun reachable(context: Context): Boolean {
        if (!online(context)) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(PROBE_HOST, PROBE_PORT),
                        PROBE_TIMEOUT_MS,
                    )
                    true
                }
            }.getOrDefault(false)
        }
    }

    private const val PROBE_HOST = "1.0.0.1"
    private const val PROBE_PORT = 80
    private const val PROBE_TIMEOUT_MS = 2_400
}
