package com.olympussurge.game.atrium

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.getSystemService
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.olympussurge.game.R
import com.olympussurge.game.IgnitionActivity
import com.olympussurge.game.sanctum.lore.MnemonicLog
import com.olympussurge.game.ui.OlympusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen "no connection" fallback.
 *
 * Design points:
 *   • Back gesture is swallowed — user cannot press Back to exit the app
 *     from here.
 *   • Retry does NOT do a network probe of its own. A probe here was
 *     racing with the OS's WiFi association: when the user tapped
 *     Retry immediately after enabling WiFi, the L2 was up but
 *     `ConnectivityManager.activeNetwork` still returned null, so we
 *     wrongly bounced them right back to this screen. Instead, Retry
 *     unconditionally relaunches the pipeline (with a short spinner
 *     dwell for feedback) — the router's own offline gate handles the
 *     "still no route" case cheaply and consistently.
 *   • A one-shot [ConnectivityManager.NetworkCallback] listens for a
 *     validated INTERNET-capable network coming up while the user is
 *     staring at this screen, and auto-triggers the same relaunch. So
 *     the screen "self-heals" without the user having to tap Retry.
 */
class EtherSilenceActivity : ComponentActivity() {

    private var checking by mutableStateOf(false)
    private val relaunchGuard = AtomicBoolean(false)
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var stallAutoRetry: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extend the window into the display-cutout region so the
        // full-bleed key art paints all the way to the top edge — no
        // reserved OS black strip over the notch. Only affects this
        // activity's window. Sibling activities keep their own
        // insets treatment (the WebView stage still pads around the
        // notch: it hosts partner UI that should not disappear under
        // hardware).
        allowDrawUnderCutout()
        enableEdgeToEdge()
        goImmersive()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    /* swallow — do nothing */
                }
            },
        )

        setContent {
            OlympusTheme {
                SilenceScreen(
                    checking = checking,
                    onRetry = ::startRetry,
                )
            }
        }

        registerConnectivityWatch()
        armStallAutoRetry()
    }

    override fun onDestroy() {
        stallAutoRetry?.cancel()
        stallAutoRetry = null
        unregisterConnectivityWatch()
        super.onDestroy()
    }

    /**
     * Fires when the router bounced us here because attribution was
     * still stuck even though the pipe was up (see the "attribution
     * stall gate" in `OracleRouter.pickForFirstBoot`). We detect
     * that state cheaply: if the OS still reports an online default
     * network at the moment we opened, this open cannot be a real
     * no-Wi-Fi event — the only remaining explanation is the router
     * bounced us. The connectivity watch skips registration on the
     * same condition (a "ghost network" open would otherwise fire
     * auto-relaunch instantly and loop the user through the splash
     * forever), so we schedule a single delayed relaunch to give
     * the vendor's SDK / server pipeline more real wall-clock time
     * to converge before the router tries the waterfall again. The
     * user does not have to tap Retry.
     *
     * If they DO tap Retry earlier, the guard in [startRetry]
     * disarms the auto-retry (the launcher path becomes the same
     * activity swap; the timer would be a no-op anyway).
     */
    private fun armStallAutoRetry() {
        if (!EtherProbe.online(this)) return
        MnemonicLog.chant(
            TAG,
            "silence opened while online → arming auto-retry in ${STALL_AUTO_RETRY_MS}ms",
        )
        stallAutoRetry = lifecycleScope.launch {
            delay(STALL_AUTO_RETRY_MS)
            if (!isFinishing && !isDestroyed) {
                MnemonicLog.chant(TAG, "silence auto-retry timer fired")
                startRetry()
            }
        }
    }

    /**
     * Auto-recover path. Two safety layers guard against the infinite
     * "long splash → NoWifi flash → long splash" loop that a naive
     * network-callback triggers on some devices:
     *
     *   1. We only enable the watch when the activity opened with NO
     *      route at all. If the OS is already reporting an
     *      INTERNET-capable network at open time (rare — usually
     *      indicates a "ghost" network with the capability flag but no
     *      actual data flow), the callback would fire immediately at
     *      registration for that same ghost, triggering a retry that
     *      lands right back here. Skipping registration in that case
     *      breaks the loop; the user's Retry button still works.
     *
     *   2. When the callback does fire, we do a real TCP handshake
     *      before relaunching. `onAvailable` from the OS only tells us
     *      "some network with the INTERNET flag is up" — it does NOT
     *      tell us bytes are actually flowing. The probe is the
     *      difference between an auto-recover that always works and
     *      one that relaunches into another silence screen.
     *
     * `AtomicBoolean` guards make both firings idempotent even if the
     * OS delivers `onAvailable` for WiFi and cellular in the same
     * millisecond.
     */
    private fun registerConnectivityWatch() {
        if (EtherProbe.online(this)) {
            // Loop-guard, see (1) above. The user can still tap Retry.
            MnemonicLog.chant(TAG, "connectivity watch: skipped (already online at open)")
            return
        }
        val cm = getSystemService<ConnectivityManager>() ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                MnemonicLog.chant(TAG, "connectivity watch: onAvailable → probing")
                lifecycleScope.launch {
                    val reachable = withContext(Dispatchers.IO) {
                        EtherProbe.reachable(this@EtherSilenceActivity)
                    }
                    if (reachable) {
                        MnemonicLog.chant(TAG, "connectivity watch: probe ok → auto-relaunch")
                        startRetry()
                    } else {
                        MnemonicLog.warn(TAG, "connectivity watch: probe failed, staying on silence")
                    }
                }
            }
        }
        runCatching { cm.registerNetworkCallback(request, cb) }
            .onSuccess { netCallback = cb }
            .onFailure { MnemonicLog.warn(TAG, "connectivity watch register failed", it) }
    }

    private fun unregisterConnectivityWatch() {
        val cb = netCallback ?: return
        netCallback = null
        val cm = getSystemService<ConnectivityManager>() ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    /**
     * Retry path. Relaunches the whole app in a **fresh OS process** so
     * the attribution SDK gets a truly clean slate.
     *
     * The naive "call `.start()` again and relaunch Ignition" approach
     * fails on a very common real-world chain:
     *
     *   1. User taps a OneLink while online.
     *   2. Wi-Fi goes off before the install completes.
     *   3. App opens offline → SDK boots but its very first install-
     *      event HTTP call fails; it enters an internal exponential
     *      backoff (30-45s per vendor docs).
     *   4. User enables Wi-Fi, taps Retry inside the SAME process.
     *   5. `AppsFlyerLib.start()` on the same process is a no-op while
     *      the SDK is inside its backoff window — no new install event
     *      is dispatched, the conversion callback never fires, the
     *      attribution deferred stays pending forever, the router
     *      waterfall times out empty, we bounce back to silence, the
     *      cycle repeats. The user sees a "loading → no-wifi → loading
     *      → no-wifi" loop that only ends when the app is manually
     *      cold-launched from the launcher (fresh process resets the
     *      backoff and the SDK finally succeeds).
     *
     * The reliable fix is exactly that: force a fresh process on Retry.
     * We schedule the launcher activity via [AlarmManager] with a
     * short delay, then `exit(0)` immediately. Android brings up a
     * brand-new process for the alarm's intent (~150ms later), the SDK
     * re-initialises, and the first install-event attempt lands
     * against a clean slate. On the network side this is the same
     * cost as a manual launcher tap.
     *
     * A pending [EXTRA_RESUME_URL] is preserved across the restart so
     * a user who lost connectivity inside the WebView returns to the
     * exact same page, not the stage's front door.
     */
    private fun startRetry() {
        if (!relaunchGuard.compareAndSet(false, true)) return
        stallAutoRetry?.cancel()
        stallAutoRetry = null
        checking = true
        val resumeUrl = intent.getStringExtra(EXTRA_RESUME_URL)

        lifecycleScope.launch {
            // Short spinner dwell so the tap visibly did something
            // before the screen goes dark for the process swap.
            delay(RETRY_DWELL_MS)
            hardRestartIntoIgnition(resumeUrl)
        }
    }

    /**
     * Schedules a launcher-activity relaunch through [AlarmManager]
     * (fires ~150ms after this call, in a fresh process) then
     * terminates the current process. Android delivers the pending
     * intent to a newly-spawned process, which restarts
     * [SanctumApplication] from `onCreate` — the attribution SDK gets
     * a clean init, Firebase re-boots, no in-memory backoff carries
     * over. Uses inexact `set` to avoid the `SCHEDULE_EXACT_ALARM`
     * permission dance on Android 12+; a 150ms best-effort delay from
     * a foreground UI tap is delivered without visible slippage.
     */
    private fun hardRestartIntoIgnition(resumeUrl: String?) {
        MnemonicLog.chant(
            TAG,
            "hard restart: scheduling fresh Ignition in ${RESTART_HANDOFF_MS}ms " +
                "and terminating current process (resume=${resumeUrl != null})",
        )
        val ctx = applicationContext
        val restartIntent = Intent(ctx, IgnitionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (!resumeUrl.isNullOrBlank()) {
                putExtra(IgnitionActivity.EXTRA_RESUME_URL, resumeUrl)
            }
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(ctx, RESTART_REQUEST_CODE, restartIntent, flags)
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarm?.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + RESTART_HANDOFF_MS,
            pending,
        )
        finish()
        overridePendingTransition(0, 0)
        Runtime.getRuntime().exit(0)
    }

    private fun goImmersive() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(
            android.view.WindowInsets.Type.systemBars(),
        )
    }

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

    companion object {
        private const val TAG = "EtherSilence"

        /** Short spinner dwell on the Retry button so the tap looks
         *  like it did something before the activity swap. Not a
         *  round 500 — the sibling Kotlin port uses 500. */
        private const val RETRY_DWELL_MS = 420L

        /** Delay before the silent auto-retry timer fires. Only used
         *  when the router bounced us here because attribution was
         *  still stalled (see `OracleRouter`); a genuine "no Wi-Fi"
         *  open leaves the timer disarmed and waits for the network
         *  callback / user tap instead. Long enough to buy AppsFlyer
         *  another real attribution attempt across the wire, short
         *  enough that the silence screen doesn't feel abandoned. */
        private const val STALL_AUTO_RETRY_MS = 3_400L

        /** How long the scheduled restart alarm waits after we kill
         *  the current process before Android brings up the new one.
         *  Short enough for the user to see a single seamless swap. */
        private const val RESTART_HANDOFF_MS = 160L

        /** PendingIntent request code for the restart alarm.
         *  Arbitrary — never collides with anything else in the app. */
        private const val RESTART_REQUEST_CODE = 0x4E52 // "NR"

        /** URL the user was on when connectivity dropped — preserved so
         *  a successful Retry resumes on the exact same page. */
        const val EXTRA_RESUME_URL = "sanctum.extra.silence_resume_url"

        fun newIntent(context: Context, resumeUrl: String? = null): Intent =
            Intent(context, EtherSilenceActivity::class.java).apply {
                if (!resumeUrl.isNullOrBlank()) putExtra(EXTRA_RESUME_URL, resumeUrl)
            }
    }
}

@Composable
private fun SilenceScreen(
    checking: Boolean,
    onRetry: () -> Unit,
) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E22)),
    ) {
        // Bleed all the way to the top edge — the activity has been
        // extended under the display cutout so no OS-reserved black
        // strip clips the artwork.
        Image(
            painter = painterResource(R.drawable.bg_no_wifi),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (landscape) 32.dp else 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                    color = Color(0xFFFFC93B),
                )
            } else {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC93B),
                        contentColor = Color(0xFF15112A),
                    ),
                    border = BorderStroke(2.dp, Color(0xFF15112A)),
                    modifier = Modifier
                        .width(if (landscape) 220.dp else 260.dp)
                        .height(56.dp)
                        .border(
                            width = 3.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(28.dp),
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.no_wifi_retry),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
