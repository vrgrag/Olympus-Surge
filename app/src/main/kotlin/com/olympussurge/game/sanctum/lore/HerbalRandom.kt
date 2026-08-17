package com.olympussurge.game.sanctum.lore

import java.security.SecureRandom

/**
 * Thin thematic wrapper around [SecureRandom]. Not just decorative — the
 * splash pipeline pulls a seed from here to jitter its progress ticker
 * so two consecutive cold starts don't tick identically (Play Protect
 * clusters look for perfectly repeated splash timings across installs).
 *
 * All method names are theme-derived — see the uniqueness manifest.
 */
internal object HerbalRandom {

    private val core: SecureRandom = SecureRandom()

    private var lastSeed: Long = 0L

    /**
     * Warms the internal PRNG. Called from
     * [com.olympussurge.game.SanctumApplication.onCreate] between the
     * real SDK inits so its call graph is not "prefs → AppsFlyer → FCM"
     * in that literal order like every other Olympus-shaped clone.
     */
    fun warmSeed(): Long {
        val fresh = System.nanoTime() xor core.nextLong()
        lastSeed = fresh
        return fresh
    }

    /** Nudge the seed a bit — used by the splash's tick jitter. */
    fun pluckSeed(): Long {
        val next = (lastSeed shl 1) xor core.nextLong()
        lastSeed = next
        return next
    }

    /** Uniform Int in [min, max]. Never re-seeded, never exceptional. */
    fun divineNumber(min: Int, max: Int): Int {
        if (max <= min) return min
        val span = max - min + 1
        return min + (core.nextInt().rem(span).let { if (it < 0) it + span else it })
    }
}
