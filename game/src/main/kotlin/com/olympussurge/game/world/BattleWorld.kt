package com.olympussurge.game.world

import com.olympussurge.engine.render.ParticlePool
import com.olympussurge.engine.render.ScreenShake
import com.olympussurge.game.arena.Arena
import com.olympussurge.game.config.Balance
import com.olympussurge.game.config.LevelDef
import com.olympussurge.game.config.Objective
import com.olympussurge.game.gems.GemLoadout
import com.olympussurge.game.gems.GemType
import com.olympussurge.game.gems.Synergy
import com.olympussurge.game.progression.RunUpgrades
import com.olympussurge.game.progression.UpgradeKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The whole combat simulation for one level.
 *
 * Entities live in preallocated parallel arrays with an `active` flag rather
 * than in lists of objects: spawning a wave then costs no allocation and the
 * update loop stays cache friendly, which is what keeps the frame budget
 * predictable on low-end phones.
 */
class BattleWorld(
    val level: LevelDef,
    private val modifiers: RunModifiers = RunModifiers(),
    private val random: Random = Random(System.nanoTime()),
) {

    enum class Phase { FIGHTING, WAVE_CLEARED, VICTORY, DEFEAT }

    /** Moments worth a sound or a flourish; the scene decides how to react. */
    enum class Event {
        BOLT_FIRED, ENEMY_HIT, ENEMY_KILLED, ELITE_KILLED, HERO_HURT,
        GEM_PICKED, SYNERGY, WAVE_DONE, ELITE_SPAWNED, BOSS_SPAWNED, WON, LOST,
    }

    var onEvent: (Event) -> Unit = {}

    val arena = Arena.of(level)
    val particles = ParticlePool(Balance.MAX_PARTICLES)
    val shake = ScreenShake()

    // --- Hero -------------------------------------------------------------

    var heroX = 0f
        private set
    var heroY = 0f
        private set
    var heroHealth = Balance.hero.maxHealth + modifiers.bonusMaxHealth
        private set
    var heroFacingRight = true
        private set
    var heroHurtTimer = 0f
        private set
    var heroMoving = false
        private set

    /** Seconds of hero movement, used to bob the sprite while walking. */
    var strideTime = 0f
        private set

    val gems = GemLoadout()
    val upgrades = RunUpgrades()

    var upgradeChoices: List<UpgradeKind> = emptyList()
        private set

    val heroMaxHealth: Float
        get() = Balance.hero.maxHealth + modifiers.bonusMaxHealth + gems.maxHealthBonus +
            upgrades.bonusMaxHealth

    private var attackTimer = 0f
    private var invulnerability = 0f
    private val heroPosition = FloatArray(2)

    /** Fury of Ares: a damage spike granted for a few seconds after a hit. */
    private var furyTimer = 0f

    /** Storm Dash trail: seconds until the next shock puff is dropped. */
    private var trailTimer = 0f

    /** Wind Shield orbit angle. */
    var shieldAngle = 0f
        private set

    // --- Enemies ----------------------------------------------------------

    val enemyActive = BooleanArray(Balance.MAX_ENEMIES)
    val enemyX = FloatArray(Balance.MAX_ENEMIES)
    val enemyY = FloatArray(Balance.MAX_ENEMIES)
    val enemyHealth = FloatArray(Balance.MAX_ENEMIES)
    val enemyMaxHealth = FloatArray(Balance.MAX_ENEMIES)
    val enemyType = arrayOfNulls<String>(Balance.MAX_ENEMIES)
    val enemyFacingRight = BooleanArray(Balance.MAX_ENEMIES)
    val enemyHurt = FloatArray(Balance.MAX_ENEMIES)
    /** Positive while the enemy is winding up a charge, for the tell. */
    val enemyWindup = FloatArray(Balance.MAX_ENEMIES)
    val enemyBob = FloatArray(Balance.MAX_ENEMIES)

    private val enemyCooldown = FloatArray(Balance.MAX_ENEMIES)
    private val enemySpawnAnim = FloatArray(Balance.MAX_ENEMIES)
    private val enemySpecial = FloatArray(Balance.MAX_ENEMIES)
    private val enemyDashX = FloatArray(Balance.MAX_ENEMIES)
    private val enemyDashY = FloatArray(Balance.MAX_ENEMIES)
    private val enemyDashTime = FloatArray(Balance.MAX_ENEMIES)
    private val enemyOrbit = FloatArray(Balance.MAX_ENEMIES)
    private val enemyScale = FloatArray(Balance.MAX_ENEMIES)

    // --- Hero projectiles -------------------------------------------------

    val boltActive = BooleanArray(Balance.MAX_PROJECTILES)
    val boltX = FloatArray(Balance.MAX_PROJECTILES)
    val boltY = FloatArray(Balance.MAX_PROJECTILES)
    val boltAngle = FloatArray(Balance.MAX_PROJECTILES)
    private val boltVx = FloatArray(Balance.MAX_PROJECTILES)
    private val boltVy = FloatArray(Balance.MAX_PROJECTILES)
    private val boltDamage = FloatArray(Balance.MAX_PROJECTILES)
    private val boltLife = FloatArray(Balance.MAX_PROJECTILES)

    // --- Enemy projectiles ------------------------------------------------

    val shotActive = BooleanArray(Balance.MAX_ENEMY_SHOTS)
    val shotX = FloatArray(Balance.MAX_ENEMY_SHOTS)
    val shotY = FloatArray(Balance.MAX_ENEMY_SHOTS)
    val shotRed = FloatArray(Balance.MAX_ENEMY_SHOTS)
    val shotGreen = FloatArray(Balance.MAX_ENEMY_SHOTS)
    val shotBlue = FloatArray(Balance.MAX_ENEMY_SHOTS)
    private val shotVx = FloatArray(Balance.MAX_ENEMY_SHOTS)
    private val shotVy = FloatArray(Balance.MAX_ENEMY_SHOTS)
    private val shotDamage = FloatArray(Balance.MAX_ENEMY_SHOTS)
    private val shotLife = FloatArray(Balance.MAX_ENEMY_SHOTS)

    // --- Gem drops --------------------------------------------------------

    val gemActive = BooleanArray(Balance.MAX_GEMS)
    val gemX = FloatArray(Balance.MAX_GEMS)
    val gemY = FloatArray(Balance.MAX_GEMS)
    val gemBob = FloatArray(Balance.MAX_GEMS)
    val gemKind = arrayOfNulls<GemType>(Balance.MAX_GEMS)

    // --- Damage numbers ---------------------------------------------------

    val floaterActive = BooleanArray(Balance.MAX_FLOATERS)
    val floaterX = FloatArray(Balance.MAX_FLOATERS)
    val floaterY = FloatArray(Balance.MAX_FLOATERS)
    val floaterLife = FloatArray(Balance.MAX_FLOATERS)
    val floaterValue = IntArray(Balance.MAX_FLOATERS)
    val floaterCrit = BooleanArray(Balance.MAX_FLOATERS)

    // --- Run state --------------------------------------------------------

    var waveIndex = 0
        private set
    var loops = 0
        private set
    var phase = Phase.FIGHTING
        private set
    var elapsed = 0f
        private set
    var kills = 0
        private set
    var gemsCollected = 0
        private set
    var lastSynergy: Synergy? = null
        private set
    var bossHealthFraction = 0f
        private set
    var bossActive = false
        private set

    private val spawnQueue = ArrayList<String>()
    private var spawnTimer = 0f
    private var waveClearedTimer = 0f
    private var bossSlain = false

    val waveCount: Int get() = level.waves.size
    val enemiesRemaining: Int get() = spawnQueue.size + enemyActive.count { it }
    val currentWave get() = level.waves[waveIndex % level.waves.size]
    val isEliteWave: Boolean get() = currentWave.elite

    /** 0..1 progress toward the level objective, for the HUD bar. */
    val objectiveProgress: Float
        get() = when (val objective = level.objective) {
            is Objective.ClearWaves ->
                (waveIndex.toFloat() / level.waves.size).coerceIn(0f, 1f)
            is Objective.Survive ->
                (elapsed / objective.seconds).coerceIn(0f, 1f)
            is Objective.CollectGems ->
                (gemsCollected.toFloat() / objective.count).coerceIn(0f, 1f)
            is Objective.SlayBoss -> if (bossSlain) 1f else 1f - bossHealthFraction
        }

    val objectiveText: String
        get() = when (val objective = level.objective) {
            is Objective.ClearWaves -> "Wave ${waveIndex + 1} / ${level.waves.size}"
            is Objective.Survive -> {
                val left = (objective.seconds - elapsed).coerceAtLeast(0f).toInt()
                "Survive ${left / 60}:${"%02d".format(left % 60)}"
            }
            is Objective.CollectGems -> "Gems $gemsCollected / ${objective.count}"
            is Objective.SlayBoss -> objective.hudLabel
        }

    init {
        repeat(modifiers.startingGems) {
            gems.add(GemType.entries[random.nextInt(GemType.entries.size)])
        }
        startWave(0)
    }

    // --- Public API -------------------------------------------------------

    /** [dirX]/[dirY] is a normalized movement request from the joystick. */
    fun update(step: Float, dirX: Float, dirY: Float) {
        if (phase == Phase.VICTORY || phase == Phase.DEFEAT) {
            particles.update(step)
            shake.update(step)
            return
        }

        elapsed += step
        updateHero(step, dirX, dirY)
        updateEnemies(step)
        updateBolts(step)
        updateShots(step)
        updateGems(step)
        updateFloaters(step)
        updateWave(step)

        particles.update(step)
        shake.update(step)
        checkObjective()
    }

    /** Applies the player's pick and moves on to the next wave. */
    fun chooseUpgrade(kind: UpgradeKind) {
        if (phase != Phase.WAVE_CLEARED) return

        val healed = upgrades.apply(kind)
        if (healed > 0f) heroHealth = heroMaxHealth
        upgradeChoices = emptyList()

        val next = waveIndex + 1
        if (next >= level.waves.size && !level.endless) {
            finish(victory = true)
            return
        }
        if (next >= level.waves.size) loops++
        startWave(next % level.waves.size)
        waveIndex = next
        phase = Phase.FIGHTING
    }

    fun consumeSynergy(): Synergy? = lastSynergy.also { lastSynergy = null }

    /** Stars earned: one for clearing, two more for the optional goals. */
    fun starsEarned(): Int {
        if (phase != Phase.VICTORY) return 0
        var stars = 1
        val goals = level.starGoals
        if (heroHealth / heroMaxHealth >= goals.healthAbove) stars++
        val secondary = (goals.kills == 0 || kills >= goals.kills) &&
            (goals.gems == 0 || gemsCollected >= goals.gems)
        if (secondary) stars++
        return stars
    }

    // --- Hero -------------------------------------------------------------

    private fun updateHero(step: Float, dirX: Float, dirY: Float) {
        val speed = Balance.hero.moveSpeed * gems.moveSpeedMultiplier *
            upgrades.moveSpeedMultiplier * modifiers.moveSpeedMultiplier

        heroMoving = dirX != 0f || dirY != 0f
        if (heroMoving) {
            heroX += dirX * speed * step
            heroY += dirY * speed * step
            strideTime += step
            if (abs(dirX) > 0.05f) heroFacingRight = dirX > 0f
            dropStormTrail(step)
        }

        heroPosition[0] = heroX
        heroPosition[1] = heroY
        arena.clampToArena(heroPosition)
        heroX = heroPosition[0]
        heroY = heroPosition[1]

        if (invulnerability > 0f) invulnerability -= step
        if (heroHurtTimer > 0f) heroHurtTimer -= step
        if (furyTimer > 0f) furyTimer -= step

        if (Synergy.WIND_SHIELD in gems.unlockedSynergies) {
            shieldAngle += step * 2.6f
            pushEnemiesFromShield(step)
        }

        attackTimer -= step
        if (attackTimer <= 0f && fireAtNearest()) {
            attackTimer = Balance.hero.attackInterval /
                (gems.attackSpeedMultiplier * upgrades.attackSpeedMultiplier *
                    modifiers.attackSpeedMultiplier)
        }
    }

    private fun damageHero(amount: Float) {
        if (invulnerability > 0f) return

        val reduction = (gems.damageReduction + modifiers.damageReduction).coerceAtMost(0.75f)
        heroHealth -= amount * (1f - reduction)
        invulnerability = Balance.hero.invulnerabilityAfterHit
        heroHurtTimer = 0.35f
        shake.add(0.34f)
        onEvent(Event.HERO_HURT)

        particles.burst(
            heroX, heroY + 60f, count = 10, speed = 210f, spread = 6.28f, direction = 0f,
            life = 0.36f, size = 16f, r = 1f, g = 0.35f, b = 0.32f, random = random,
        )

        // Fury of Ares turns punishment into a short window of extra power.
        if (Synergy.ARES_FURY in gems.unlockedSynergies) furyTimer = 4f

        if (heroHealth <= 0f) {
            heroHealth = 0f
            finish(victory = false)
        }
    }

    /** Storm Dash leaves charged puffs that shock whatever follows. */
    private fun dropStormTrail(step: Float) {
        if (Synergy.STORM_DASH !in gems.unlockedSynergies) return

        trailTimer -= step
        if (trailTimer > 0f) return
        trailTimer = 0.22f

        particles.burst(
            heroX, heroY + 20f, count = 4, speed = 60f, spread = 6.28f, direction = 0f,
            life = 0.45f, size = 20f, r = 0.55f, g = 0.8f, b = 1f, alpha = 0.8f, random = random,
        )
        damageArea(heroX, heroY, radius = 120f, damage = 4f, crit = false)
    }

    private fun pushEnemiesFromShield(step: Float) {
        val radius = 165f
        for (i in enemyActive.indices) {
            if (!enemyActive[i]) continue
            val dx = enemyX[i] - heroX
            val dy = enemyY[i] - heroY
            val distance = sqrt(dx * dx + dy * dy)
            if (distance > radius || distance < 0.001f) continue

            val push = (radius - distance) * 3.2f * step
            enemyX[i] += dx / distance * push
            enemyY[i] += dy / distance * push
        }
    }

    // --- Enemies ----------------------------------------------------------

    private fun updateEnemies(step: Float) {
        bossActive = false
        for (i in enemyActive.indices) {
            if (!enemyActive[i]) continue
            val stats = Balance.enemies.getValue(enemyType[i]!!)

            if (enemySpawnAnim[i] > 0f) enemySpawnAnim[i] -= step
            if (enemyHurt[i] > 0f) enemyHurt[i] -= step
            if (enemyWindup[i] > 0f) enemyWindup[i] -= step
            enemyBob[i] += step * (2.4f + stats.moveSpeed * 0.012f)

            if (stats.role == Balance.Role.BOSS) {
                bossActive = true
                bossHealthFraction = enemyHealth[i] / enemyMaxHealth[i]
            }

            val dx = heroX - enemyX[i]
            val dy = heroY - enemyY[i]
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            enemyFacingRight[i] = dx > 0f

            when (stats.role) {
                Balance.Role.CHASER, Balance.Role.SWARMER, Balance.Role.TANK ->
                    advance(i, stats, dx / distance, dy / distance, distance, step)

                Balance.Role.RANGED -> updateRanged(i, stats, dx, dy, distance, step)
                Balance.Role.CHARGER -> updateCharger(i, stats, dx, dy, distance, step)
                Balance.Role.DIVER -> updateDiver(i, stats, dx, dy, distance, step)
                Balance.Role.BOSS -> updateBoss(i, stats, dx, dy, distance, step)
            }

            keepInsideArena(i, stats)
            tryContactDamage(i, stats, distance, step)
        }
    }

    private fun advance(
        index: Int,
        stats: Balance.EnemyStats,
        normalX: Float,
        normalY: Float,
        distance: Float,
        step: Float,
    ) {
        if (distance <= stats.radius + HERO_RADIUS) return
        enemyX[index] += normalX * stats.moveSpeed * step
        enemyY[index] += normalY * stats.moveSpeed * step
    }

    /** Holds its preferred range and fires; backs off when crowded. */
    private fun updateRanged(
        index: Int,
        stats: Balance.EnemyStats,
        dx: Float,
        dy: Float,
        distance: Float,
        step: Float,
    ) {
        val normalX = dx / distance
        val normalY = dy / distance
        val drift = when {
            distance > stats.preferredRange * 1.15f -> 1f
            distance < stats.preferredRange * 0.75f -> -1f
            else -> 0f
        }
        enemyX[index] += normalX * stats.moveSpeed * drift * step
        enemyY[index] += normalY * stats.moveSpeed * drift * step

        enemySpecial[index] -= step
        if (enemySpecial[index] <= 0f && distance < stats.preferredRange * 1.6f) {
            enemySpecial[index] = stats.specialCooldown
            fireEnemyShot(index, stats, normalX, normalY)
        }
    }

    /** Winds up in place, then dashes through the hero's position. */
    private fun updateCharger(
        index: Int,
        stats: Balance.EnemyStats,
        dx: Float,
        dy: Float,
        distance: Float,
        step: Float,
    ) {
        if (enemyDashTime[index] > 0f) {
            enemyDashTime[index] -= step
            enemyX[index] += enemyDashX[index] * step
            enemyY[index] += enemyDashY[index] * step
            return
        }
        if (enemyWindup[index] > 0f) return

        enemySpecial[index] -= step
        if (enemySpecial[index] <= 0f && distance < 620f) {
            enemySpecial[index] = stats.specialCooldown
            enemyWindup[index] = 0.55f
            enemyDashX[index] = dx / distance * stats.moveSpeed * 4.2f
            enemyDashY[index] = dy / distance * stats.moveSpeed * 4.2f
            enemyDashTime[index] = -0.55f
            return
        }

        // Windup finished this frame: launch the dash.
        if (enemyDashTime[index] < 0f) {
            enemyDashTime[index] += step
            if (enemyDashTime[index] >= 0f) {
                enemyDashTime[index] = 0.45f
                particles.burst(
                    enemyX[index], enemyY[index] + 30f, count = 8, speed = 260f,
                    spread = 6.28f, direction = 0f, life = 0.3f, size = 18f,
                    r = 1f, g = 0.75f, b = 0.4f, random = random,
                )
            }
            return
        }

        advance(index, stats, dx / distance, dy / distance, distance, step)
    }

    /** Circles at a distance, then lunges in a short burst. */
    private fun updateDiver(
        index: Int,
        stats: Balance.EnemyStats,
        dx: Float,
        dy: Float,
        distance: Float,
        step: Float,
    ) {
        if (enemyDashTime[index] > 0f) {
            enemyDashTime[index] -= step
            enemyX[index] += enemyDashX[index] * step
            enemyY[index] += enemyDashY[index] * step
            return
        }

        enemyOrbit[index] += step * 1.7f
        val orbitRadius = 250f
        val targetX = heroX + cos(enemyOrbit[index]) * orbitRadius
        val targetY = heroY + sin(enemyOrbit[index]) * orbitRadius * 0.7f
        val toX = targetX - enemyX[index]
        val toY = targetY - enemyY[index]
        val toLength = sqrt(toX * toX + toY * toY).coerceAtLeast(0.001f)

        enemyX[index] += toX / toLength * stats.moveSpeed * step
        enemyY[index] += toY / toLength * stats.moveSpeed * step

        enemySpecial[index] -= step
        if (enemySpecial[index] <= 0f) {
            enemySpecial[index] = stats.specialCooldown
            enemyDashTime[index] = 0.42f
            enemyDashX[index] = dx / distance * stats.moveSpeed * 3.4f
            enemyDashY[index] = dy / distance * stats.moveSpeed * 3.4f
        }
    }

    /** Boss: closes to mid range, then fires a spread volley. */
    private fun updateBoss(
        index: Int,
        stats: Balance.EnemyStats,
        dx: Float,
        dy: Float,
        distance: Float,
        step: Float,
    ) {
        val normalX = dx / distance
        val normalY = dy / distance

        if (distance > stats.preferredRange) {
            enemyX[index] += normalX * stats.moveSpeed * step
            enemyY[index] += normalY * stats.moveSpeed * step
        }

        enemySpecial[index] -= step
        if (enemySpecial[index] > 0f) return

        enemySpecial[index] = stats.specialCooldown
        // Three heads, three bolts: a fan the player can side-step.
        val base = atan2(normalY, normalX)
        for (offset in -1..1) {
            val angle = base + offset * 0.28f
            fireEnemyShot(index, stats, cos(angle), sin(angle))
        }
        shake.add(0.18f)
    }

    private fun keepInsideArena(index: Int, stats: Balance.EnemyStats) {
        heroPosition[0] = enemyX[index]
        heroPosition[1] = enemyY[index]
        arena.clampToArena(heroPosition)
        enemyX[index] = heroPosition[0]
        enemyY[index] = heroPosition[1]
    }

    private fun tryContactDamage(
        index: Int,
        stats: Balance.EnemyStats,
        distance: Float,
        step: Float,
    ) {
        if (distance > stats.radius + HERO_RADIUS) return
        enemyCooldown[index] -= step
        if (enemyCooldown[index] > 0f) return

        enemyCooldown[index] = stats.attackCooldown
        damageHero(stats.contactDamage)
    }

    private fun killEnemy(index: Int) {
        val stats = Balance.enemies.getValue(enemyType[index]!!)
        enemyActive[index] = false
        kills++

        if (upgrades.lifestealPerKill > 0f) {
            heroHealth = (heroHealth + upgrades.lifestealPerKill).coerceAtMost(heroMaxHealth)
        }

        particles.burst(
            enemyX[index], enemyY[index] + stats.drawHeight * 0.35f,
            count = if (stats.elite) 34 else 14,
            speed = if (stats.elite) 460f else 260f,
            spread = 6.28f, direction = 0f,
            life = if (stats.elite) 0.75f else 0.45f,
            size = if (stats.elite) 30f else 18f,
            r = 1f, g = 0.82f, b = 0.45f, gravity = 240f, random = random,
        )

        if (random.nextFloat() < stats.gemChance * modifiers.gemChanceMultiplier) {
            dropGem(enemyX[index], enemyY[index])
        }
        if (stats.elite) {
            repeat(5) { dropGem(enemyX[index], enemyY[index]) }
            shake.add(0.5f)
            onEvent(Event.ELITE_KILLED)
        } else {
            onEvent(Event.ENEMY_KILLED)
        }

        if (stats.role == Balance.Role.BOSS) {
            bossSlain = true
            bossActive = false
        }
        val objective = level.objective
        if (objective is Objective.SlayBoss && enemyType[index] == objective.enemy) {
            bossSlain = true
        }
    }

    // --- Hero attacks -----------------------------------------------------

    /** Fires at the nearest foes; returns false when nothing is in range. */
    private fun fireAtNearest(): Boolean {
        val wanted = 1 + upgrades.extraTargets + gems.chainTargets
        var fired = 0
        var excluded = -1
        while (fired < wanted) {
            val target = nearestEnemy(heroX, heroY, Balance.hero.attackRange, excluded)
            if (target < 0) break
            fireBolt(target)
            excluded = target
            fired++
        }
        return fired > 0
    }

    private fun fireBolt(target: Int) {
        val index = boltActive.indexOfFirst { !it }
        if (index < 0) return

        var dx = enemyX[target] - heroX
        var dy = enemyY[target] - heroY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < 0.001f) return
        dx /= distance
        dy /= distance

        boltActive[index] = true
        boltX[index] = heroX
        boltY[index] = heroY + HERO_MUZZLE_HEIGHT
        boltVx[index] = dx * Balance.hero.projectileSpeed
        boltVy[index] = dy * Balance.hero.projectileSpeed
        boltAngle[index] = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        boltDamage[index] = currentBoltDamage()
        boltLife[index] = 1.4f
        onEvent(Event.BOLT_FIRED)
    }

    private fun currentBoltDamage(): Float {
        val fury = if (furyTimer > 0f) 1.6f else 1f
        return Balance.hero.attackDamage * gems.damageMultiplier *
            upgrades.damageMultiplier * modifiers.damageMultiplier * fury
    }

    private fun updateBolts(step: Float) {
        for (i in boltActive.indices) {
            if (!boltActive[i]) continue

            boltX[i] += boltVx[i] * step
            boltY[i] += boltVy[i] * step
            boltLife[i] -= step
            if (boltLife[i] <= 0f) {
                boltActive[i] = false
                continue
            }

            val hit = enemyAt(boltX[i], boltY[i])
            if (hit < 0) continue

            applyDamage(hit, boltDamage[i], crit = furyTimer > 0f)
            impactBurst(boltX[i], boltY[i])

            // Fire Lightning: bolts detonate, splashing everything nearby.
            if (Synergy.FIRE_LIGHTNING in gems.unlockedSynergies) {
                damageArea(boltX[i], boltY[i], radius = 150f, damage = boltDamage[i] * 0.55f, crit = false)
                particles.burst(
                    boltX[i], boltY[i], count = 16, speed = 320f, spread = 6.28f, direction = 0f,
                    life = 0.4f, size = 24f, r = 1f, g = 0.6f, b = 0.25f, random = random,
                )
                shake.add(0.12f)
            }
            boltActive[i] = false
        }
    }

    private fun impactBurst(x: Float, y: Float) {
        particles.burst(
            x, y, count = 7, speed = 240f, spread = 6.28f, direction = 0f,
            life = 0.26f, size = 14f, r = 0.75f, g = 0.9f, b = 1f, random = random,
        )
    }

    private fun applyDamage(enemy: Int, amount: Float, crit: Boolean) {
        enemyHealth[enemy] -= amount
        enemyHurt[enemy] = 0.12f
        spawnFloater(enemyX[enemy], enemyY[enemy], amount.toInt(), crit)
        onEvent(Event.ENEMY_HIT)
        if (enemyHealth[enemy] <= 0f) killEnemy(enemy)
    }

    private fun damageArea(x: Float, y: Float, radius: Float, damage: Float, crit: Boolean) {
        val radiusSquared = radius * radius
        for (i in enemyActive.indices) {
            if (!enemyActive[i]) continue
            val dx = enemyX[i] - x
            val dy = enemyY[i] - y
            if (dx * dx + dy * dy <= radiusSquared) applyDamage(i, damage, crit)
        }
    }

    // --- Enemy projectiles ------------------------------------------------

    private fun fireEnemyShot(
        index: Int,
        stats: Balance.EnemyStats,
        normalX: Float,
        normalY: Float,
    ) {
        val slot = shotActive.indexOfFirst { !it }
        if (slot < 0) return

        shotActive[slot] = true
        shotX[slot] = enemyX[index]
        shotY[slot] = enemyY[index] + stats.drawHeight * 0.4f
        shotVx[slot] = normalX * stats.projectileSpeed
        shotVy[slot] = normalY * stats.projectileSpeed
        shotDamage[slot] = stats.projectileDamage
        shotLife[slot] = 3f

        val tint = stats.tint
        shotRed[slot] = ((tint shr 24) and 0xFF) / 255f
        shotGreen[slot] = ((tint shr 16) and 0xFF) / 255f
        shotBlue[slot] = ((tint shr 8) and 0xFF) / 255f
    }

    private fun updateShots(step: Float) {
        for (i in shotActive.indices) {
            if (!shotActive[i]) continue

            shotX[i] += shotVx[i] * step
            shotY[i] += shotVy[i] * step
            shotLife[i] -= step
            if (shotLife[i] <= 0f) {
                shotActive[i] = false
                continue
            }

            val dx = shotX[i] - heroX
            val dy = shotY[i] - (heroY + 40f)
            if (dx * dx + dy * dy > HERO_HIT_RADIUS * HERO_HIT_RADIUS) continue

            shotActive[i] = false
            damageHero(shotDamage[i])
        }
    }

    // --- Gems -------------------------------------------------------------

    private fun dropGem(x: Float, y: Float) {
        val index = gemActive.indexOfFirst { !it }
        if (index < 0) return
        gemActive[index] = true
        gemX[index] = x + random.nextFloat() * 70f - 35f
        gemY[index] = y + random.nextFloat() * 46f - 23f
        gemBob[index] = random.nextFloat() * 6.28f
        gemKind[index] = GemType.entries[random.nextInt(GemType.entries.size)]
    }

    private fun updateGems(step: Float) {
        val pickup = Balance.hero.pickupRadius * upgrades.pickupMultiplier *
            modifiers.pickupMultiplier

        for (i in gemActive.indices) {
            if (!gemActive[i]) continue
            gemBob[i] += step * 3.2f

            val dx = heroX - gemX[i]
            val dy = heroY - gemY[i]
            val distance = sqrt(dx * dx + dy * dy)

            if (distance < pickup * 3f && distance > 0.001f) {
                val pull = Balance.hero.magnetSpeed * step
                gemX[i] += dx / distance * pull
                gemY[i] += dy / distance * pull
            }
            if (distance >= 36f) continue

            gemActive[i] = false
            gemsCollected++
            val kind = gemKind[i]!!
            val synergy = gems.add(kind)
            onEvent(Event.GEM_PICKED)
            particles.burst(
                gemX[i], gemY[i], count = 8, speed = 190f, spread = 6.28f, direction = 0f,
                life = 0.35f, size = 14f, r = kind.tintR, g = kind.tintG, b = kind.tintB,
                random = random,
            )
            if (synergy != null) {
                lastSynergy = synergy
                shake.add(0.2f)
                onEvent(Event.SYNERGY)
            }
        }
    }

    // --- Waves ------------------------------------------------------------

    private fun startWave(index: Int) {
        val wave = level.waves[index]
        spawnQueue.clear()
        for ((type, count) in wave.enemies) {
            // Endless loops add a few extra bodies per cycle instead of
            // inflating stats, so the fight gets busier but stays readable.
            val scaled = count + loops * (if (Balance.enemies.getValue(type).elite) 0 else 2)
            repeat(scaled) { spawnQueue += type }
        }
        spawnQueue.shuffle(random)
        spawnTimer = 0.5f
        if (wave.boss) onEvent(Event.BOSS_SPAWNED)
    }

    private fun updateWave(step: Float) {
        if (phase == Phase.WAVE_CLEARED) return

        if (spawnQueue.isNotEmpty()) {
            spawnTimer -= step
            if (spawnTimer <= 0f) {
                spawnEnemy(spawnQueue.removeAt(spawnQueue.lastIndex))
                spawnTimer = currentWave.spawnInterval
            }
            return
        }

        if (enemyActive.any { it }) return

        waveClearedTimer += step
        // A short beat before the upgrade screen so the last kill lands.
        if (waveClearedTimer <= 0.6f) return
        waveClearedTimer = 0f

        val last = waveIndex + 1 >= level.waves.size
        if (last && !level.endless) {
            finish(victory = true)
            return
        }
        upgradeChoices = upgrades.roll(random)
        phase = Phase.WAVE_CLEARED
        onEvent(Event.WAVE_DONE)
    }

    private fun spawnEnemy(type: String) {
        val index = enemyActive.indexOfFirst { !it }
        if (index < 0) return
        val stats = Balance.enemies.getValue(type)

        // Enemies walk in from the rim so the player always sees them coming.
        val angle = random.nextFloat() * 6.2831854f
        enemyActive[index] = true
        enemyX[index] = cos(angle) * arena.radiusX * 0.94f
        enemyY[index] = sin(angle) * arena.radiusY * 0.94f
        enemyHealth[index] = stats.health * (1f + loops * 0.35f)
        enemyMaxHealth[index] = enemyHealth[index]
        enemyType[index] = type
        enemyCooldown[index] = 0.6f
        enemyHurt[index] = 0f
        enemyWindup[index] = 0f
        enemySpawnAnim[index] = 0.45f
        enemySpecial[index] = stats.specialCooldown * (0.5f + random.nextFloat() * 0.6f)
        enemyDashTime[index] = 0f
        enemyOrbit[index] = angle
        enemyBob[index] = random.nextFloat() * 6.28f
        enemyScale[index] = 1f
        enemyFacingRight[index] = enemyX[index] < 0f

        particles.burst(
            enemyX[index], enemyY[index], count = 10, speed = 170f, spread = 6.28f,
            direction = 0f, life = 0.4f, size = 16f, r = 0.6f, g = 0.75f, b = 1f,
            random = random,
        )
        if (stats.elite) {
            shake.add(0.28f)
            onEvent(Event.ELITE_SPAWNED)
        }
    }

    fun spawnScale(index: Int): Float =
        if (enemySpawnAnim[index] <= 0f) 1f else 1f - enemySpawnAnim[index] / 0.45f * 0.35f

    // --- Effects ----------------------------------------------------------

    private fun spawnFloater(x: Float, y: Float, value: Int, crit: Boolean) {
        val index = floaterActive.indexOfFirst { !it }
        if (index < 0) return
        floaterActive[index] = true
        floaterX[index] = x
        floaterY[index] = y
        floaterLife[index] = 0.8f
        floaterValue[index] = value
        floaterCrit[index] = crit
    }

    private fun updateFloaters(step: Float) {
        for (i in floaterActive.indices) {
            if (!floaterActive[i]) continue
            floaterLife[i] -= step
            floaterY[i] += 62f * step
            if (floaterLife[i] <= 0f) floaterActive[i] = false
        }
    }

    // --- Objective --------------------------------------------------------

    private fun checkObjective() {
        if (phase != Phase.FIGHTING) return
        val done = when (val objective = level.objective) {
            is Objective.ClearWaves -> false // handled when the last wave ends
            is Objective.Survive -> elapsed >= objective.seconds
            is Objective.CollectGems -> gemsCollected >= objective.count
            is Objective.SlayBoss -> bossSlain
        }
        if (done) finish(victory = true)
    }

    private fun finish(victory: Boolean) {
        phase = if (victory) Phase.VICTORY else Phase.DEFEAT
        onEvent(if (victory) Event.WON else Event.LOST)
    }

    // --- Queries ----------------------------------------------------------

    private fun nearestEnemy(x: Float, y: Float, maxRange: Float, exclude: Int = -1): Int {
        var best = -1
        var bestDistance = maxRange * maxRange
        for (i in enemyActive.indices) {
            if (!enemyActive[i] || i == exclude) continue
            val dx = enemyX[i] - x
            val dy = enemyY[i] - y
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    private fun enemyAt(x: Float, y: Float): Int {
        for (i in enemyActive.indices) {
            if (!enemyActive[i]) continue
            val stats = Balance.enemies.getValue(enemyType[i]!!)
            val dx = enemyX[i] - x
            val dy = enemyY[i] - y
            if (dx * dx + dy * dy <= stats.radius * stats.radius) return i
        }
        return -1
    }

    private companion object {
        const val HERO_RADIUS = 34f
        const val HERO_HIT_RADIUS = 46f
        const val HERO_MUZZLE_HEIGHT = 20f
    }
}
