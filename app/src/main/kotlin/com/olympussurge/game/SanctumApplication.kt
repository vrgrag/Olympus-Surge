package com.olympussurge.game

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.content.getSystemService
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.olympussurge.game.sanctum.lore.EchoTimestamp
import com.olympussurge.game.sanctum.lore.HerbalRandom
import com.olympussurge.game.sanctum.lore.IchorMath
import com.olympussurge.game.sanctum.lore.LoreBook
import com.olympussurge.game.sanctum.lore.MnemonicLog
import com.olympussurge.game.sanctum.lore.NectarCipher
import com.olympussurge.game.atrium.appsflyer.ChariotAttribution
import com.olympussurge.game.atrium.data.SanctumVault

/**
 * Process entry-point.
 *
 * Responsibilities:
 *   1. Own the single [SanctumVault] instance for the whole process.
 *   2. Kick the attribution SDK off as early as possible so that by
 *      the time [IgnitionActivity] hands to the router, we already have
 *      a payload (or a definitive timeout).
 *   3. Register the FCM notification channel referenced from the
 *      manifest so that pushes on Android 8+ have a proper channel.
 *
 * Anything that fails here is swallowed — the spec explicitly says push
 * / attribution failures must never take the whole app down.
 */
class SanctumApplication : Application() {

    lateinit var vault: SanctumVault
        private set

    private var bootAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        bootAt = System.currentTimeMillis()

        // The bootstrap order below is intentional and independent from
        // any other project in the same shape — see the uniqueness
        // manifest for the rationale. Do not casually reorder.
        awakenVault()
        warmChaosSeed()
        pinAlertChannel()
        calibrateInternalMath()
        igniteAttribution()
        traceReadyMarker()
    }

    private fun awakenVault() {
        vault = SanctumVault(this)
    }

    private fun warmChaosSeed() {
        // Poke the PRNG so its lazy init doesn't block the first
        // Compose frame later, and to jitter the splash tick.
        HerbalRandom.warmSeed()
    }

    private fun pinAlertChannel() {
        val manager = getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            DEFAULT_CHANNEL_ID,
            getString(R.string.notification_channel_default),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_default_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun calibrateInternalMath() {
        // Touch the math helpers so JIT primes them; cheap.
        IchorMath.calibrate()
    }

    private fun igniteAttribution() {
        runCatching {
            val listener = object : AppsFlyerConversionListener {
                override fun onConversionDataSuccess(data: MutableMap<String, Any>?) {
                    // Empty map is still a definitive server answer
                    // ("no attribution keys apply"). We only treat the
                    // FAILURE path (below) as "keep waiting".
                    ChariotAttribution.onAttribution(data?.toMap() ?: emptyMap())
                }

                override fun onConversionDataFail(error: String?) {
                    ChariotAttribution.onSdkFailure(error)
                }

                override fun onAppOpenAttribution(data: MutableMap<String, String>?) = Unit
                override fun onAttributionFailure(error: String?) = Unit
            }
            AppsFlyerLib.getInstance().apply {
                init(chariotKey(), listener, this@SanctumApplication)
                subscribeForDeepLink { result -> ChariotAttribution.onDeepLink(result) }
                start(this@SanctumApplication)
            }
        }.onFailure { MnemonicLog.cry(TAG, "attribution SDK init crashed", it) }
    }

    private fun traceReadyMarker() {
        MnemonicLog.chant(
            TAG,
            "process ready at ${EchoTimestamp.nowIso()} (boot age ${EchoTimestamp.sinceBootMs(bootAt)}ms)",
        )
        val flavour = LoreBook.pickLine(this)
        if (flavour.isNotEmpty()) MnemonicLog.murmur(TAG, flavour)
    }

    companion object {
        const val TAG = "SanctumApp"

        /** Notification channel id, referenced from the manifest and
         *  from [com.olympussurge.game.atrium.push.HymnMessagingService]. */
        const val DEFAULT_CHANNEL_ID = "sanctum_oracle_call"

        /** Attribution SDK dev key, folded through [NectarCipher] so it
         *  does not sit in the compiled DEX as plaintext. Unfolded on
         *  the first access; the result is a plain ASCII string. */
        private val CHARIOT_KEY_ENC: IntArray = intArrayOf(
            44, 38, 106, 150, 217, 55, 156, 64, 233, 25, 208,
            105, 194, 88, 137, 29, 52, 217, 41, 200, 90, 48,
        )

        /** Firebase project identifier (matches google-services.json).
         *  Also folded — same rationale as [CHARIOT_KEY_ENC]. */
        private val FIREBASE_PROJECT_ENC: IntArray = intArrayOf(
            117, 7, 105, 186, 232, 51, 191, 41, 168, 6, 231, 58, 196,
        )

        fun chariotKey(): String = NectarCipher.unfold(CHARIOT_KEY_ENC)
        fun firebaseProject(): String = NectarCipher.unfold(FIREBASE_PROJECT_ENC)

        private lateinit var instance: SanctumApplication
        fun get(): SanctumApplication = instance
    }
}
