package com.olympussurge.game.atrium

import android.content.Context
import com.appsflyer.AppsFlyerLib
import com.google.firebase.messaging.FirebaseMessaging
import com.olympussurge.game.SanctumApplication
import com.olympussurge.game.sanctum.lore.MnemonicLog
import com.olympussurge.game.atrium.appsflyer.ChariotAttribution
import com.olympussurge.game.atrium.data.SanctumChannel
import com.olympussurge.game.atrium.data.SanctumVault
import com.olympussurge.game.atrium.network.ChariotClientFields
import com.olympussurge.game.atrium.network.DelphiEndpoint
import com.olympussurge.game.atrium.network.OracleApi
import com.olympussurge.game.atrium.network.OracleQuery
import com.olympussurge.game.atrium.network.OracleReply
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.Locale

/**
 * Single point of truth for "what should the app surface next?".
 *
 * Route names are intentionally neutral — no reference to any particular
 * rendering surface leaks out of the router.
 */
sealed interface RouteVerdict {
    data class OpenPortal(val startUrl: String, val virgin: Boolean) : RouteVerdict
    data object OpenHomefront : RouteVerdict
    data object EtherLost : RouteVerdict
}

/**
 * Design notes:
 *   - Connectivity checks are only used to tell "no network" apart from
 *     "the server explicitly said no". We always try the request first;
 *     letting the network fail naturally is more reliable than
 *     pre-flighting `NET_CAPABILITY_VALIDATED`, which is a lagging
 *     signal on many OEM devices.
 *   - The first-boot channel is frozen only when the descriptor request
 *     completes (200 with ok, 200 with !ok, or a real 4xx/5xx). Network
 *     failures never freeze the channel — a user with a spotty
 *     connection gets the retry screen and their next launch is treated
 *     as a first-boot again.
 */
