package com.olympussurge.game.config

import com.olympussurge.game.config.Balance.BRONZE_HOPLITE
import com.olympussurge.game.config.Balance.CERBERUS
import com.olympussurge.game.config.Balance.FLAME_WRAITH
import com.olympussurge.game.config.Balance.FROST_WRAITH
import com.olympussurge.game.config.Balance.HARPY
import com.olympussurge.game.config.Balance.MEDUSA
import com.olympussurge.game.config.Balance.MINOTAUR
import com.olympussurge.game.config.Balance.SHADOW_HOPLITE
import com.olympussurge.game.config.Balance.SHADOW_WRAITH
import com.olympussurge.game.config.Balance.SPECTRAL_CHAMPION
import com.olympussurge.game.config.Balance.STONE_GUARDIAN
import com.olympussurge.game.config.Balance.WINGED_DEMON
import com.olympussurge.game.config.Balance.Wave

/**
 * What the player has to do to clear a level.
 *
 * Every level still ends in combat, but the win condition changes what the
 * fight is about: holding ground, farming under pressure, or killing one big
 * thing. That variety is what keeps a dozen arenas from feeling like one arena
 * repeated.
 */
sealed interface Objective {

    /** Short line shown in the HUD while fighting. */
    val hudLabel: String

    /** Clear every wave the level defines. */
    data object ClearWaves : Objective {
        override val hudLabel = "Clear all waves"
    }

    /** Stay alive for a fixed time while enemies keep arriving. */
    data class Survive(val seconds: Int) : Objective {
        override val hudLabel = "Survive ${seconds}s"
    }

    /** Gather gems while fighting; the waves are the pressure, not the goal. */
    data class CollectGems(val count: Int) : Objective {
        override val hudLabel = "Collect $count gems"
    }

    /** Kill the level's boss; its escorts respawn until it falls. */
    data class SlayBoss(val enemy: String, val displayName: String) : Objective {
        override val hudLabel = "Slay $displayName"
    }
}

/**
 * Optional goals worth one extra star each.
 *
 * They are checked at the end of a run and never announced mid-fight, so they
 * reward how you played rather than turning the level into a checklist.
 */
data class StarGoals(
    val healthAbove: Float = 0.5f,
    val kills: Int = 0,
    val gems: Int = 0,
) {
    fun describeSecond(): String = "Finish above ${(healthAbove * 100).toInt()}% health"
    fun describeThird(): String = when {
        gems > 0 && kills > 0 -> "Collect $gems gems and defeat $kills enemies"
        gems > 0 -> "Collect $gems gems"
        else -> "Defeat $kills enemies"
    }
}

/**
 * One arena in the campaign.
 *
 * The arena artwork, the backdrop and the roster are all part of the level
 * definition, so adding a stage is a data change here and nothing else.
 */
data class LevelDef(
    val id: String,
    val index: Int,
    val name: String,
    val subtitle: String,
    val floorSprite: String,
    val backgroundSprite: String,
    val radiusX: Float,
    val radiusY: Float,
    val waves: List<Wave>,
    val objective: Objective,
    val starGoals: StarGoals,
    /** Refills the wave list forever once exhausted; used by endless. */
    val endless: Boolean = false,
)

/** The campaign: six arenas plus an endless trial unlocked at the end. */
object Levels {

