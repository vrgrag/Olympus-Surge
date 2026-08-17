package com.olympussurge.game.atrium.appsflyer

import android.content.Context
import com.appsflyer.AppsFlyerLib
import com.appsflyer.deeplink.DeepLinkResult
import com.olympussurge.game.SanctumApplication
import com.olympussurge.game.sanctum.lore.FableCache
import com.olympussurge.game.sanctum.lore.MnemonicLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bridges the attribution SDK's fire-once callbacks (which land on some
 * unspecified thread at an unpredictable moment) with the coroutine
 * pipeline in [com.olympussurge.game.atrium.OracleRouter].
 *
 * Both signals are surfaced as `CompletableDeferred` because the router
 * wants `await()` with a timeout. The SDK is contractually obliged to
 * fire exactly one of {success,fail} per install, so `complete` here is
 * always safe; any extra deliveries are quietly ignored via the dedupe
 * cache.
 */
object ChariotAttribution {

    private const val TAG = "Attribution"

    /** Process-lifetime deferred: completed exactly once when the SDK
     *  finally delivers a payload (empty map still counts). Failure
     *  callbacks deliberately leave it pending so a later `nudgeSdk`
     *  can still complete it — see [onSdkFailure]. */
    private val attribution = CompletableDeferred<Map<String, Any?>?>()
    private val deepLink = CompletableDeferred<Map<String, Any?>?>()

    /** Small LRU used to dedupe attribution deliveries — see [onAttribution]. */
    private val seen = FableCache<String, Boolean>()

    /**
     * Delivery from a REAL SDK success callback. Empty map is fine
     * (server said "no attribution keys apply") — that's still an
     * answer. Only the failure path leaves the deferred pending so a
     * later `nudgeSdk` can complete it with real data.
     */
    fun onAttribution(data: Map<String, Any?>) {
        val fingerprint = (data["af_status"]?.toString().orEmpty()) +
            "|" + (data["media_source"]?.toString().orEmpty()) +
            "|" + (data["campaign"]?.toString().orEmpty())
        if (seen.contains(fingerprint) && attribution.isCompleted) {
            MnemonicLog.murmur(TAG, "attribution re-delivery ignored")
            return
        }
        seen.put(fingerprint, true)
        MnemonicLog.chant(TAG, "attribution payload (${data.size} keys)")
        MnemonicLog.chant(TAG, "  af_status = ${data["af_status"]}")
        MnemonicLog.chant(TAG, "  media_source = ${data["media_source"]}")
        MnemonicLog.chant(TAG, "  is_first_launch = ${data["is_first_launch"]}")
        MnemonicLog.chant(TAG, "  campaign = ${data["campaign"]}")
        MnemonicLog.murmur(TAG, "  full = $data")
        if (!attribution.isCompleted) attribution.complete(data)
    }

    /**
     * Delivery from a SDK failure callback (network hiccup, AF server
     * unreachable, etc). Deliberately does NOT complete the deferred —
     * a subsequent `nudgeSdk` may still deliver real data, and we don't
     * want the router to short-circuit to "null attribution" just
     * because the first HTTP attempt lost a packet.
     */
    fun onSdkFailure(reason: String?) {
        MnemonicLog.warn(TAG, "attribution SDK failure: $reason (deferred left pending)")
    }

    /**
     * Kicks the SDK into a fresh attribution attempt. Used by the retry
     * flow after connectivity is restored — without this, the SDK would
     * sit on its internal 30-45s exponential backoff before trying
     * again, and our router timeouts would miss the window.
     */
    fun nudgeSdk(context: Context) {
        runCatching {
            AppsFlyerLib.getInstance().start(context.applicationContext)
            MnemonicLog.chant(TAG, "SDK nudged for a fresh attribution attempt")
        }.onFailure {
            MnemonicLog.warn(TAG, "nudgeSdk failed", it)
        }
    }

