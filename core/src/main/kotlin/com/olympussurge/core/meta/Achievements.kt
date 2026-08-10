package com.olympussurge.core.meta

import com.olympussurge.core.save.PlayerProfile

/**
 * Achievements are derived from the profile counters rather than stored.
 *
 * Nothing can drift out of sync that way, and progress bars come for free:
 * each entry knows both its goal and how far the player currently is.
 */
enum class Achievement(
    val title: String,
    val description: String,
    val goal: Int,
) {
    FIRST_STEPS("First Descent", "Finish your first run", 1),
    VETERAN("Veteran of Olympus", "Finish 10 runs", 10),
    SLAYER("Slayer", "Defeat 250 enemies", 250),
    EXTERMINATOR("Storm Incarnate", "Defeat 1000 enemies", 1000),
    COLLECTOR("Gem Collector", "Gather 100 gems", 100),
    DEEP_RUNNER("Deep Runner", "Reach wave 4", 4),
    CHAMPION("Champion of the Gods", "Win a run", 1),
    RICH("Keeper of Essence", "Hold 2000 essence at once", 2000),
    ;

    fun progressOf(profile: PlayerProfile): Int = when (this) {
        FIRST_STEPS, VETERAN -> profile.runs
        SLAYER, EXTERMINATOR -> profile.totalKills
        COLLECTOR -> profile.totalGems
        DEEP_RUNNER -> profile.bestWave
        CHAMPION -> profile.victories
        RICH -> profile.essence
    }

    fun isUnlocked(profile: PlayerProfile): Boolean = progressOf(profile) >= goal

    fun fraction(profile: PlayerProfile): Float =
        (progressOf(profile).toFloat() / goal).coerceIn(0f, 1f)
}
