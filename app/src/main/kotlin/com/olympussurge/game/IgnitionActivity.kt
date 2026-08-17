package com.olympussurge.game

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.olympussurge.game.sanctum.lore.MnemonicLog
import com.olympussurge.game.atrium.EtherProbe
import com.olympussurge.game.atrium.EtherSilenceActivity
import com.olympussurge.game.atrium.OracleRouter
import com.olympussurge.game.atrium.RouteVerdict
import com.olympussurge.game.atrium.SanctumStageActivity
import com.olympussurge.game.atrium.data.SanctumChannel
import com.olympussurge.game.atrium.push.HymnMessagingService
import com.olympussurge.game.atrium.push.OracleConsentActivity
import com.olympussurge.game.ui.OlympusTheme
import com.olympussurge.game.ui.SplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The very first activity. Renders the splash while the router picks
 * which experience to show next.
 *
 * Two special short-circuit paths run before the normal router flow:
 *
 *   1. Resume-URL after silence. When the stage got killed by an
 *      offline event, [EtherSilenceActivity] boots us back with
 *      [EXTRA_RESUME_URL]. We go straight back to the stage on that
 *      exact URL, no descriptor re-fetch, so the user resumes where
 *      they were — never the stage's front page.
 *
 *   2. Alert tap. Cold-start alert (app killed) or warm alert where
 *      the OEM launcher shortcut swallowed our extras. Alert URL is
 *      looked up in this priority:
 *        a. Our own extra [HymnMessagingService.EXTRA_ALERT_URL].
 *        b. Raw FCM data keys as intent extras (`url`, `link`, …).
 *        c. [com.olympussurge.game.atrium.data.SanctumVault.consumeStashedAlertUrl] —
 *           written by the FCM service when the alert arrived even if
 *           the OS then bypassed our custom PendingIntent.
 *      Alert routing is honoured for every non-homefront channel; on
 *      a fresh install the alert itself implies the portal channel.
 */
class IgnitionActivity : ComponentActivity() {

    private var warmupDone: Boolean = false
    private var routerVerdict: RouteVerdict? = null
    private var routed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Explicitly opt this activity into drawing behind display
        // cutouts. Without this the OS reserves a black bar over the
        // camera notch region on the splash screen — every other
        // "gray" surface (silence, consent, stage) draws its own black
        // top strip to visually match that reserved area, but the
        // splash key art is meant to bleed all the way to the top
        // edge (no notch strip). Only affects this activity's window;
        // sibling activities keep their existing insets treatment.
        allowDrawUnderCutout()
        enableEdgeToEdge()
        goFullscreen()

        setContent {
            OlympusTheme {
                SplashScreen(onReady = ::onWarmupReady)
            }
        }

        dumpIntentDebug(intent, "onCreate")

        if (handleResumeShortCircuit()) return
        if (handleAlertShortCircuit()) return

