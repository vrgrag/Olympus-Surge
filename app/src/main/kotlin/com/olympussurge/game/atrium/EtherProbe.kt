package com.olympussurge.game.atrium

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Reachability helpers used by the router, the silence screen and the
 * long-lived WebView surface. Three tiers, each with a very different
 * cost/accuracy trade-off.
 *
 * ── [online] ── zero-cost snapshot ──
 *   `activeNetwork` + `NET_CAPABILITY_INTERNET`, nothing else.
 *   Intentionally does NOT require `NET_CAPABILITY_VALIDATED` —
 *   that flag is a lagging signal on multiple OEM stacks (MIUI,
 *   EMUI, several carrier ROMs) and produced false negatives that
 *   showed the silence screen while the pipe was actually flowing.
 *   Intentionally does NOT require a specific transport type
 *   either — WiFi / cellular / Ethernet / VPN all count as up.
 *
 * ── [awaitOnline] ── snapshot with a grace period ──
 *   Waits up to `graceMs` for a link to come up (200ms polling).
 *   Used by every router decision gate: when the user taps Retry
 *   from the silence screen it is common for `online` to still
 *   read false for 100-800ms while Android finishes registering
 *   the just-restored network. A single-shot snapshot is a
 *   guaranteed false negative in that window.
 *
 * ── [reachable] ── TCP handshake, MULTIPLE hosts ──
 *   Only used where a real byte-through-the-pipe guarantee is
 *   worth the extra latency: the silence screen's auto-recovery
 *   NetworkCallback (before re-launching from a phantom "network
 *   available" event) and the WebView's mid-session heartbeat.
 *   Deliberately NOT used by the router gates — TCP to a single
 *   external host is a well-known false-negative source on hotel
 *   / airport / corporate / VPN networks that only permit HTTPS
 *   to a whitelist, and a false negative there loops the user
 *   back onto the silence screen even though they are online.
 */
object EtherProbe {

    /** Cheap non-blocking check. Returns false only when the OS is
     *  not even claiming an INTERNET-capable route. */
    fun online(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Snapshot with grace: waits up to [graceMs] for the OS to report a
     * link. Returns true as soon as one appears. Small polling step so
     * a real recovery is caught within ~200ms of Android's own state
     * flipping.
     *
     * Used by the router's first-frame reachability gate — critical
     * for the "silence screen Retry immediately after enabling WiFi"
     * flow, where the Retry fires ~100-500ms before Android has
     * finished registering the restored network.
     */
    suspend fun awaitOnline(context: Context, graceMs: Long = DEFAULT_GRACE_MS): Boolean {
        if (online(context)) return true
        val stepMs = 190L
        var waited = 0L
        while (waited < graceMs) {
            delay(stepMs)
            waited += stepMs
            if (online(context)) return true
        }
        return online(context)
    }

    /**
     * Real-byte reachability check. Attempts a short TCP handshake
     * against several well-known always-on endpoints; the first one to
     * connect wins. Returns false only when every candidate host fails
     * or times out — that's the "OS says INTERNET but bytes do not
     * actually flow" case (captive portal, ghost cellular, hostile
     * VPN split-tunnel).
     *
     * Host list mixes ports 443 (survives HTTPS-only firewalls) and
     * 80 (survives ports-blocked-except-web firewalls) and different
     * DNS-free anycast providers so a single blackhole does not blind
     * the probe.
     */
    suspend fun reachable(context: Context): Boolean {
        if (!online(context)) return false
        return withContext(Dispatchers.IO) {
            for (target in PROBE_TARGETS) {
                val ok = runCatching {
                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(target.host, target.port),
                            PROBE_TIMEOUT_MS,
                        )
                        true
                    }
                }.getOrDefault(false)
                if (ok) return@withContext true
            }
            false
        }
    }

    private data class ProbeTarget(val host: String, val port: Int)

    /** Ordered by best-first hit rate on real user data. Cloudflare
     *  443 first (near-universal), Google 443 second (public wifi
     *  captive portals often serve it plain-text on 80), then a plain
     *  port-80 fallback for HTTPS-blocked corporate proxies. */
    private val PROBE_TARGETS = arrayOf(
        ProbeTarget("1.1.1.1", 443),
        ProbeTarget("8.8.8.8", 443),
        ProbeTarget("1.0.0.1", 80),
    )

    /** Per-host TCP-handshake budget. Sized so the whole 3-host sweep
     *  fits well inside a single splash step; a single wrong host
     *  hanging for the full budget still leaves us with two more
     *  chances before the caller times out. */
    private const val PROBE_TIMEOUT_MS = 3_100

    /** Default grace window for [awaitOnline]. Long enough to swallow
     *  the "just tapped Retry after enabling WiFi" race, short enough
     *  that a genuinely offline device still hits the silence screen
     *  well before the user gives up. */
    private const val DEFAULT_GRACE_MS = 1_800L
}
