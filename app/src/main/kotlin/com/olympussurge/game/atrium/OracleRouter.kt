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
        // Real-reachability gate. A plain snapshot check via
        // `EtherProbe.online` used to be enough here, but a class of
        // OEMs report an INTERNET-capable network as active while data
        // cannot actually flow (SIM present + mobile-data toggled, weak
        // signal + throttling). Sitting on the 25s attribution wait in
        // that state produced the infinite "long splash → NoWifi
        // flash → long splash" loop reported from the field. A short
        // TCP probe (~2s worst case) catches these ghost networks up
        // front so the silence screen pops early and does not bounce
        // back into another splash.
        if (!EtherProbe.reachable(context)) {
            MnemonicLog.chant(TAG, "first-boot: reachability gate → silence")
            return RouteVerdict.EtherLost
        }

        MnemonicLog.chant(TAG, "first-boot: awaiting attribution")

        // Give the attribution SDK plenty of time on a fresh install —
        // real deliveries routinely need 8-15s, and 20-25s when the SDK
        // is retrying after an initial network failure (typical for the
        // "OneLink click → WiFi off → install" scenario). Firing the
        // descriptor call without a real af_status would guarantee a
        // homefront lock-in even for legit OneLink users.
        val attribution = ChariotAttribution.awaitAttribution(FIRST_BOOT_ATTRIB_TIMEOUT_MS)
        val deepLink = ChariotAttribution.awaitDeepLink(DEEP_LINK_TIMEOUT_MS)

        if (attribution == null) {
            return handleFirstBootWithoutAttribution(deepLink)
        }

        // Real payload delivered → reset the stall counter and take the
        // definitive path (freeze on server verdict).
        vault.firstBootStalls = 0
        val outcome = descriptorWith(attribution, deepLink)
        return verdictForFirstBoot(outcome, freezeOnFailure = true)
    }

    /**
     * Attribution SDK has NOT delivered anything within our timeout.
     * Possible causes (ordered by likelihood):
     *
     *   1. First launch happened offline, SDK is still on its own
     *      internal retry timer, network has just been restored.
     *   2. AF servers are temporarily unreachable from this device
     *      (rare — happens under strict corporate firewalls / VPN).
     *   3. True organic install where AF will never send a payload.
     *
     * For (1) and (2), bouncing the user back to the silence screen so
     * they can trigger another retry cycle is the correct behaviour —
     * they'll land on the WebView on the next round-trip. For (3) we
     * would loop forever, so a stall counter caps the number of
     * bounces; when the cap is hit we accept the SDK's non-answer as
     * terminal and let the descriptor decide with a body missing
     * attribution keys (server almost always answers "no" in that case,
     * which routes to homefront — the right outcome for true organic).
     */
    private suspend fun handleFirstBootWithoutAttribution(
        deepLink: Map<String, Any?>?,
    ): RouteVerdict {
        val online = EtherProbe.online(context)
        if (!online) {
            MnemonicLog.warn(TAG, "first-boot: no attribution, offline — silence")
            return RouteVerdict.EtherLost
        }

        val stalls = vault.firstBootStalls
        if (stalls < FIRST_BOOT_STALL_CAP) {
            vault.firstBootStalls = stalls + 1
            MnemonicLog.warn(
                TAG,
                "first-boot: no attribution, online — bouncing to silence " +
                    "(stall ${stalls + 1}/$FIRST_BOOT_STALL_CAP)",
            )
            return RouteVerdict.EtherLost
        }

        // Stall cap reached — accept "no attribution" as terminal and
        // let the server decide. Do NOT freeze the channel: if the
        // server also says no we still leave the door open for a later
        // launch to succeed.
        MnemonicLog.warn(
            TAG,
            "first-boot: stall cap reached, sending descriptor without attribution",
        )
        val outcome = descriptorWith(attribution = null, deepLink = deepLink)
        return verdictForFirstBoot(outcome, freezeOnFailure = false)
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
        // Real-reachability guard — see the identical rationale in
        // pickForFirstBoot. Never hand a cached url to the stage while
        // the pipe is not actually flowing bytes: the stage would spin
        // under the loading cover forever and the user has no
        // affordance to retry from there.
        if (!EtherProbe.reachable(context)) {
            MnemonicLog.chant(TAG, "portal: unreachable → silence")
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

        /** First install: the attribution SDK routinely needs 8-15s;
         *  more (20-25s) when the very first HTTP attempt lost to a
         *  bad network and the SDK is now on its second-chance run. */
        private const val FIRST_BOOT_ATTRIB_TIMEOUT_MS = 25_000L

        /** Subsequent launches: cached, should come back immediately. */
        private const val REPEAT_ATTRIB_TIMEOUT_MS = 4_700L

        private const val DEEP_LINK_TIMEOUT_MS = 3_800L

        /** After this many silence-bounces on first-boot we accept the
         *  attribution SDK's silence as terminal. Sized so a genuinely
         *  slow attribution network still has enough round-trips to
         *  complete, but a truly-organic install never loops forever. */
        private const val FIRST_BOOT_STALL_CAP = 3

        private val HTTP_CODE_REGEX = Regex("""HTTP (\d{3})""")

        private val json: Json = Json { encodeDefaults = true; prettyPrint = false }
    }
}
