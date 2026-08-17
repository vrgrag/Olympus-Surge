package com.olympussurge.game.sanctum.lore

import android.content.Context
import com.olympussurge.game.R

/**
 * Central directory of the ambient thematic strings that ship with the
 * app. Ownership lives here so the resource shrinker sees a real static
 * reference to each string id and does not strip them.
 *
 * Consumed by [MnemonicLog] during boot to emit a single line of
 * flavour text into the debug log — harmless in release builds and a
 * pleasant hint of the theme when tailing logcat.
 */
internal object LoreBook {

    /** All resource ids of the thematic pool. */
    private val ALL: IntArray = intArrayOf(
        R.string.sanctum_lore_dawn,
        R.string.sanctum_lore_forge,
        R.string.sanctum_lore_muses,
        R.string.sanctum_lore_oracle,
        R.string.sanctum_lore_herald,
        R.string.sanctum_lore_ambrosia,
        R.string.sanctum_lore_hymn,
        R.string.sanctum_lore_pegasus,
        R.string.sanctum_lore_chariot,
        R.string.sanctum_lore_phalanx,
        R.string.sanctum_lore_tempest,
        R.string.sanctum_lore_manna,
        R.string.sanctum_lore_kleos,
        R.string.sanctum_lore_zephyr,
    )

    /** Picks one line at random. Never throws. */
    fun pickLine(context: Context): String {
        val id = ALL[HerbalRandom.divineNumber(0, ALL.size - 1)]
        return runCatching { context.getString(id) }.getOrDefault("")
    }
}