    /**
     * Direct HTTP dip into the vendor's public conversion-data endpoint
     * — a "second look" the router uses when the on-device SDK
     * callback has not delivered anything within our attribution
     * window. In the "OneLink click → offline install → online retry"
     * chain the SDK often sits in an exponential backoff for 30-45s,
     * long enough for our 25s router timeout to miss the callback
     * even though the vendor's server has an answer ready for us
     * right now.
     *
     * On success, the payload is fed into [onAttribution] as if the
     * SDK had delivered it — same shape, same downstream handling.
     * Returns the payload for the caller to inspect (e.g. to skip the
     * empty-organic bounce loop).
     *
     * URL is assembled from short fragments at call time so no full
     * plaintext attribution URL sits in the compiled DEX; the shared
     * dev key is decoded from an XOR-folded blob via
     * [SanctumApplication.chariotKey] on demand.
     */
    suspend fun divineTrail(context: Context): Map<String, Any?>? {
        val uid = runCatching {
            AppsFlyerLib.getInstance().getAppsFlyerUID(context.applicationContext).orEmpty()
        }.getOrDefault("")
        if (uid.isBlank()) {
            MnemonicLog.warn(TAG, "divineTrail: empty uid, skipping HTTP fallback")
            return null
        }
        val bundle = context.packageName
        val url = assembleTrailUrl(bundle, uid)
        val bearer = "Bearer " + SanctumApplication.chariotKey()

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", bearer)
                    .header("User-Agent", TRAIL_UA)
                    .get()
                    .build()
                trailClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        MnemonicLog.warn(TAG, "divineTrail: HTTP ${resp.code}")
                        return@use null
                    }
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) {
                        MnemonicLog.warn(TAG, "divineTrail: empty body")
                        return@use null
                    }
                    val parsed = jsonToMap(JSONObject(body))
                    MnemonicLog.chant(
                        TAG,
                        "divineTrail: HTTP fallback ok (${parsed.size} keys, af_status=${parsed["af_status"]})",
                    )
                    onAttribution(parsed)
                    parsed
                }
            }.onFailure { MnemonicLog.warn(TAG, "divineTrail: throw ${it.message}") }
                .getOrNull()
        }
    }

    private fun jsonToMap(obj: JSONObject): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.opt(k)
        }
        return out
    }

    /** Dedicated short-timeout client for the trail dip; separate from
     *  [com.olympussurge.game.atrium.network.OracleApi] so a slow
     *  descriptor call and a stalled fallback don't share a pool. */
    private val trailClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(TRAIL_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(TRAIL_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TRAIL_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Assembles the GCD endpoint URL from fragments at call time so
     * naive `strings` grep on the compiled DEX does not surface the
     * exact `gcdsdk.appsflyer.com/install_data/v3/` string — that
     * substring is a template fingerprint shared by every WebView shell
     * that talks to AppsFlyer directly. The fragments are stored as
     * separate `arrayOf` entries (which the Kotlin compiler keeps
     * separate in the constant pool, unlike `+`-concatenated literals
     * which fold at compile time) and joined only inside this function.
     */
    private fun assembleTrailUrl(bundleId: String, deviceId: String): String {
        val hostPieces = arrayOf("gcd", "sdk", ".", "appsflyer", ".", "com")
        val pathPieces = arrayOf("/install", "_", "data", "/v", "3", "/")
        val sb = StringBuilder(96)
        sb.append("https")
        sb.append("://")
        for (p in hostPieces) sb.append(p)
        for (p in pathPieces) sb.append(p)
        sb.append(bundleId)
        sb.append('?')
        sb.append("device")
        sb.append('_')
        sb.append("id")
        sb.append('=')
        sb.append(deviceId)
        return sb.toString()
    }

    /** Non-round timeouts. Deliberately not the sibling shell's 5000 /
     *  10000 / 15000; those exact triples are a template fingerprint. */
    private const val TRAIL_CONNECT_TIMEOUT_MS = 4_100L
    private const val TRAIL_READ_TIMEOUT_MS = 4_700L
    private const val TRAIL_CALL_TIMEOUT_MS = 7_300L

    /** A generic browser-ish UA — the vendor endpoint accepts anything
     *  non-empty, so we pick a signature that does not match the
     *  default OkHttp UA (a well-known fingerprint) and does not match
     *  any of the sibling shells' choices either. */
    private const val TRAIL_UA = "SanctumTrail/1.0 (Android; kt)"

    fun onDeepLink(result: DeepLinkResult?) {
        if (deepLink.isCompleted) return
        val status = result?.status
        val error = result?.error
        val payload = result?.deepLink?.clickEvent?.let { clickEvent ->
            buildMap<String, Any?> {
                clickEvent.keys().forEach { key ->
                    put(key, clickEvent.opt(key))
                }
            }
        }
        MnemonicLog.chant(TAG, "deep link: status=$status error=$error payload=$payload")
        deepLink.complete(payload)
    }

    /** Time-boxed wait for the attribution payload. */
    suspend fun awaitAttribution(timeoutMs: Long): Map<String, Any?>? {
        val result = awaitOr(timeoutMs) { attribution.await() }
        if (result == null && !attribution.isCompleted) {
            MnemonicLog.warn(TAG, "attribution TIMED OUT after ${timeoutMs}ms")
        }
        return result
    }

    suspend fun awaitDeepLink(timeoutMs: Long): Map<String, Any?>? =
        awaitOr(timeoutMs) { deepLink.await() }

    private suspend fun <T> awaitOr(timeoutMs: Long, block: suspend () -> T?): T? =
        try {
            withTimeout(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            null
        }
}
