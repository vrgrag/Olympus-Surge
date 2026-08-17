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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process entry-point.
 *
 * Responsibilities:
 *   1. Own the single [SanctumVault] instance for the whole process.
 *   2. Register the FCM notification channel referenced from the
 *      manifest so that pushes on Android 8+ have a proper channel.
 *   3. Expose an idempotent [ensureAttributionIgnited] entry point that
 *      the router-owning activity calls only AFTER connectivity has
 *      been confirmed. Starting the attribution SDK from `onCreate`
 *      unconditionally is what triggered the "offline install → retry
 *      loops into game mode" bug: the SDK's very first install-event
 *      call would fail on the dead pipe, drop into a 30-45s internal
 *      exponential backoff, and every subsequent in-process retry
 *      would silently no-op until the user cold-launched the app from
 *      the launcher. Deferring the init until we have a real link
 *      means the SDK never sees the offline state at all.
 *
 * Anything that fails here is swallowed — the spec explicitly says push
 * / attribution failures must never take the whole app down.
 */
class SanctumApplication : Application() {

    lateinit var vault: SanctumVault
        private set

    private var bootAt: Long = 0L

    /** True once [ensureAttributionIgnited] has actually wired up the
     *  vendor SDK. Guards against duplicate init calls. */
    private val attributionIgnited = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        instance = this
        bootAt = System.currentTimeMillis()

        // The bootstrap order below is intentional and independent from
        // any other project in the same shape — see the uniqueness
        // manifest for the rationale. Do not casually reorder.
        //
        // Note: `igniteAttribution` is NOT called here anymore. See
        // [ensureAttributionIgnited] and the class-level doc.
        awakenVault()
        warmChaosSeed()
        pinAlertChannel()
        calibrateInternalMath()
        traceReadyMarker()
    }

    /**
     * Idempotent late-boot for the attribution SDK. Called by
     * [IgnitionActivity] once connectivity has been confirmed. Safe to
     * call multiple times — only the first call runs the init; every
     * subsequent call is a cheap no-op.
     *
     * Returns true iff the SDK is ready to deliver attribution after
     * this call (i.e. init ran either now or previously). A `false`
     * return only happens when the vendor library itself blew up during
     * init — in that case the caller should treat attribution as
     * permanently unavailable and hand a null payload to the router.
     */
    fun ensureAttributionIgnited(): Boolean {
        if (!attributionIgnited.compareAndSet(false, true)) return true
        return igniteAttribution().also { ok ->
            if (!ok) attributionIgnited.set(false)
        }
    }

    /** True once the SDK has been wired up in this process. */
    fun isAttributionIgnited(): Boolean = attributionIgnited.get()

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

    /**
     * Wires up the vendor attribution SDK. Kept private so callers can
     * only reach it through [ensureAttributionIgnited], which enforces
     * the "one init per process" invariant.
     *
     * Returns true on success. A `false` return means the vendor
     * library threw during init — extremely rare (bad manifest, missing
     * native lib), but the caller needs to know so it can proceed with
     * a null attribution payload instead of waiting forever for a
     * callback that will never fire.
     */
    private fun igniteAttribution(): Boolean {
        val outcome = runCatching {
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
            MnemonicLog.chant(TAG, "attribution SDK ignited (deferred until online)")
        }
        outcome.onFailure { MnemonicLog.cry(TAG, "attribution SDK init crashed", it) }
        return outcome.isSuccess
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
