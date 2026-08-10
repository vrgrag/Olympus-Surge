package com.olympussurge.game.gems

/** The four divine energies. Every build is a mix of these. */
enum class GemType(
    val displayName: String,
    val sprite: String,
    val tintR: Float,
    val tintG: Float,
    val tintB: Float,
) {
    SAPPHIRE("Sapphire of Zeus", "sapphire_zeus", 0.30f, 0.55f, 1f),
    RUBY("Ruby of Ares", "ruby_ares", 1f, 0.32f, 0.30f),
    EMERALD("Emerald of Athena", "emerald_athena", 0.30f, 0.85f, 0.45f),
    AMETHYST("Amethyst of Hermes", "amethyst_hermes", 0.68f, 0.40f, 1f),
}

/**
 * Aggregated effect of everything the player has picked up this run.
 *
 * Gems grant flat stacks; the derived multipliers are recomputed whenever a
 * stack changes, so combat code reads plain numbers and never walks a list of
 * modifiers per frame.
 */
class GemLoadout {

    private val counts = IntArray(GemType.entries.size)

    var damageMultiplier = 1f
        private set
    var attackSpeedMultiplier = 1f
        private set
    var moveSpeedMultiplier = 1f
        private set
    var maxHealthBonus = 0f
        private set
    var damageReduction = 0f
        private set
    var chainTargets = 0
        private set

    val unlockedSynergies = LinkedHashSet<Synergy>()

    fun countOf(type: GemType): Int = counts[type.ordinal]

    fun total(): Int = counts.sum()

    /** Adds one gem and returns the synergy it unlocked, if any. */
    fun add(type: GemType): Synergy? {
        counts[type.ordinal]++
        recompute()
        return Synergy.entries.firstOrNull { synergy ->
            synergy !in unlockedSynergies && synergy.isSatisfiedBy(this)
        }?.also { unlockedSynergies += it }
    }

    private fun recompute() {
        val sapphire = counts[GemType.SAPPHIRE.ordinal]
        val ruby = counts[GemType.RUBY.ordinal]
        val emerald = counts[GemType.EMERALD.ordinal]
        val amethyst = counts[GemType.AMETHYST.ordinal]

        // Zeus speeds up the strikes, Ares makes each one hurt more, Athena
        // keeps the hero alive, Hermes moves them out of trouble.
        attackSpeedMultiplier = 1f + sapphire * 0.09f
        damageMultiplier = 1f + ruby * 0.14f
        maxHealthBonus = emerald * 12f
        damageReduction = (emerald * 0.035f).coerceAtMost(0.45f)
        moveSpeedMultiplier = 1f + amethyst * 0.055f
        chainTargets = sapphire / 3
    }
}

/** Named pairings the player can discover by mixing energies. */
enum class Synergy(
    val displayName: String,
    val description: String,
    private val first: GemType,
    private val second: GemType,
    private val threshold: Int,
) {
    FIRE_LIGHTNING(
        "Fire Lightning of Zeus",
        "Bolts detonate on impact, damaging nearby foes.",
        GemType.SAPPHIRE, GemType.RUBY, 2,
    ),
    WIND_SHIELD(
        "Wind Shield of Olympus",
        "A barrier orbits the hero and pushes enemies back.",
        GemType.EMERALD, GemType.AMETHYST, 2,
    ),
    STORM_DASH(
        "Storm Dash",
        "Movement leaves a charged trail that shocks pursuers.",
        GemType.SAPPHIRE, GemType.AMETHYST, 3,
    ),
    ARES_FURY(
        "Fury of Ares",
        "Taking damage sharply raises attack power for a while.",
        GemType.RUBY, GemType.EMERALD, 3,
    );

    fun isSatisfiedBy(loadout: GemLoadout): Boolean =
        loadout.countOf(first) >= threshold && loadout.countOf(second) >= threshold
}
