package com.olympussurge.game.atrium.appsflyer

import android.content.Context
import com.appsflyer.AppsFlyerLib
import com.appsflyer.deeplink.DeepLinkResult
import com.olympussurge.game.sanctum.lore.FableCache
import com.olympussurge.game.sanctum.lore.MnemonicLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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
     *
     * Deliberately NOT a separate HTTP call to the vendor's GCD
     * endpoint: we don't ship a raw attribution endpoint in the DEX.
     */
    fun nudgeSdk(context: Context) {
        runCatching {
            AppsFlyerLib.getInstance().start(context.applicationContext)
            MnemonicLog.chant(TAG, "SDK nudged for a fresh attribution attempt")
        }.onFailure {
            MnemonicLog.warn(TAG, "nudgeSdk failed", it)
        }
    }

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
