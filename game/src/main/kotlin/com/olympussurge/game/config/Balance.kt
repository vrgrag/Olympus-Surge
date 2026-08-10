package com.olympussurge.game.config

/**
 * Every tunable number lives here, never inline in the systems.
 *
 * Keeping balance in plain data classes means a designer pass is a diff in one
 * file, and the same structures can later be deserialized from JSON without
 * touching gameplay code.
 */
object Balance {

    data class HeroStats(
        val maxHealth: Float = 110f,
        val moveSpeed: Float = 320f,
        val attackDamage: Float = 13f,
        val attackInterval: Float = 0.52f,
        val attackRange: Float = 460f,
        val projectileSpeed: Float = 950f,
        val pickupRadius: Float = 95f,
        val magnetSpeed: Float = 660f,
        val invulnerabilityAfterHit: Float = 0.65f,
    )

    /**
     * How an enemy approaches the hero. The role drives its whole behaviour,
     * so adding a new silhouette to the roster is a data change, not new code.
     */
    enum class Role {
        /** Walks straight at the hero and hits on contact. */
        CHASER,

        /** Fast and fragile; arrives in numbers. */
        SWARMER,

        /** Slow, heavy, punishing to stand next to. */
        TANK,

        /** Keeps its distance and throws projectiles. */
        RANGED,

        /** Winds up, then charges in a straight line. */
        CHARGER,

        /** Circles the hero and dives in bursts. */
        DIVER,

        /** Boss: alternates between chasing and firing volleys. */
        BOSS,
    }

    data class EnemyStats(
        val sprite: String,
        val role: Role,
        val health: Float,
        val moveSpeed: Float,
        val contactDamage: Float,
        val attackCooldown: Float,
        val radius: Float,
        val drawHeight: Float,
        val gemChance: Float,
        val elite: Boolean = false,
        /** Distance a RANGED/BOSS enemy tries to hold, in world units. */
        val preferredRange: Float = 0f,
        val projectileDamage: Float = 0f,
        val projectileSpeed: Float = 420f,
        /** Seconds between special moves (charge, dive, volley). */
        val specialCooldown: Float = 0f,
        val tint: Long = 0xFFFFFFFF,
    )

    val hero = HeroStats()

