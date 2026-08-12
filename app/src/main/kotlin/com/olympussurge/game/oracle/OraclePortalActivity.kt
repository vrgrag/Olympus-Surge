package com.olympussurge.game.oracle

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.olympussurge.game.BuildConfig
import com.olympussurge.game.SplashActivity
import com.olympussurge.game.oracle.config.OracleFacade
import com.olympussurge.game.oracle.gateway.OracleAttribution
import com.olympussurge.game.oracle.gateway.OracleBeacon
import com.olympussurge.game.oracle.gateway.OracleGate
import com.olympussurge.game.oracle.gateway.OracleLink
import com.olympussurge.game.oracle.gateway.OracleRelay
import com.olympussurge.game.oracle.gateway.OracleVault
import com.olympussurge.game.oracle.model.OracleMode
import com.olympussurge.game.oracle.model.OracleReply
import com.olympussurge.game.oracle.ui.OracleLoadingScreen
import com.olympussurge.game.oracle.ui.OracleShellTheme
import com.olympussurge.game.oracle.util.OracleUrlGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Locale

/**
 * Entry point and router. Shows the loading screen while it decides
 * whether this install belongs in the WebView shell or the native game.
 *
 * The state machine, by persisted [OracleMode]:
 *
 *  **Pending** (first launch)
 *    No connection → straight to the offline screen on the first frame.
 *    Nothing is started and nothing is written; the offline screen
 *    relaunches this router when the link returns.
 *    Connected → ignite AppsFlyer → attribution → config POST → decide.
 *
 *  **Web** (was the shell last time)
 *    A push URL wins outright. Then a cached link that has not expired.
 *    Otherwise a fresh config POST, falling back to the cached link and
 *    only then to the offline screen. A Web install never drops into
 *    the game because one request failed.
 *
 *  **Native** (was the game last time)
 *    The game, always, with no network work at all — which is also what
 *    makes the white part launch with the radio off. Once native, stay
 *    native, including when a push carries a URL.
 *
 * The one rule worth stating on its own: a native verdict is permanent,
 * so it has to be a real one. It is written only when the endpoint
 * genuinely answered *and* there was attribution behind the answer.
 * Everything else leaves the decision open and simply opens the game
 * for this launch.
 */
class OraclePortalActivity : ComponentActivity() {

    private lateinit var vault: OracleVault
    private lateinit var link: OracleLink
    private lateinit var gate: OracleGate

    private val tracker: OracleAttribution
        get() = (application as OracleRuntime).tracker

