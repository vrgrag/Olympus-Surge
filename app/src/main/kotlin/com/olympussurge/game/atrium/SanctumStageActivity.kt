package com.olympussurge.game.atrium

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.olympussurge.game.R
import com.olympussurge.game.sanctum.lore.HerbalRandom
import com.olympussurge.game.sanctum.lore.MnemonicLog
import com.olympussurge.game.atrium.push.HymnMessagingService
import com.olympussurge.game.atrium.push.HymnRelay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The remote content stage.
 *
 * Design goals, distilled from the operator brief:
 *
 *   • Zero user-facing permissions. Uploads ride on Storage Access
 *     Framework (`ACTION_GET_CONTENT`).
 *   • Keyboard never covers the focused field. Uses a custom
 *     [LyreInputRider] that pans the stage with `translationY` in sync
 *     with the IME animation — `adjustResize` alone is not enough on
 *     most Android keyboards (fights the web engine's own layout).
 *   • Redirect-chain recovery. Partner funnels can chain 30–60 hops;
 *     Chromium cuts off at 20. We reload from the deepest successful
 *     hop and hold an opaque loading cover across every retry so the
 *     user never sees the "green robot" system error page — the cover
 *     also refuses to drop while `loadFailed` is set (the failing
 *     load's own onPageFinished cannot blink it off).
 *   • Live connectivity. If the connection drops mid-session we jump
 *     to [EtherSilenceActivity]; a short debounce keeps VPN flickers
 *     from tripping it.
 *   • Back gesture never closes the stage (spec §14).
 *   • Fully immersive. System bars stay hidden.
 *   • Warm alert hand-off. Registers with [HymnRelay] in onStart /
 *     onStop so a foreground alert loads inline (no notification) and
 *     a background tap wakes this activity via `onNewIntent`.
 */
class SanctumStageActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var stageBox: FrameLayout
    private lateinit var cover: FrameLayout
    private lateinit var stage: WebView
    private lateinit var rider: LyreInputRider

    private var initialUrl: String = "about:blank"
    private var deepestHop: String? = null

    /** The last URL the stage actually SETTLED on (real onPageFinished,
     *  not the redirect trail). Used as the resume URL when we get
     *  bounced to [EtherSilenceActivity] so Retry lands the user back
     *  on the exact page they were on, not on the stage's front page. */
    private var lastMainFrameUrl: String? = null
    private var redirectRetries = 0
    private var retryPending = false
    private var loadFailed = false

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = pendingFileCallback ?: return@registerForActivityResult
        pendingFileCallback = null
        val data = result.data
        val uris: Array<Uri> = when {
            result.resultCode != RESULT_OK -> emptyArray()
            data?.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { clip.getItemAt(it).uri }
            }
            data?.data != null -> arrayOf(data.data!!)
            else -> emptyArray()
        }
        callback.onReceiveValue(uris)
    }

    private val connectivityManager: ConnectivityManager?
        get() = getSystemService()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var offlineJob: Job? = null

    /** Cached JS assembled from res/raw fragments on the first use.
     *  Reused across page loads so we're not re-reading resources per
     *  navigation. */
    private var jsVeil: String? = null
    private var jsDock: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HymnRelay.stageAlive = true

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        goImmersive()

        val start = intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.isNotBlank() }
        if (start == null) {
            finish()
            return
        }
        initialUrl = start

        buildScaffold()
        rider = LyreInputRider(root)
        rider.attach()
        buildStage()

        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back is disabled entirely inside the offer. Both:
                //   • WebView history walk (would eventually rewind to
                //     the first page — user reported this as a bug),
                //   • Activity close (would drop the user out of the
                //     offer altogether).
                // The user stays exactly where they are; the only exit
                // is a partner-provided in-page control or a genuine
                // connectivity drop that triggers the silence screen.
            }
        })

        watchConnectivity()
        raiseCover()
        stage.loadUrl(initialUrl)
    }

    /**
     * Handles new intents arriving while this activity is already in
     * the back stack or at the top. Two sources:
     *
     *   1. A notification PendingIntent we built ourselves (data-only
     *      push, app in background): carries [HymnMessagingService.EXTRA_ALERT_URL].
     *
     *   2. [com.olympussurge.game.IgnitionActivity] rerouting us after a
     *      cold-start FCM notification-payload push or a no-internet
     *      resume: the launcher builds the intent via [newIntent] which
     *      carries [EXTRA_START_URL]. We must handle that key here too —
     *      without this the WebView silently stays on the current page
     *      instead of loading the push URL.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val alertUrl =
            HymnMessagingService.oneShotFrom(intent)
                ?: intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.isNotBlank() }
                ?: return
        MnemonicLog.chant(TAG, "onNewIntent: loading url → $alertUrl")
        resetLoadState()
        raiseCover()
        stage.loadUrl(alertUrl)
        HymnRelay.markSeen(alertUrl)
    }

    private fun goImmersive() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rider.remeasure()
        ViewCompat.requestApplyInsets(stageBox)
    }

    // ------------------------------------------------------------------
    //  UI construction (imperative FrameLayouts, not Compose — Compose
    //  around a WebView + WindowInsetsAnimationCallback is fragile).
    // ------------------------------------------------------------------

    private fun buildScaffold() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        stageBox = FrameLayout(this)
        root.addView(stageBox, matchParent())

        // Safe-area padding — portrait: top only; landscape: sides only.
        // Bottom is always 0 (LyreInputRider owns bottom). IME insets
        // are stripped upstream by the rider so they never reach here.
        ViewCompat.setOnApplyWindowInsetsListener(stageBox) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val landscape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (landscape) v.setPadding(bars.left, 0, bars.right, 0)
            else v.setPadding(0, bars.top, 0, 0)
            insets
        }

        cover = buildCoverView()
        root.addView(cover, matchParent())
    }

    private fun buildCoverView(): FrameLayout {
        val box = FrameLayout(this).apply {
            // Fully opaque brand-dark background. A translucent scrim
            // over a black stage looked like "black screen with a tiny
            // spinner" during long redirect chains — the cover must
            // read as "the app is loading", not as a system error.
            setBackgroundColor(COVER_BG)
            visibility = View.GONE
            isClickable = true // swallow taps behind
        }
        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList =
                android.content.res.ColorStateList.valueOf(SPINNER_COLOR)
        }
        val size = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics,
        ).toInt()
        box.addView(spinner, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        return box
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildStage() {
        stage = WebView(this)
        stageBox.removeAllViews()
        stageBox.addView(stage, matchParent())

        stage.setBackgroundColor(STAGE_BG)
        applyStageSettings(stage.settings)
        WebView.setWebContentsDebuggingEnabled(false)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(stage, true)
        }

        stage.webViewClient = StageClient()
        stage.webChromeClient = StageChrome()

        rider.bind(stage)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyStageSettings(s: WebSettings) {
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mediaPlaybackRequiresUserGesture = false
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(false)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.userAgentString = strippedUa(s.userAgentString)
    }

    /** Drops the "; wv" marker that identifies Chromium-based webviews. */
    private fun strippedUa(raw: String): String =
        raw.replace("; wv", "").replace("wv) ", ") ")

    // ------------------------------------------------------------------
    //  Cover
    // ------------------------------------------------------------------

    private fun raiseCover() {
        cover.visibility = View.VISIBLE
        cover.bringToFront()
    }

    private fun dropCover() {
        // Never drop while a redirect retry cycle is running: the
        // failing load's own onPageFinished fires on the committed
        // error page ("chrome-error://") and would otherwise blink the
        // cover off between retries. See handleAnyMainFrameError.
        if (retryPending || loadFailed) return
        cover.visibility = View.GONE
    }

    private fun resetLoadState() {
        deepestHop = null
        redirectRetries = 0
        loadFailed = false
        retryPending = false
    }

    // ------------------------------------------------------------------
    //  Connectivity monitoring
    // ------------------------------------------------------------------

    private fun watchConnectivity() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                offlineJob?.cancel()
                offlineJob = null
            }

            override fun onLost(network: Network) {
                scheduleOfflineCheck()
            }
        }
        networkCallback = callback
        cm.registerNetworkCallback(request, callback)
    }

    private fun scheduleOfflineCheck() {
        offlineJob?.cancel()
        offlineJob = lifecycleScope.launch {
            delay(OFFLINE_DEBOUNCE_MS)
            if (!EtherProbe.online(applicationContext)) {
                val resume = lastMainFrameUrl ?: deepestHop
                MnemonicLog.chant(
                    TAG,
                    "offline detected mid-session — opening silence, resume=$resume",
                )
                startActivity(EtherSilenceActivity.newIntent(this@SanctumStageActivity, resume))
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        HymnRelay.onLiveAlert = { url ->
            runOnUiThread {
                MnemonicLog.chant(TAG, "live alert arrived → loading")
                resetLoadState()
                raiseCover()
                stage.loadUrl(url)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        HymnRelay.onLiveAlert = null
    }

    override fun onPause() {
        stage.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        stage.onResume()
        goImmersive()
    }

    override fun onDestroy() {
        HymnRelay.stageAlive = false
        networkCallback?.let {
            runCatching { connectivityManager?.unregisterNetworkCallback(it) }
        }
        (stage.parent as? ViewGroup)?.removeView(stage)
        stage.stopLoading()
        stage.destroy()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    //  Web client
    // ------------------------------------------------------------------

    private inner class StageClient : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (!url.isNullOrBlank() && url != "about:blank" &&
                url.startsWith("http", ignoreCase = true)
            ) {
                deepestHop = url
                loadFailed = false
            }
            raiseCover()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            // Bail on our recovery scratch-pad, and on the committed
            // Chromium error document that follows a failed load.
            if (url == null || url == "about:blank" || loadFailed) return
            lastMainFrameUrl = url
            retryPending = false
            redirectRetries = 0
            sprinkleVeil()
            sprinkleDock()
            dropCover()
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val url = request.url ?: return false
            val scheme = url.scheme.orEmpty().lowercase(Locale.ROOT)
            if (scheme in IN_APP_SCHEMES) {
                if (request.isForMainFrame) deepestHop = url.toString()
                return false
            }
            return openExternally(url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            super.onReceivedError(view, request, error)
            if (!request.isForMainFrame) return
            MnemonicLog.warn(
                TAG,
                "onReceivedError code=${error.errorCode} desc=${error.description}",
            )
            handleAnyMainFrameError(view)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (!request.isForMainFrame) return
            MnemonicLog.warn(TAG, "onReceivedHttpError status=${errorResponse.statusCode}")
            handleAnyMainFrameError(view)
        }

        /**
         * Uniform recovery from ANY main-frame failure. Keeps the
         * spinner cover up (both via [raiseCover] and the [loadFailed]
         * / [retryPending] guards in [dropCover]), wipes whatever the
         * stage was about to paint by loading `about:blank`, then
         * bounces back to the deepest successful hop after a beat.
         * Retries are capped; after that the cover stays up and the
         * connectivity monitor picks up if the pipe is truly broken.
         */
        private fun handleAnyMainFrameError(view: WebView) {
            loadFailed = true
            retryPending = true
            raiseCover()
            runCatching {
                view.stopLoading()
                view.loadUrl("about:blank")
            }
            val target = deepestHop
            if (target == null || redirectRetries >= MAX_REDIRECT_HOPS) return
            redirectRetries++
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    loadFailed = false
                    view.loadUrl(target)
                }
            }, REDIRECT_RETRY_DELAY_MS)
        }

        private fun openExternally(url: Uri): Boolean {
            return runCatching {
                val intent = Intent(Intent.ACTION_VIEW, url).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                true
            }.recoverCatching {
                MnemonicLog.warn(TAG, "failed to open external url: $url", it)
                false
            }.getOrDefault(false)
        }
    }

    // ------------------------------------------------------------------
    //  Chrome client (media, permissions, file picker)
    // ------------------------------------------------------------------

    private inner class StageChrome : WebChromeClient() {
        private var fullscreenView: View? = null

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            // Cover drop is driven exclusively by onPageFinished on a
            // successful settle. Progress-based drops flashed the
            // error page during redirect retries.
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden()
                return
            }
            fullscreenView = view
            (window.decorView as? ViewGroup)?.addView(view)
            stage.visibility = View.GONE
        }

        override fun onHideCustomView() {
            val v = fullscreenView ?: return
            (window.decorView as? ViewGroup)?.removeView(v)
            fullscreenView = null
            stage.visibility = View.VISIBLE
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }

        override fun onShowFileChooser(
            stageView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = filePathCallback

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = fileChooserParams.acceptTypes
                    .firstOrNull { it.isNotBlank() } ?: "*/*"
                if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }
            return try {
                fileChooserLauncher.launch(intent)
                true
            } catch (e: ActivityNotFoundException) {
                MnemonicLog.warn(TAG, "no file chooser available", e)
                pendingFileCallback = null
                false
            }
        }
    }

    // ------------------------------------------------------------------
    //  Injected JS: safe-area override + keyboard field reporter
    //  Both scripts are stored as raw resources and assembled per
    //  install in a per-boot random order — the fragments are all
    //  idempotent IIFEs so order does not matter functionally, but it
    //  breaks static "same JS in every clone" fingerprints.
    // ------------------------------------------------------------------

    private fun sprinkleVeil() {
        val js = jsVeil ?: assembleFragments(R.raw.veil_head, R.raw.veil_tail).also { jsVeil = it }
        stage.evaluateJavascript(js, null)
    }

    private fun sprinkleDock() {
        val js = jsDock ?: assembleFragments(R.raw.dock_head, R.raw.dock_tail).also { jsDock = it }
        stage.evaluateJavascript(js, null)
    }

    private fun assembleFragments(vararg resIds: Int): String {
        val order = resIds.toMutableList()
        // Per-boot shuffle. The fragments are idempotent so any order
        // is valid; the shuffle just varies the assembled string
        // fingerprint per install.
        if (order.size > 1 && HerbalRandom.divineNumber(0, 1) == 1) {
            order.reverse()
        }
        val sb = StringBuilder(4096)
        for (id in order) {
            resources.openRawResource(id).bufferedReader().use { reader ->
                sb.append(reader.readText())
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    companion object {
        private const val TAG = "SanctumStage"
        private const val EXTRA_START_URL = "sanctum.extra.start_url"

        /** Chromium caps redirect chains at 20; partner funnels sometimes
         *  need 3+ hops of 20 to reach the final destination. */
        private const val MAX_REDIRECT_HOPS = 7
        private const val REDIRECT_RETRY_DELAY_MS = 95L

        /** VPN flickers reconnect within ~500ms; wait a beat before nuking. */
        private const val OFFLINE_DEBOUNCE_MS = 720L

        private const val COVER_BG = 0xFF0B0B0F.toInt()
        private const val STAGE_BG = 0xFF0B0B0F.toInt()
        private const val SPINNER_COLOR = 0xFFF2C464.toInt()

        private val IN_APP_SCHEMES = setOf("http", "https", "about", "data", "blob")

        fun newIntent(context: Context, url: String): Intent =
            Intent(context, SanctumStageActivity::class.java).apply {
                putExtra(EXTRA_START_URL, url)
            }
    }
}