    /**
     * Twelve silhouettes across seven roles. Difficulty comes from mixing
     * roles — a tank you must walk around while a caster punishes standing
     * still — rather than from raw stat inflation.
     */
    val enemies: Map<String, EnemyStats> = mapOf(
        BRONZE_HOPLITE to EnemyStats(
            sprite = "bronze_hoplite",
            role = Role.CHASER,
            health = 42f,
            moveSpeed = 108f,
            contactDamage = 9f,
            attackCooldown = 1.1f,
            radius = 38f,
            drawHeight = 165f,
            gemChance = 0.32f,
        ),
        SHADOW_HOPLITE to EnemyStats(
            sprite = "shadow_hoplite",
            role = Role.CHASER,
            health = 78f,
            moveSpeed = 122f,
            contactDamage = 13f,
            attackCooldown = 1.0f,
            radius = 40f,
            drawHeight = 172f,
            gemChance = 0.38f,
        ),
        SHADOW_WRAITH to EnemyStats(
            sprite = "shadow_wraith",
            role = Role.SWARMER,
            health = 28f,
            moveSpeed = 178f,
            contactDamage = 6f,
            attackCooldown = 0.85f,
            radius = 34f,
            drawHeight = 145f,
            gemChance = 0.40f,
        ),
        STONE_GUARDIAN to EnemyStats(
            sprite = "stone_guardian",
            role = Role.TANK,
            health = 210f,
            moveSpeed = 74f,
            contactDamage = 20f,
            attackCooldown = 1.5f,
            radius = 52f,
            drawHeight = 210f,
            gemChance = 0.70f,
        ),
        FLAME_WRAITH to EnemyStats(
            sprite = "flame_wraith",
            role = Role.RANGED,
            health = 52f,
            moveSpeed = 96f,
            contactDamage = 5f,
            attackCooldown = 1.4f,
            radius = 36f,
            drawHeight = 155f,
            gemChance = 0.48f,
            preferredRange = 380f,
            projectileDamage = 11f,
            projectileSpeed = 430f,
            specialCooldown = 1.9f,
            tint = 0xFFB080FF,
        ),
        FROST_WRAITH to EnemyStats(
            sprite = "frost_wraith",
            role = Role.RANGED,
            health = 60f,
            moveSpeed = 88f,
            contactDamage = 5f,
            attackCooldown = 1.4f,
            radius = 36f,
            drawHeight = 155f,
            gemChance = 0.48f,
            preferredRange = 420f,
            projectileDamage = 9f,
            projectileSpeed = 380f,
            specialCooldown = 2.3f,
            tint = 0xC8E4FFFF,
        ),
        WINGED_DEMON to EnemyStats(
            sprite = "winged_demon",
            role = Role.DIVER,
            health = 66f,
            moveSpeed = 150f,
            contactDamage = 14f,
            attackCooldown = 1.0f,
            radius = 40f,
            drawHeight = 180f,
            gemChance = 0.55f,
            specialCooldown = 3.0f,
        ),
        SPECTRAL_CHAMPION to EnemyStats(
            sprite = "spectral_champion",
            role = Role.CHARGER,
            health = 165f,
            moveSpeed = 112f,
            contactDamage = 18f,
            attackCooldown = 1.2f,
            radius = 46f,
            drawHeight = 195f,
            gemChance = 0.75f,
            specialCooldown = 4.2f,
        ),

        // --- Elites -------------------------------------------------------
        HARPY to EnemyStats(
            sprite = "harpy",
            role = Role.DIVER,
            health = 360f,
            moveSpeed = 168f,
            contactDamage = 17f,
            attackCooldown = 1.0f,
            radius = 54f,
            drawHeight = 235f,
            gemChance = 1f,
            elite = true,
            specialCooldown = 2.6f,
        ),
        MINOTAUR to EnemyStats(
            sprite = "minotaur",
            role = Role.CHARGER,
            health = 560f,
            moveSpeed = 96f,
            contactDamage = 24f,
            attackCooldown = 1.4f,
            radius = 66f,
            drawHeight = 275f,
            gemChance = 1f,
            elite = true,
            specialCooldown = 3.6f,
        ),
        MEDUSA to EnemyStats(
            sprite = "medusa",
            role = Role.RANGED,
            health = 470f,
            moveSpeed = 92f,
            contactDamage = 12f,
            attackCooldown = 1.2f,
            radius = 56f,
            drawHeight = 250f,
            gemChance = 1f,
            elite = true,
            preferredRange = 400f,
            projectileDamage = 16f,
            projectileSpeed = 470f,
            specialCooldown = 1.5f,
            tint = 0xBFFFC8FF,
        ),
        CERBERUS to EnemyStats(
            sprite = "cerberus",
            role = Role.BOSS,
            health = 1500f,
            moveSpeed = 104f,
            contactDamage = 26f,
            attackCooldown = 1.1f,
            radius = 78f,
            drawHeight = 320f,
            gemChance = 1f,
            elite = true,
            preferredRange = 300f,
            projectileDamage = 14f,
            projectileSpeed = 440f,
            specialCooldown = 3.2f,
        ),
    )

    /**
     * A wave is a budget of enemies released over time.
     *
     * Waves belong to a level rather than to a global list, so each arena can
     * introduce one new role at a time and build its own curve.
     */
    data class Wave(
        val name: String,
        val enemies: List<Pair<String, Int>>,
        val spawnInterval: Float,
        val elite: Boolean = false,
        val boss: Boolean = false,
    )

    const val BRONZE_HOPLITE = "bronze_hoplite"
    const val SHADOW_HOPLITE = "shadow_hoplite"
    const val SHADOW_WRAITH = "shadow_wraith"
    const val STONE_GUARDIAN = "stone_guardian"
    const val FLAME_WRAITH = "flame_wraith"
    const val FROST_WRAITH = "frost_wraith"
    const val WINGED_DEMON = "winged_demon"
    const val SPECTRAL_CHAMPION = "spectral_champion"
    const val HARPY = "harpy"
    const val MINOTAUR = "minotaur"
    const val MEDUSA = "medusa"
    const val CERBERUS = "cerberus"

    /** Hard caps that size the object pools; combat never allocates past them. */
    const val MAX_ENEMIES = 96
    const val MAX_PROJECTILES = 192
    const val MAX_ENEMY_SHOTS = 96
    const val MAX_GEMS = 128
    const val MAX_FLOATERS = 64
    const val MAX_PARTICLES = 512
}