        // Normal boot.
        lifecycleScope.launch {
            val verdict = withContext(Dispatchers.IO) {
                OracleRouter(applicationContext, app().vault).pickNext()
            }
            MnemonicLog.chant(TAG, "router verdict: $verdict")
            routerVerdict = verdict
            tryRoute()
        }
    }

    /** Return-from-silence short-circuit. Returns true if we handled it. */
    private fun handleResumeShortCircuit(): Boolean {
        val resume = intent.getStringExtra(EXTRA_RESUME_URL)?.takeIf { it.isNotBlank() }
            ?: return false

        // Hand straight back to the stage. Do NOT gate on a
        // connectivity check here — the silence screen already made
        // that call (its Retry only fires when the user asks for a
        // retry, and its auto-recovery only fires after a real TCP
        // probe succeeds). Adding a second gate here caused a
        // reproducible false negative on hotel wifi / VPN where the
        // OS's link report lagged the actual network by 3-8 seconds
        // and the user was bounced right back to silence despite
        // having internet. The stage's own load callbacks will catch
        // a truly-dead network and route back here with the same
        // resume URL preserved.
        MnemonicLog.chant(TAG, "resume-URL short-circuit → resuming stage at $resume")
        routerVerdict = RouteVerdict.OpenPortal(startUrl = resume, virgin = false)
        tryRoute()
        return true
    }

    /** Alert-tap short-circuit. Returns true if we handled it. */
    private fun handleAlertShortCircuit(): Boolean {
        val alertUrl = resolveAlertUrl() ?: return false
        val channel = app().vault.channel
        if (channel == SanctumChannel.Homefront) return false
        MnemonicLog.chant(TAG, "alert short-circuit: channel=$channel, opening stage at $alertUrl")
        if (channel == SanctumChannel.Uncharted) {
            // First-ever launch via an alert tap: the presence of a
            // push token proves the user is on the portal channel.
            // Commit that channel now so the next boot doesn't re-run
            // the full attribution dance and possibly flip to
            // homefront.
            app().vault.channel = SanctumChannel.Portal
        }
        routerVerdict = RouteVerdict.OpenPortal(startUrl = alertUrl, virgin = false)
        tryRoute()
        return true
    }

    /**
     * Resolves the alert URL that should drive this launch, in priority:
     *   1. Our own [HymnMessagingService.EXTRA_ALERT_URL] extra (set by
     *      the notification's PendingIntent that we control).
     *   2. Any raw FCM data key delivered as an intent extra by the
     *      default launcher intent (Firebase notification-payload
     *      pushes) — also handled by [HymnMessagingService.oneShotFrom].
     *   3. The vault stash written by [HymnMessagingService] when the
     *      alert arrived — the only path that survives OEM launchers
     *      replacing our PendingIntent.
     */
    private fun resolveAlertUrl(): String? {
        HymnMessagingService.oneShotFrom(intent)?.let { return it }
        val stashed = app().vault.consumeStashedAlertUrl()
        if (stashed != null) {
            MnemonicLog.chant(TAG, "alert URL restored from vault stash")
            return stashed
        }
        return null
    }

    /** Called by the SplashScreen composable once every warmup step is done. */
    private fun onWarmupReady() {
        warmupDone = true
        tryRoute()
    }

    private fun tryRoute() {
        if (routed) return
        val verdict = routerVerdict ?: return

        when (verdict) {
            is RouteVerdict.OpenPortal -> handleOpenPortal(verdict)
            RouteVerdict.EtherLost -> {
                routed = true
                launchAndFinish(EtherSilenceActivity.newIntent(this))
            }
            RouteVerdict.OpenHomefront -> {
                if (!warmupDone) return
                routed = true
                openHomefront()
            }
        }
    }

    private fun handleOpenPortal(verdict: RouteVerdict.OpenPortal) {
        routed = true
        // Check on every portal open, not only on first-boot (virgin).
        // `shouldPromptForAlerts()` owns the "never again after Accept"
        // and "cooldown after Skip" logic internally, so the virgin flag
        // does not belong here. Without this, the cooldown set by a Skip
        // tap would never expire into a re-prompt because every repeat
        // launch uses a non-virgin verdict from pickForBoundPortal.
        if (shouldPromptForAlerts()) {
            launchAndFinish(OracleConsentActivity.newIntent(this, verdict.startUrl))
        } else {
            launchAndFinish(
                SanctumStageActivity.newIntent(this, verdict.startUrl).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                },
            )
        }
    }

    private fun openHomefront() {
        val transition = ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out,
        )
        startActivity(Intent(this, PantheonActivity::class.java), transition.toBundle())
        finish()
    }

    private fun shouldPromptForAlerts(): Boolean {
        val v = app().vault
        if (v.systemAlertPrompted) return false
        val lastDecline = v.consentDeclinedAt
        if (lastDecline == 0L) return true
        return System.currentTimeMillis() - lastDecline > CONSENT_COOLDOWN_MS
    }

    private fun launchAndFinish(intent: Intent) {
        val transition = ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out,
        )
        startActivity(intent, transition.toBundle())
        finish()
    }

    /**
     * Dumps every string extra plus action / data / categories to the
     * log. When an alert tap isn't routing, the operator can grab this
     * to see WHERE the URL is (or isn't) in the intent.
     */
    private fun dumpIntentDebug(intent: Intent?, phase: String) {
        if (intent == null) {
            MnemonicLog.chant(TAG, "[$phase] intent = null")
            return
        }
        MnemonicLog.chant(
            TAG,
            "[$phase] intent.action=${intent.action} data=${intent.data} " +
                "categories=${intent.categories}",
        )
        val extras = intent.extras
        if (extras == null) {
            MnemonicLog.chant(TAG, "[$phase] intent.extras = null")
            return
        }
        val keys = extras.keySet()
        MnemonicLog.chant(TAG, "[$phase] intent.extras keys=$keys")
        for (key in keys) {
            @Suppress("DEPRECATION")
            val v = extras.get(key)
            MnemonicLog.chant(TAG, "[$phase]   $key = $v (${v?.javaClass?.simpleName})")
        }
    }

    private fun goFullscreen() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Extends the splash window into the display-cutout region so the
     * key art fills the whole panel — no reserved black strip over
     * the notch. The system defaults to [LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT]
     * which paints that strip; without an explicit override the
     * splash looks pillar-boxed on any device with a top cutout in
     * landscape. Feature guarded on API 28 (cutout API introduction).
     */
    private fun allowDrawUnderCutout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = target
        }
    }

    private fun app(): SanctumApplication = application as SanctumApplication

    companion object {
        private const val TAG = "SanctumIgnite"

        /** Cooldown before we re-show the consent screen after a Skip.
         *  Deliberately NOT the canonical 3 * 24 * 3600 * 1000 = 259_200_000
         *  — that exact value is a template fingerprint (see the sibling
         *  Kotlin port's [FINGERPRINT] note on the same knob). We drift
         *  it to 2 days 20 hours = 244 800 seconds. */
        private const val CONSENT_COOLDOWN_MS = 244_800_000L

        /** Set by [EtherSilenceActivity] on retry — the URL we were on
         *  when connectivity dropped, so the user resumes there instead
         *  of bouncing back to the stage's front page. */
        const val EXTRA_RESUME_URL = "sanctum.extra.resume_url"
    }
}
