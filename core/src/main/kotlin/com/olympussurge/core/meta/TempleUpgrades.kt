package com.olympussurge.core.meta

/**
 * Permanent upgrades bought at the Temple of Olympus with essence.
 *
 * Unlike in-run blessings these persist forever, so each step is deliberately
 * small: the temple should shorten the climb, not replace the skill of a run.
 * Cost grows linearly with the level, which keeps the last ranks aspirational
 * without turning them into a grind wall.
 */
enum class TempleUpgrade(
    val title: String,
    val effectPerLevel: String,
    val maxLevel: Int,
    val baseCost: Int,
    val iconName: String,
) {
    VITALITY("Blessing of Athena", "+8 max health", 10, 120, "icon_vitality"),
    WRATH("Wrath of Ares", "+6% bolt damage", 10, 150, "icon_wrath"),
    SWIFTNESS("Sandals of Hermes", "+4% movement speed", 8, 140, "icon_swiftness"),
    STORM("Favour of Zeus", "+5% attack speed", 10, 160, "icon_storm"),
    FORTUNE("Fortune of Olympus", "+10% gem drop chance", 6, 200, "icon_fortune"),
    MAGNET("Call of the Stones", "+15% pickup range", 6, 130, "icon_magnet"),
    RESILIENCE("Aegis Plating", "-3% damage taken", 6, 220, "icon_resilience"),
    STARTING_GEM("Divine Endowment", "Start the run with a gem", 3, 300, "icon_starting_gem"),
    ;

    /** Essence needed to go from [level] to the next one; 0 when maxed. */
    fun costAt(level: Int): Int =
        if (level >= maxLevel) 0 else baseCost * (level + 1)
}

/**
 * Flat bonuses derived from temple levels, ready to hand to the battle scene.
 *
 * Resolving the levels once here keeps the combat code free of lookups and
 * makes the numbers easy to display on the temple screen.
 */
data class MetaBonuses(
    val bonusMaxHealth: Float = 0f,
    val damageMultiplier: Float = 1f,
    val moveSpeedMultiplier: Float = 1f,
    val attackSpeedMultiplier: Float = 1f,
    val gemChanceMultiplier: Float = 1f,
    val pickupMultiplier: Float = 1f,
    val damageReduction: Float = 0f,
    val startingGems: Int = 0,
) {
    companion object {
        fun from(levels: Map<TempleUpgrade, Int>): MetaBonuses = MetaBonuses(
            bonusMaxHealth = 8f * levelOf(levels, TempleUpgrade.VITALITY),
            damageMultiplier = 1f + 0.06f * levelOf(levels, TempleUpgrade.WRATH),
            moveSpeedMultiplier = 1f + 0.04f * levelOf(levels, TempleUpgrade.SWIFTNESS),
            attackSpeedMultiplier = 1f + 0.05f * levelOf(levels, TempleUpgrade.STORM),
            gemChanceMultiplier = 1f + 0.10f * levelOf(levels, TempleUpgrade.FORTUNE),
            pickupMultiplier = 1f + 0.15f * levelOf(levels, TempleUpgrade.MAGNET),
            damageReduction = 0.03f * levelOf(levels, TempleUpgrade.RESILIENCE),
            startingGems = levelOf(levels, TempleUpgrade.STARTING_GEM),
        )

        private fun levelOf(levels: Map<TempleUpgrade, Int>, upgrade: TempleUpgrade): Int =
            levels[upgrade] ?: 0
    }
}
