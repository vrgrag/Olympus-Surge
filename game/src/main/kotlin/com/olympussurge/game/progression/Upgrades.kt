package com.olympussurge.game.progression

import kotlin.random.Random

/**
 * Between-wave upgrades: the run-scoped half of progression.
 *
 * Each pick is a flat, readable step ("+25% bolt damage"), never a hidden
 * percentage, so the player can reason about the build they are assembling.
 */
enum class UpgradeKind(
    val title: String,
    val description: String,
    val rarity: Rarity,
) {
    BOLT_DAMAGE("Wrath of Zeus", "+25% bolt damage", Rarity.COMMON),
    ATTACK_SPEED("Swift Judgement", "+20% attack speed", Rarity.COMMON),
    MOVE_SPEED("Winged Sandals", "+15% movement speed", Rarity.COMMON),
    MAX_HEALTH("Aegis of Athena", "+30 max health, fully healed", Rarity.RARE),
    PICKUP_RADIUS("Call of the Gems", "+60% gem pickup range", Rarity.COMMON),
    MULTISHOT("Split Lightning", "Bolts strike one extra foe", Rarity.EPIC),
    LIFESTEAL("Blood of Ares", "Heal 1 health per kill", Rarity.RARE),
    ;

    enum class Rarity(val label: String, val tint: Long) {
        COMMON("COMMON", 0x9FC4FFFF),
        RARE("RARE", 0x6BE0A8FF),
        EPIC("EPIC", 0xD79BFFFF),
    }
}

/** Accumulated effect of every upgrade chosen during the current run. */
class RunUpgrades {

    var damageMultiplier = 1f
        private set
    var attackSpeedMultiplier = 1f
        private set
    var moveSpeedMultiplier = 1f
        private set
    var bonusMaxHealth = 0f
        private set
    var pickupMultiplier = 1f
        private set
    var extraTargets = 0
        private set
    var lifestealPerKill = 0f
        private set

    val taken = ArrayList<UpgradeKind>()

    /** Returns bonus health to grant immediately, if the pick heals. */
    fun apply(kind: UpgradeKind): Float {
        taken += kind
        when (kind) {
            UpgradeKind.BOLT_DAMAGE -> damageMultiplier += 0.25f
            UpgradeKind.ATTACK_SPEED -> attackSpeedMultiplier += 0.20f
            UpgradeKind.MOVE_SPEED -> moveSpeedMultiplier += 0.15f
            UpgradeKind.MAX_HEALTH -> {
                bonusMaxHealth += 30f
                return 30f
            }
            UpgradeKind.PICKUP_RADIUS -> pickupMultiplier += 0.60f
            UpgradeKind.MULTISHOT -> extraTargets += 1
            UpgradeKind.LIFESTEAL -> lifestealPerKill += 1f
        }
        return 0f
    }

    /**
     * Rolls three distinct options. Multishot is limited so a lucky run cannot
     * trivialise later waves by stacking it every time.
     */
    fun roll(random: Random): List<UpgradeKind> {
        val pool = UpgradeKind.entries.filter { kind ->
            kind != UpgradeKind.MULTISHOT || extraTargets < 2
        }
        return pool.shuffled(random).take(3)
    }
}