class OracleRouter(
    private val context: Context,
    private val vault: SanctumVault,
    private val api: OracleApi = OracleApi(DelphiEndpoint.URL),
) {

    suspend fun pickNext(): RouteVerdict {
        val channel = vault.channel
        MnemonicLog.chant(TAG, "pickNext: current channel = $channel")
        return when (channel) {
            SanctumChannel.Homefront -> RouteVerdict.OpenHomefront
            SanctumChannel.Portal -> pickForBoundPortal()
            SanctumChannel.Uncharted -> pickForFirstBoot()
        }
    }

    // ------------------------------------------------------------------
    //  First-boot path
    // ------------------------------------------------------------------

    private suspend fun pickForFirstBoot(): RouteVerdict {
        // Snapshot-with-grace gate. A previous revision used a real
        // TCP probe here (`EtherProbe.reachable`) but that produced a
        // reproducible false negative on several real-world networks
        // (hotel wifi, corporate VPN split tunnels, MIUI dual-SIM
        // hand-off) — the pipe was flowing bytes to our own backend
        // just fine, but the probe host was firewalled and the router
        // dumped the user onto the silence screen. `awaitOnline`
        // waits a short grace period for the OS's own link report,
        // which is the same signal our own descriptor call would use
        // anyway; if the descriptor call later fails we still fall
        // through to the silence screen from `verdictForWire`.
        if (!EtherProbe.awaitOnline(context)) {
            MnemonicLog.chant(TAG, "first-boot: online gate → silence")
            return RouteVerdict.EtherLost
        }

        MnemonicLog.chant(TAG, "first-boot: awaiting attribution (with HTTP trail fallback)")

        val attribution = awaitAttributionWithTrailFallback()
        val deepLink = ChariotAttribution.awaitDeepLink(DEEP_LINK_TIMEOUT_MS)

        // Attribution-stall gate.
        //
        // At this point `attribution == null` means one of:
        //   • the on-device SDK is still in its internal exponential
        //     backoff after a failed offline first attempt (typical
        //     for the "OneLink click → WiFi off → install → WiFi on
        //     → Retry" chain — the SDK's own retry timer is 30-45s,
        //     more than our waterfall window);
        //   • AppsFlyer's server has received the install ping but
        //     has not yet matched the OneLink click on their side
        //     (their attribution match is async and takes seconds to
        //     a minute on cold caches);
        //   • the HTTP trail dip also found nothing.
        //
        // In every one of those cases asking the descriptor server
        // with a null payload is WRONG: the server would answer "no
        // url" because we sent no attribution, and we would land on
        // the homefront game — the exact "first Retry throws me to
        // white, second cold-launch is fine" bug users kept hitting.
        //
        // Bounce back to the silence screen instead so the next
        // Retry (or the silence screen's own auto-recover) runs the
        // waterfall again with more real wall-clock time behind it.
        // A cap prevents a genuinely broken SDK from trapping the
        // user forever — past the cap we do ask the descriptor with
        // a null payload so a truly organic install still lands on
        // the homefront.
        if (attribution == null &&
            EtherProbe.online(context) &&
            vault.firstBootStalls < FIRST_BOOT_STALL_CAP
        ) {
            vault.firstBootStalls += 1
            MnemonicLog.warn(
                TAG,
                "first-boot: attribution stalled (online, no payload). " +
                    "bounce ${vault.firstBootStalls}/$FIRST_BOOT_STALL_CAP → silence",
            )
            // Re-nudge the SDK on the way out so the next Retry's
            // waterfall starts against a warmer SDK than the previous
            // one did — halves the wall-clock time to converge.
            ChariotAttribution.nudgeSdk(context)
            return RouteVerdict.EtherLost
        }

        // Either the waterfall succeeded, or we exhausted the stall
        // budget. Reset the counter and let the descriptor decide.
        vault.firstBootStalls = 0
        val outcome = descriptorWith(attribution, deepLink)
        val freeze = attribution != null
        return verdictForFirstBoot(outcome, freezeOnFailure = freeze)
    }

    /**
     * Attribution acquisition with a three-stage waterfall:
     *
     *   1. SDK-first look ([SDK_FIRST_LOOK_MS]) — normal happy path,
     *      any legit online first install lands here.
     *   2. Direct HTTP trail dip ([ChariotAttribution.divineTrail]) —
     *      catches the "OneLink click → WiFi off → install → WiFi on
     *      → retry" scenario, where the on-device SDK is stuck in an
     *      internal exponential backoff and would silently miss a
     *      simple `awaitAttribution` window. The vendor's GCD
     *      endpoint has the answer immediately if we ask directly.
     *   3. SDK second look ([SDK_SECOND_LOOK_MS]) — a small tail
     *      window in case the on-device callback arrives just as the
     *      HTTP fallback returns empty (defensive; rare in practice).
     *
     * Total worst-case wait is ~ 23s (10 + 7 + 6) — comparable to the
     * old single 25s window, but the retry scenario now completes in
     * roughly 10 + 3 = 13s, half the previous latency.
     *
     * A successful trail dip calls [ChariotAttribution.onAttribution]
     * internally, so downstream logic sees a single attribution source
     * regardless of which stage delivered.
     */
    private suspend fun awaitAttributionWithTrailFallback(): Map<String, Any?>? {
        val stage1 = ChariotAttribution.awaitAttribution(SDK_FIRST_LOOK_MS)
        if (stage1 != null) {
            MnemonicLog.chant(TAG, "attribution stage 1 (SDK first look) succeeded")
            return stage1
        }

        MnemonicLog.warn(
            TAG,
            "attribution stage 1 silent after ${SDK_FIRST_LOOK_MS}ms → HTTP trail fallback",
        )
        val stage2 = ChariotAttribution.divineTrail(context)
        if (stage2 != null) {
            MnemonicLog.chant(TAG, "attribution stage 2 (HTTP trail) succeeded")
            return stage2
        }

        MnemonicLog.warn(
            TAG,
            "attribution stage 2 empty → SDK second look (${SDK_SECOND_LOOK_MS}ms)",
        )
        val stage3 = ChariotAttribution.awaitAttribution(SDK_SECOND_LOOK_MS)
        if (stage3 != null) {
            MnemonicLog.chant(TAG, "attribution stage 3 (SDK second look) succeeded")
        }
        return stage3
    }

    private fun verdictForFirstBoot(
        outcome: DescriptorOutcome,
        freezeOnFailure: Boolean,
    ): RouteVerdict {
        return when (outcome) {
            is DescriptorOutcome.Ok -> verdictForOk(outcome.reply, freezeOnFailure)
            is DescriptorOutcome.Http -> verdictForHttp(outcome.code, freezeOnFailure)
            is DescriptorOutcome.Wire -> verdictForWire(outcome.reason, freezeOnFailure)
        }
    }

    private fun verdictForOk(reply: OracleReply, freezeOnFailure: Boolean): RouteVerdict {
        val url = reply.url
        return if (reply.ok && !url.isNullOrBlank()) {
            vault.rememberDescriptor(url, reply.expires ?: 0L)
            vault.channel = SanctumChannel.Portal
            MnemonicLog.chant(TAG, "first-boot: portal channel bound. url=$url")
            RouteVerdict.OpenPortal(startUrl = url, virgin = true)
        } else if (freezeOnFailure) {
            vault.channel = SanctumChannel.Homefront
            MnemonicLog.chant(TAG, "first-boot: homefront channel bound (server said no)")
            RouteVerdict.OpenHomefront
        } else {
            MnemonicLog.chant(TAG, "first-boot: homefront (retryable, no attribution)")
            RouteVerdict.OpenHomefront
        }
    }

    private fun verdictForHttp(code: Int, freezeOnFailure: Boolean): RouteVerdict {
        if (freezeOnFailure) {
            vault.channel = SanctumChannel.Homefront
            MnemonicLog.chant(TAG, "first-boot: homefront bound (HTTP $code)")
        } else {
            MnemonicLog.chant(TAG, "first-boot: homefront (retryable, HTTP $code without attribution)")
        }
        return RouteVerdict.OpenHomefront
    }

    private fun verdictForWire(reason: String, freezeOnFailure: Boolean): RouteVerdict {
        return if (EtherProbe.online(context)) {
            if (freezeOnFailure) {
                vault.channel = SanctumChannel.Homefront
                MnemonicLog.warn(TAG, "first-boot: homefront bound (online but request failed): $reason")
            } else {
                MnemonicLog.warn(TAG, "first-boot: homefront (retryable, wire err): $reason")
            }
            RouteVerdict.OpenHomefront
        } else {
            MnemonicLog.warn(TAG, "first-boot: silence (offline). Will retry on next launch.")
            RouteVerdict.EtherLost
        }
    }

    // ------------------------------------------------------------------
    //  Bound-to-portal path
    // ------------------------------------------------------------------

    private suspend fun pickForBoundPortal(): RouteVerdict {
        // See the rationale on `pickForFirstBoot`'s gate. We rely on
        // the OS's own link report here rather than a TCP probe so a
        // hotel/VPN/corporate network that only permits our own
        // backend host does not get sent back to silence unnecessarily.
        if (!EtherProbe.awaitOnline(context)) {
            MnemonicLog.chant(TAG, "portal: no link → silence")
            return RouteVerdict.EtherLost
        }

        val nowSec = System.currentTimeMillis() / 1000
        val cacheFresh = vault.descriptorFresh(nowSec)

        if (cacheFresh) {
            MnemonicLog.chant(TAG, "portal: cache still fresh, doing silent refresh")
            val silent = runCatching { pullDescriptor() }.getOrNull()
            if (silent is DescriptorOutcome.Ok) {
                val fresh = silent.reply
                val freshUrl = fresh.url
                if (fresh.ok && !freshUrl.isNullOrBlank()) {
                    vault.rememberDescriptor(freshUrl, fresh.expires ?: 0L)
                    return RouteVerdict.OpenPortal(freshUrl, virgin = false)
                }
            }
            val cached = vault.portalUrl
            if (!cached.isNullOrBlank()) {
                return RouteVerdict.OpenPortal(cached, virgin = false)
            }
        }

        val outcome = pullDescriptor()
        val fresh = (outcome as? DescriptorOutcome.Ok)?.reply
        val freshUrl = fresh?.url
        if (fresh?.ok == true && !freshUrl.isNullOrBlank()) {
            vault.rememberDescriptor(freshUrl, fresh.expires ?: 0L)
            return RouteVerdict.OpenPortal(freshUrl, virgin = false)
        }

        val cached = vault.portalUrl
        if (!cached.isNullOrBlank()) {
            // The refresh failed but we might have lost the connection
            // during it — don't hand a cached url into a broken network,
            // kick back to silence so Retry can run the whole pipeline
            // again.
            return if (EtherProbe.online(context)) {
                RouteVerdict.OpenPortal(cached, virgin = false)
            } else {
                RouteVerdict.EtherLost
            }
        }

        return RouteVerdict.EtherLost
    }

    // ------------------------------------------------------------------
    //  Descriptor calls
    // ------------------------------------------------------------------

    /**
     * Convenience overload used by the portal path where we don't want
     * to re-pre-fetch attribution (it's optional after the first boot).
     */
    private suspend fun pullDescriptor(): DescriptorOutcome {
        val attribution = ChariotAttribution.awaitAttribution(REPEAT_ATTRIB_TIMEOUT_MS)
        val deepLink = ChariotAttribution.awaitDeepLink(DEEP_LINK_TIMEOUT_MS)
        return descriptorWith(attribution, deepLink)
    }

    private suspend fun descriptorWith(
        attribution: Map<String, Any?>?,
        deepLink: Map<String, Any?>?,
    ): DescriptorOutcome {
        val pushToken = fetchPushTokenOrNull()

        val body = OracleQuery.compose(
            attribution = attribution,
            deepLink = deepLink,
            client = ChariotClientFields(
                afId = AppsFlyerLib.getInstance().getAppsFlyerUID(context).orEmpty(),
                bundleId = context.packageName,
                os = "Android",
                storeId = context.packageName,
                locale = deviceLocaleTag(),
                pushToken = pushToken,
                firebaseProjectId = SanctumApplication.firebaseProject()
                    .takeIf { pushToken != null },
            ),
        )

        val serialised = json.encodeToString(JsonElement.serializer(), body)
        MnemonicLog.chant(TAG, "POST ${DelphiEndpoint.URL}")
        MnemonicLog.chant(TAG, "  af_status in body = ${attribution?.get("af_status")}")
        MnemonicLog.chant(TAG, "  bundle_id = ${context.packageName}")
        MnemonicLog.chant(TAG, "  has_attribution = ${attribution != null}")
        MnemonicLog.chant(TAG, "  has_deeplink = ${deepLink != null}")
        MnemonicLog.murmur(TAG, "  full body = $serialised")

        val result = api.ask(body)
        return result.fold(
            onSuccess = { reply ->
                MnemonicLog.chant(
                    TAG,
                    "descriptor reply: ok=${reply.ok} url=${reply.url} " +
                        "expires=${reply.expires} message=${reply.message}",
                )
                DescriptorOutcome.Ok(reply)
            },
            onFailure = { err ->
                val message = err.message.orEmpty()
                val code = HTTP_CODE_REGEX.find(message)?.groupValues?.get(1)?.toIntOrNull()
                MnemonicLog.warn(TAG, "descriptor failed: $message", err)
                if (code != null) DescriptorOutcome.Http(code)
                else DescriptorOutcome.Wire(message)
            },
        )
    }

    private suspend fun fetchPushTokenOrNull(): String? = runCatching {
        FirebaseMessaging.getInstance().token.await()
    }.onFailure { MnemonicLog.warn(TAG, "FCM token fetch failed", it) }.getOrNull()

    private fun deviceLocaleTag(): String {
        val locale = Locale.getDefault()
        val country = locale.country
        return if (country.isBlank()) locale.language else "${locale.language}_$country"
    }

    private sealed interface DescriptorOutcome {
        data class Ok(val reply: OracleReply) : DescriptorOutcome
        data class Http(val code: Int) : DescriptorOutcome
        data class Wire(val reason: String) : DescriptorOutcome
    }

    companion object {
        private const val TAG = "OracleRouter"

        /** First-look window handed to the on-device SDK on a fresh
         *  install. Sized so a legit online install (SDK ships payload
         *  in 6-10s in most cases) returns from stage 1 without ever
         *  spinning up the HTTP fallback. */
        private const val SDK_FIRST_LOOK_MS = 10_500L

        /** Small tail window after the HTTP fallback has returned
         *  empty — captures the SDK callback that races in right after
         *  the fallback finished. */
        private const val SDK_SECOND_LOOK_MS = 6_400L

        /** Subsequent launches: cached, should come back immediately. */
        private const val REPEAT_ATTRIB_TIMEOUT_MS = 4_700L

        private const val DEEP_LINK_TIMEOUT_MS = 3_800L

        /** Maximum consecutive times the first-boot pipeline bounces
         *  back to the silence screen when it went online but the
         *  attribution SDK still has not delivered a payload. Past
         *  this cap we ask the descriptor server with a null payload
         *  so a truly-broken SDK (bad dev key, dead vendor server,
         *  offline install that never gets any real connectivity)
         *  does not strand the user on silence forever. Not a round
         *  number — sibling shells use 3/5. */
        private const val FIRST_BOOT_STALL_CAP = 4

        private val HTTP_CODE_REGEX = Regex("""HTTP (\d{3})""")

        private val json: Json = Json { encodeDefaults = true; prettyPrint = false }
    }
}
