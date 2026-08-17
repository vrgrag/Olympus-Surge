package com.olympussurge.game.atrium.push

import java.util.ArrayDeque

/**
 * Process-wide hand-off for remote alerts targeting the stage.
 *
 * The stage activity flips [stageAlive] true in `onCreate` (and back
 * to false in `onDestroy`), and installs [onLiveAlert] in `onStart`
 * (clearing it in `onStop`). A background delivery can land exactly in
 * the window where the callback is null even though the stage is still
 * alive, so the relay also keeps a tiny [queue] that gets drained on
 * the next `onStart`.
 *
 * A short dedupe window protects against the notification tap racing
 * with the drain: `onStart` fires first and drains the queue, then
 * `onNewIntent` arrives with the same URL — without dedupe we'd load
 * the same page twice.
 */
internal object HymnRelay {

    @Volatile
    var stageAlive: Boolean = false

    @Volatile
    var onLiveAlert: ((String) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) drain(value)
        }

    private val queue = ArrayDeque<String>()

    /** Same URL delivered twice within this window is coalesced. */
    private const val DEDUPE_WINDOW_MS = 2300L
    private var lastDelivered: String? = null
    private var lastDeliveredAt: Long = 0L

    @Synchronized
    fun handOff(url: String): Boolean {
        val cb = onLiveAlert
        if (cb != null) {
            emit(cb, url)
            return true
        }
        if (stageAlive) queue.addLast(url)
        return false
    }

    /**
     * The stage calls this immediately after it has already loaded the
     * URL via the cold-tap path, so the drain pass doesn't re-load the
     * same page.
     */
    @Synchronized
    fun markSeen(url: String) {
        lastDelivered = url
        lastDeliveredAt = System.currentTimeMillis()
    }

    @Synchronized
    private fun drain(cb: (String) -> Unit) {
        while (queue.isNotEmpty()) emit(cb, queue.removeFirst())
    }

    private fun emit(cb: (String) -> Unit, url: String) {
        val now = System.currentTimeMillis()
        if (url == lastDelivered && now - lastDeliveredAt < DEDUPE_WINDOW_MS) return
        lastDelivered = url
        lastDeliveredAt = now
        cb(url)
    }
}