    val all: List<LevelDef> = listOf(
        LevelDef(
            id = "celestial_arena",
            index = 0,
            name = "Celestial Arena",
            subtitle = "Where the chosen are tested",
            floorSprite = "celestial_olympus_arena",
            backgroundSprite = "olympus_clouds_background",
            radiusX = 620f,
            radiusY = 380f,
            objective = Objective.ClearWaves,
            starGoals = StarGoals(healthAbove = 0.6f, kills = 30),
            waves = listOf(
                Wave("Bronze Vanguard", listOf(BRONZE_HOPLITE to 6), 1.05f),
                Wave("Restless Shades", listOf(BRONZE_HOPLITE to 6, SHADOW_WRAITH to 4), 0.95f),
                Wave("Gathering Storm", listOf(SHADOW_WRAITH to 9, BRONZE_HOPLITE to 4), 0.85f),
                Wave(
                    "Shield Wall",
                    listOf(SHADOW_HOPLITE to 5, BRONZE_HOPLITE to 6),
                    0.80f,
                ),
                Wave(
                    "First Trial",
                    listOf(STONE_GUARDIAN to 2, SHADOW_WRAITH to 8),
                    0.78f,
                    elite = true,
                ),
            ),
        ),

        LevelDef(
            id = "gates_of_olympus",
            index = 1,
            name = "Gates of Olympus",
            subtitle = "Hold the threshold",
            floorSprite = "cloud_platform_gate",
            backgroundSprite = "celestial_temple_background",
            radiusX = 560f,
            radiusY = 340f,
            objective = Objective.Survive(seconds = 120),
            starGoals = StarGoals(healthAbove = 0.5f, kills = 45),
            waves = listOf(
                Wave("Probing Attack", listOf(SHADOW_WRAITH to 8), 0.85f),
                Wave("Burning Advance", listOf(FLAME_WRAITH to 3, BRONZE_HOPLITE to 7), 0.80f),
                Wave("Hammer and Anvil", listOf(STONE_GUARDIAN to 3, SHADOW_HOPLITE to 6), 0.75f),
                Wave("Endless Pressure", listOf(SHADOW_WRAITH to 12, FLAME_WRAITH to 4), 0.65f),
            ),
            endless = true,
        ),

        LevelDef(
            id = "shrine_of_storms",
            index = 2,
            name = "Shrine of Storms",
            subtitle = "Gather the divine sparks",
            floorSprite = "cloud_platform_shrine",
            backgroundSprite = "ancient_ruins_background",
            radiusX = 540f,
            radiusY = 330f,
            objective = Objective.CollectGems(count = 24),
            starGoals = StarGoals(healthAbove = 0.55f, gems = 32),
            waves = listOf(
                Wave("Frozen Scouts", listOf(FROST_WRAITH to 4, SHADOW_WRAITH to 6), 0.85f),
                Wave("Crossfire", listOf(FLAME_WRAITH to 4, FROST_WRAITH to 4), 0.80f),
                Wave("Descent", listOf(WINGED_DEMON to 5, SHADOW_HOPLITE to 5), 0.72f),
                Wave("Storm Surge", listOf(SHADOW_WRAITH to 14, FROST_WRAITH to 5), 0.62f),
            ),
            endless = true,
        ),

        LevelDef(
            id = "harpy_roost",
            index = 3,
            name = "Roost of the Harpy",
            subtitle = "Something circles above",
            floorSprite = "cloud_platform_wide",
            backgroundSprite = "olympus_clouds_background",
            radiusX = 660f,
            radiusY = 390f,
            objective = Objective.SlayBoss(HARPY, "the Harpy"),
            starGoals = StarGoals(healthAbove = 0.5f, kills = 40),
            waves = listOf(
                Wave("Feathered Scouts", listOf(WINGED_DEMON to 5), 0.90f),
                Wave("Screeching Flock", listOf(WINGED_DEMON to 6, SHADOW_WRAITH to 8), 0.75f),
                Wave(
                    "The Harpy",
                    listOf(HARPY to 1, WINGED_DEMON to 4, SHADOW_WRAITH to 6),
                    1.00f,
                    elite = true,
                    boss = true,
                ),
            ),
        ),

        LevelDef(
            id = "labyrinth_ruins",
            index = 4,
            name = "Ruins of the Labyrinth",
            subtitle = "The beast remembers",
            floorSprite = "cloud_platform_large",
            backgroundSprite = "ancient_ruins_background",
            radiusX = 680f,
            radiusY = 400f,
            objective = Objective.SlayBoss(MINOTAUR, "the Minotaur"),
            starGoals = StarGoals(healthAbove = 0.45f, kills = 55),
            waves = listOf(
                Wave("Stone Sentries", listOf(STONE_GUARDIAN to 3, SHADOW_HOPLITE to 5), 0.85f),
                Wave("Champions of Dust", listOf(SPECTRAL_CHAMPION to 3, FROST_WRAITH to 4), 0.75f),
                Wave("Gaze of Medusa", listOf(MEDUSA to 1, SHADOW_WRAITH to 8), 0.90f, elite = true),
                Wave(
                    "Labyrinth Beast",
                    listOf(MINOTAUR to 1, SHADOW_HOPLITE to 8, STONE_GUARDIAN to 2),
                    1.00f,
                    elite = true,
                    boss = true,
                ),
            ),
        ),

        LevelDef(
            id = "throne_of_hades",
            index = 5,
            name = "Throne of Hades",
            subtitle = "The hound guards the gate",
            floorSprite = "celestial_olympus_arena",
            backgroundSprite = "celestial_temple_background",
            radiusX = 640f,
            radiusY = 385f,
            objective = Objective.SlayBoss(CERBERUS, "Cerberus"),
            starGoals = StarGoals(healthAbove = 0.4f, kills = 70),
            waves = listOf(
                Wave("Gatekeepers", listOf(SPECTRAL_CHAMPION to 4, STONE_GUARDIAN to 3), 0.80f),
                Wave("Infernal Choir", listOf(FLAME_WRAITH to 6, WINGED_DEMON to 5), 0.70f),
                Wave("Twin Terrors", listOf(MEDUSA to 1, HARPY to 1, SHADOW_WRAITH to 8), 0.95f, elite = true),
                Wave(
                    "The Hound of Hades",
                    listOf(CERBERUS to 1, SPECTRAL_CHAMPION to 4, SHADOW_HOPLITE to 8),
                    1.05f,
                    elite = true,
                    boss = true,
                ),
            ),
        ),

        LevelDef(
            id = "endless_trial",
            index = 6,
            name = "Endless Trial",
            subtitle = "How long can you stand?",
            floorSprite = "cloud_platform_large",
            backgroundSprite = "olympus_clouds_background",
            radiusX = 640f,
            radiusY = 385f,
            objective = Objective.Survive(seconds = 600),
            starGoals = StarGoals(healthAbove = 0.3f, kills = 150),
            waves = listOf(
                Wave("Wave", listOf(BRONZE_HOPLITE to 8, SHADOW_WRAITH to 6), 0.80f),
                Wave("Wave", listOf(SHADOW_HOPLITE to 7, FLAME_WRAITH to 4), 0.75f),
                Wave("Wave", listOf(STONE_GUARDIAN to 3, FROST_WRAITH to 5, SHADOW_WRAITH to 8), 0.70f),
                Wave("Wave", listOf(WINGED_DEMON to 6, SPECTRAL_CHAMPION to 3), 0.65f),
                Wave("Wave", listOf(HARPY to 1, SHADOW_WRAITH to 12), 0.80f, elite = true),
                Wave("Wave", listOf(MINOTAUR to 1, SHADOW_HOPLITE to 10), 0.80f, elite = true),
                Wave("Wave", listOf(MEDUSA to 1, FLAME_WRAITH to 6, WINGED_DEMON to 6), 0.70f, elite = true),
                Wave("Wave", listOf(CERBERUS to 1, SPECTRAL_CHAMPION to 5), 0.90f, elite = true, boss = true),
            ),
            endless = true,
        ),
    )

    fun byId(id: String): LevelDef = all.firstOrNull { it.id == id } ?: all.first()

    /**
     * The first arena is always open. Every subsequent one requires the
     * previous arena to appear in [clearedIds] (i.e. to have been beaten at
     * least once, regardless of star count).
     */
    fun isUnlocked(level: LevelDef, clearedIds: Set<String>): Boolean =
        level.index == 0 || all.getOrNull(level.index - 1)?.id in clearedIds
}
