package com.olympussurge.game.sanctum.lore

import android.util.Log

/**
 * Thematic log fascade. Every shell class routes its `Log.*` calls
 * through here so:
 *
 *   1. Log tags share a distinctive prefix that does not match any
 *      other Olympus-shaped port ("Sanctum·<Component>"), which is a
 *      light static-analysis fingerprint.
 *   2. A single kill-switch (`silence`) can gate all shell logging in
 *      a release build without hunting through 15 files.
 */
internal object MnemonicLog {

    /** Flip to true in a release build if we ever want silent shells. */
    @Volatile
    var silence: Boolean = false

    private const val ROOT = "Sanctum"

    fun chant(component: String, message: String) {
        if (silence) return
        Log.i("$ROOT\u00B7$component", message)
    }

    fun murmur(component: String, message: String) {
        if (silence) return
        Log.d("$ROOT\u00B7$component", message)
    }

    fun warn(component: String, message: String, err: Throwable? = null) {
        if (silence) return
        if (err != null) Log.w("$ROOT\u00B7$component", message, err)
        else Log.w("$ROOT\u00B7$component", message)
    }

    fun cry(component: String, message: String, err: Throwable? = null) {
        if (silence) return
        if (err != null) Log.e("$ROOT\u00B7$component", message, err)
        else Log.e("$ROOT\u00B7$component", message)
    }
}