    private var progress by mutableFloatStateOf(0.05f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge without FLAG_LAYOUT_NO_LIMITS — the latter marks
        // the activity as translucent in Android's orientation checker
        // and trips "Only fullscreen opaque activities can request
        // orientation" on API 27+. Orientation is declared in the
        // manifest; no runtime setter is needed.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        vault = OracleVault(this)
        link = OracleLink(this)
        gate = OracleGate()
        OracleBeacon.ensureChannel(this)

        val mode = resolveMode()
        val pushUrl = OracleBeacon.extractUrl(intent)

        // A live shell can take the URL itself — no reason to tear down
        // the page the user is looking at and rebuild it from scratch.
        if (pushUrl != null && mode == OracleMode.Web && OracleRelay.offer(pushUrl)) {
            info("warm push handed to the live shell")
            finish()
            return
        }

        // Installed from a link with the radio off. Straight to the
        // offline screen: no loading bar for a decision that cannot be
        // made, and nothing written that would have to be undone.
        if (mode == OracleMode.Pending && pushUrl == null && !link.hasAnyAdapter()) {
            info("first run with no link, offline first frame")
            startActivity(Intent(this, OracleOfflineActivity::class.java))
            finish()
            return
        }

        if (pushUrl != null && mode != OracleMode.Native) vault.stashPushLink(pushUrl)

        setContent {
            OracleShellTheme {
                OracleLoadingScreen(progress = progress)
            }
        }

        lifecycleScope.launch { route(mode) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pushUrl = OracleBeacon.extractUrl(intent) ?: return

        when (vault.readMode()) {
            // A native user keeps their game. The notification was
            // already shown; the URL goes no further.
            OracleMode.Native -> info("push tap while native, game stays")
            OracleMode.Web -> {
                if (OracleRelay.offer(pushUrl)) {
                    finish()
                } else {
                    openShell(pushUrl)
                }
            }
            OracleMode.Pending -> vault.stashPushLink(pushUrl)
        }
    }

    // ─────────────────────────────────────────────────────────
    // State machine
    // ─────────────────────────────────────────────────────────

    /**
     * The persisted mode, except that a QA build with a non-sticky
     * verdict also forgets a native one it wrote earlier.
     *
     * Without this the switch would be useless on the device that
     * needs it: an organic launch locks the install into the game, and
     * `route` returns to the game before any of the re-ask logic is
     * reached. Clearing it here means clicking the OneLink and
     * relaunching is enough to get a second opinion.
     */
    private fun resolveMode(): OracleMode {
        val stored = vault.readMode()
        if (stored != OracleMode.Native) return stored
        if (BuildConfig.DEBUG && !BuildConfig.STICKY_VERDICT) {
            vault.writeMode(OracleMode.Pending)
            info("native verdict cleared for this QA build")
            return OracleMode.Pending
        }
        return stored
    }

    private suspend fun route(mode: OracleMode) {
        // Debug-only escape hatch. Deliberately checked before the mode,
        // because the situation it exists for is being locked into the
        // game by an organic verdict with no way back short of a
        // reinstall. Empty and unreachable in release builds.
        if (BuildConfig.DEBUG && BuildConfig.PROBE_LINK.isNotEmpty()) {
            info("probe link configured, shell forced without asking anyone")
            progress = 1f
            openShell(BuildConfig.PROBE_LINK)
            return
        }

        if (mode == OracleMode.Native) {
            openGame()
            return
        }
        progress = 0.12f

        if (!awaitConnection(mode)) return

        when (mode) {
            OracleMode.Web -> resolveReturning()
            else -> resolveFirstLaunch()
        }
    }

    /**
     * A launch can easily beat the radio to it, so a missing connection
     * on the first frame is worth waiting out briefly before it counts
     * as being offline. Returning users carry their last page into the
     * offline screen so a tap on Retry goes straight back to it.
     */
    private suspend fun awaitConnection(mode: OracleMode): Boolean {
        if (link.hasAnyAdapter()) return true

        val arrived = withTimeoutOrNull(OracleFacade.CONNECT_GRACE_MS) {
            link.statusStream().first { it == OracleLink.Status.Online }
        } != null
        if (arrived) return true

        val resume = if (mode == OracleMode.Web && vault.hasUsableLink()) {
            vault.readCachedLink()
        } else {
            null
        }
        startActivity(
            Intent(this, OracleOfflineActivity::class.java).apply {
                if (!resume.isNullOrEmpty()) {
                    putExtra(OracleOfflineActivity.EXTRA_RESUME_URL, resume)
                }
            },
        )
        finish()
        return false
    }

    private suspend fun resolveFirstLaunch() {
        tracker.ignite(this)
        tracker.retrace(this)
        progress = 0.35f

        val reply = askBackend(firstLaunch = true)
        progress = 0.95f

        if (reply.allowed && reply.hasLink) {
            commitWeb(reply)
            progress = 1f
            openShell(reply.link)
            return
        }

        // A "no" sticks for the lifetime of the install, so it has to
        // be one the backend actually gave, with something behind it.
        when {
            !reply.answered ->
                info("endpoint unreachable, game for now, decision left open")
            !tracker.hasAttributionData() ->
                info("nothing behind the answer, game for now, decision left open")
            !BuildConfig.STICKY_VERDICT ->
                info("backend ruled native; not persisted (QA build)")
            else -> {
                vault.writeMode(OracleMode.Native)
                info("backend ruled native (${reply.note})")
            }
        }
        progress = 1f
        openGame()
    }

    private suspend fun resolveReturning() {
        // A push link is why this launch happened; it outranks
        // everything else, including a perfectly fresh cached page.
        val pushed = OracleUrlGuard.sanitize(vault.takePushLink())
        if (pushed != null) {
            openShell(pushed)
            return
        }

        // The backend is asked on every return rather than only when
        // the cache has expired. A campaign destination can move at any
        // time, and a link the server sent without an expiry would
        // otherwise be pinned for the life of the install. The cache is
        // the fallback for when the ask does not land, not a way to
        // skip it — which is why the attribution budget here is the
        // short one.
        tracker.ignite(this)
        tracker.retrace(this)
        progress = 0.45f

        val reply = askBackend(firstLaunch = false)
        progress = 0.95f

        val cached = vault.readCachedLink()
        when {
            reply.allowed && reply.hasLink -> {
                commitWeb(reply)
                progress = 1f
                openShell(reply.link)
            }
            // Even a stale page the user can read beats an offline
            // screen they cannot. A Web install never drops into the
            // game because one request came back empty.
            !cached.isNullOrEmpty() -> {
                progress = 1f
                openShell(cached)
            }
            else -> {
                startActivity(Intent(this, OracleOfflineActivity::class.java))
                finish()
            }
        }
    }

    private suspend fun askBackend(firstLaunch: Boolean): OracleReply {
        val body = withContext(Dispatchers.IO) {
            tracker.collectBody(firstLaunch).also { stampDevice(it) }
        }
        progress = if (firstLaunch) 0.7f else 0.75f
        return gate.query(body)
    }

    private fun commitWeb(reply: OracleReply) {
        vault.writeMode(OracleMode.Web)
        reply.link?.let(vault::writeCachedLink)
        vault.writeLinkTtl(reply.ttl ?: 0L)
    }

    // ─────────────────────────────────────────────────────────
    // Request body
    // ─────────────────────────────────────────────────────────

    private suspend fun stampDevice(body: JSONObject) {
        body.put("af_id", tracker.deviceId().orEmpty())
        body.put("bundle_id", OracleFacade.PACKAGE_ID)
        body.put("os", "Android")
        body.put("store_id", OracleFacade.PACKAGE_ID)
        body.put("locale", localeTag())

        // Both keys or neither — the contract has no room for empty
        // strings. The cached token is preferred so a launch does not
        // wait on Firebase when it does not have to.
        val token = vault.readPushToken()
            ?: withTimeoutOrNull(4_000L) { OracleBeacon.fetchToken() }?.also(vault::writePushToken)
        val project = OracleFacade.messagingProject
        if (!token.isNullOrEmpty() && project.isNotEmpty()) {
            body.put("push_token", token)
            body.put("firebase_project_id", project)
        }

        stampQaOverrides(body)
    }

    /**
     * QA overrides, stamped after everything else so they win over the
     * SDK. Both are empty and unreachable in release builds.
     *
     * `af_status` is the single field the backend routes on — it answers
     * 404 "No data" for anything other than `Non-organic`, however
     * complete the rest of the body is. A directly installed APK is
     * genuinely an organic install, so without the override the WebView
     * half of the app cannot be reached at all on a dev device.
     *
     * The campaign parameters are the other half of the problem. They
     * are not a gate, they are payload: the backend echoes them into the
     * destination URL as `sub_id_*` / `extra_param_*`. An organic install
     * has none, so flipping only `af_status` yields a live URL with every
     * sub-id empty — which looks exactly like "the app sends nothing"
     * while in fact there was nothing to send.
     */
    private fun stampQaOverrides(body: JSONObject) {
        if (!BuildConfig.DEBUG) return

        val status = BuildConfig.FORCE_AF_STATUS
        if (status.isNotEmpty()) body.put("af_status", status)

        var injected = 0
        for (pair in BuildConfig.FORCE_PARAMS.split('&')) {
            val cut = pair.indexOf('=')
            if (cut <= 0) continue
            val key = pair.substring(0, cut).trim()
            if (key.isEmpty()) continue
            val value = runCatching {
                URLDecoder.decode(pair.substring(cut + 1), "UTF-8")
            }.getOrNull() ?: continue
            body.put(key, value)
            injected++
        }

        if (status.isNotEmpty() || injected > 0) {
            info("QA overrides applied: af_status=$status, $injected campaign fields")
        }
    }

    /**
     * RFC 3066 with underscores, exactly as the config contract spells
     * it: `en_US`, `ru`, `zh_Hans_CN`. Built from the full language tag
     * rather than language + country, so a locale carrying a script or
     * a variant reaches the backend intact instead of being flattened
     * to its first two subtags.
     */
    private fun localeTag(): String {
        val tag = Locale.getDefault().toLanguageTag()
        if (tag.isEmpty() || tag == "und") return "en"
        return tag.replace('-', '_')
    }

    // ─────────────────────────────────────────────────────────
    // Exits
    // ─────────────────────────────────────────────────────────

    private fun openShell(url: String?) {
        val target = OracleUrlGuard.sanitize(url)
        if (target == null) {
            openGame()
            return
        }
        val next = if (vault.shouldOfferInvite(this)) {
            Intent(this, OracleInviteActivity::class.java)
                .putExtra(OracleInviteActivity.EXTRA_TARGET_URL, target)
        } else {
            Intent(this, OracleGateActivity::class.java)
                .putExtra(OracleGateActivity.EXTRA_TARGET_URL, target)
        }
        startActivity(next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun openGame() {
        startActivity(
            Intent(this, SplashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun info(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "OraclePortal"
    }
}
