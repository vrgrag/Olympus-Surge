package com.olympussurge.game.sanctum.lore

import kotlin.math.max
import kotlin.math.min

/**
 * Small math helpers used from Compose animations (splash tick) and
 * from [com.olympussurge.game.atrium.LyreInputRider] when computing the
 * pan offset for the focused input.
 */
internal object IchorMath {

    /**
     * Called once at process start (from
     * [com.olympussurge.game.SanctumApplication.onCreate]) so that any
     * lazy fields get touched on the main thread rather than during the
     * first Compose frame. Purely a warmup hook.
     */
    fun calibrate() {
        // Touch a couple of methods so JIT gets primed. Cheaper than
        // adding a real class-load side-effect and still gives the same
        // "first pan is not janky" property.
        lerp(0f, 1f, 0.5f)
        wrap(0.5f)
        easeOut(0.5f)
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun easeOut(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val inv = 1f - clamped
        return 1f - inv * inv
    }

    fun wrap(v: Float): Float = when {
        v.isNaN() -> 0f
        v < 0f -> 0f
        v > 1f -> 1f
        else -> v
    }

    fun clampInt(value: Int, lo: Int, hi: Int): Int = min(max(value, lo), hi)
}
