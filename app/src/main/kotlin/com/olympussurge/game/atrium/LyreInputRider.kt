package com.olympussurge.game.atrium

import android.content.res.Configuration
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Panning-based IME handling for the stage host.
 *
 * Why a hand-rolled panner instead of `adjustResize` + JS scroll?
 * On most Android OEM keyboards `adjustResize` fires two independent
 * relayout waves (system inset + web engine) that fight each other for
 * ~200 ms — the focused field jitters or ends up half-hidden under the
 * keyboard. `scrollIntoView` from JS races the same relayout.
 *
 * The pan approach here:
 *   1. Strip the IME inset from the host's insets so the web engine
 *      never resizes (`withoutKeyboard()` on the OnApplyWindowInsets
 *      listener).
 *   2. Watch the IME animation via [WindowInsetsAnimationCompat] and
 *      pan the stage with `translationY` in sync — smooth, no jitter.
 *   3. Injected JS reports the focused field's geometry over the
 *      [Bridge]; we compute how much to lift so the field lands right
 *      above the keyboard with a small comfort gap.
 *
 * Usage: call [attach] once on the root, then [bind] once on the (only)
 * stage view instance we host.
 */
internal class LyreInputRider(private val host: View) {

    private var stage: WebView? = null
    private var fieldTop: Float = -1f
    private var fieldBottom: Float = -1f
    private var keyboard: Int = 0
    private var riding: Boolean = false
    private var declaredKb: Int = 0
    private var settledKb: Int = 0

    private val probe = Runnable {
        stage?.evaluateJavascript(
            "window.$JS_REPORT_HOOK && window.$JS_REPORT_HOOK();",
            null,
        )
    }

    fun attach() {
        ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
            if (!riding) {
                settle(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                apply(animated = keyboard > 0)
                if (keyboard > 0) askAgain(SETTLE_MS)
            }
            withoutKeyboard(insets)
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            host,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        riding = true
                    }
                }

                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat,
                ): WindowInsetsAnimationCompat.BoundsCompat {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        declaredKb = bounds.upperBound.bottom
                    }
                    return bounds
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    running: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat {
                    if (riding) {
                        rise(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                        apply(animated = false)
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    riding = false
                    declaredKb = 0
                    ViewCompat.requestApplyInsets(host)
                    if (keyboard > 0) askAgain(SETTLE_MS) else apply(animated = false)
                }
            },
        )
        ViewCompat.requestApplyInsets(host)
    }

    fun bind(view: WebView) {
        stage = view
        forget()
        view.translationY = 0f
        view.addJavascriptInterface(Bridge(), JS_BRIDGE_NAME)
    }

    fun forget() {
        host.removeCallbacks(probe)
        fieldTop = -1f
        fieldBottom = -1f
        apply(animated = false)
    }

    fun remeasure() {
        forget()
        settledKb = 0
        if (keyboard > 0) askAgain(SETTLE_MS)
    }

    private fun askAgain(delay: Long) {
        host.removeCallbacks(probe)
        host.postDelayed(probe, delay)
    }

    private fun settle(height: Int) {
        keyboard = height
        if (height > 0) settledKb = height
    }

    private fun rise(height: Int) {
        val rest = if (settledKb > 0) settledKb else declaredKb
        keyboard = if (rest > 0) minOf(height, rest) else height
    }

    private fun withoutKeyboard(insets: WindowInsetsCompat): WindowInsetsCompat =
        runCatching {
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.ime(), false)
                .build()
        }.getOrDefault(insets)

    private fun apply(animated: Boolean) {
        val view = stage ?: return
        val target = -offset(view)
        view.animate().cancel()
        if (animated && view.translationY != target) {
            view.animate().translationY(target).setDuration(PAN_MS).start()
        } else {
            view.translationY = target
        }
    }

    private fun offset(view: View): Float {
        val height = keyboard
        val span = view.height
        if (height <= 0 || span <= 0 || fieldBottom < 0f) return 0f
        val landscape =
            host.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val headDp = if (landscape) 48f else 96f
        val aim = minOf(
            fieldBottom,
            fieldTop + headDp * view.resources.displayMetrics.density,
        )
        val ceiling = height.toFloat()
        return (aim - (span - height)).coerceIn(0f, ceiling)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun focus(top: Double, bottom: Double) {
            val view = stage ?: return
            view.post {
                fieldTop = top.toFloat()
                fieldBottom = bottom.toFloat()
                if (keyboard > 0) apply(animated = !riding)
            }
        }
    }

    companion object {
        /** JS bridge object name — the injected page-side script talks to
         *  `window.NectarInput.focus(top, bottom)`. */
        const val JS_BRIDGE_NAME = "NectarInput"

        /** JS variable that the page-side script sets to a function
         *  callable from Kotlin to force a fresh geometry report. */
        const val JS_REPORT_HOOK = "__lyre_ping"

        private const val SETTLE_MS = 155L
        private const val PAN_MS = 175L
    }
}
