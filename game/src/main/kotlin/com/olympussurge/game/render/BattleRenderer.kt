package com.olympussurge.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.olympussurge.engine.assets.SpriteLibrary
import com.olympussurge.engine.input.VirtualJoystick
import com.olympussurge.engine.math.IsoProjection
import com.olympussurge.engine.render.Primitives
import com.olympussurge.game.config.Balance
import com.olympussurge.game.gems.Synergy
import com.olympussurge.game.world.BattleWorld
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the battle in back-to-front order.
 *
 * Everything standing on the floor is sorted by world Y so nearer figures
 * overlap farther ones, which is what sells the tilted camera given the
 * artwork is a set of flat, front-facing sprites.
 */
class BattleRenderer(
    private val sprites: SpriteLibrary,
    private val primitives: Primitives,
    private val font: BitmapFont,
    private val worldWidth: Float,
    private val worldHeight: Float,
) {
    private val centerX = worldWidth / 2f
    private val centerY = worldHeight * 0.46f

    /** Camera shake offset for the current frame, applied to world space only. */
    private var shakeX = 0f
    private var shakeY = 0f

    /** Sort buffer for floor-standing entities; reused every frame. */
    private val order = IntArray(Balance.MAX_ENEMIES + 1)
    private val orderDepth = FloatArray(Balance.MAX_ENEMIES + 1)

    fun screenX(worldX: Float): Float = centerX + IsoProjection.screenX(worldX) + shakeX

    fun screenY(worldY: Float): Float = centerY + IsoProjection.screenY(worldY) + shakeY

    fun draw(batch: SpriteBatch, world: BattleWorld, joystick: VirtualJoystick) {
        shakeX = world.shake.offsetX
        shakeY = world.shake.offsetY

        drawBackground(batch, world)
        drawFloor(batch, world)
        drawGems(batch, world)
        drawActors(batch, world)
        drawEnemyShots(batch, world)
        drawBolts(batch, world)
        drawParticles(batch, world)
        drawFloaters(batch, world)
        drawJoystick(batch, joystick)
    }

    private fun drawBackground(batch: SpriteBatch, world: BattleWorld) {
        val region = sprites[world.arena.backgroundSprite]
        batch.setColor(0.62f, 0.70f, 0.92f, 1f)
        // The backdrop drifts at a fraction of the shake so the arena feels
        // like it is further away than the fighters standing on it.
        batch.draw(region, shakeX * 0.25f, shakeY * 0.25f, worldWidth, worldHeight)
        batch.color = Color.WHITE
    }

    private fun drawFloor(batch: SpriteBatch, world: BattleWorld) {
        val region = sprites[world.arena.floorSprite]
        val width = world.arena.radiusX * 2f / FLOOR_PLATE_RATIO
        val height = width * region.regionHeight / region.regionWidth
        batch.draw(
            region,
            centerX - width / 2f + shakeX,
            centerY - height * FLOOR_CENTER_RATIO + shakeY,
            width,
            height,
        )
    }

    private fun drawGems(batch: SpriteBatch, world: BattleWorld) {
        for (i in world.gemActive.indices) {
            if (!world.gemActive[i]) continue
            val kind = world.gemKind[i] ?: continue
            val region = sprites[kind.sprite]

            val bob = sin(world.gemBob[i]) * 8f
            val x = screenX(world.gemX[i])
            val y = screenY(world.gemY[i]) + bob
            val size = 52f
            val ratio = region.regionHeight.toFloat() / region.regionWidth

            batch.setColor(kind.tintR, kind.tintG, kind.tintB, 0.5f)
            batch.draw(primitives.glow, x - size, y - size * 0.7f, size * 2f, size * 1.4f)
            batch.color = Color.WHITE
            batch.draw(region, x - size / 2f, y - size * ratio / 2f, size, size * ratio)
        }
    }

    /** Hero and enemies share one depth-sorted pass. */
    private fun drawActors(batch: SpriteBatch, world: BattleWorld) {
        var count = 0
        for (i in world.enemyActive.indices) {
            if (!world.enemyActive[i]) continue
            order[count] = i
            orderDepth[count] = world.enemyY[i]
            count++
        }
        order[count] = HERO_SLOT
        orderDepth[count] = world.heroY
        count++

        sortByDepthDescending(count)

        for (slot in 0 until count) {
            val index = order[slot]
            if (index == HERO_SLOT) drawHero(batch, world) else drawEnemy(batch, world, index)
        }
    }

    /** Insertion sort: the list is small and nearly ordered between frames. */
    private fun sortByDepthDescending(count: Int) {
        for (i in 1 until count) {
            val entity = order[i]
            val depth = orderDepth[i]
            var j = i - 1
            while (j >= 0 && orderDepth[j] < depth) {
                order[j + 1] = order[j]
                orderDepth[j + 1] = orderDepth[j]
                j--
            }
            order[j + 1] = entity
            orderDepth[j + 1] = depth
        }
    }

    private fun drawHero(batch: SpriteBatch, world: BattleWorld) {
        val region = sprites["chosen_olympus_warrior"]
        val x = screenX(world.heroX)
        val y = screenY(world.heroY)

        // A short vertical bob plus a squash on the same beat reads as a stride
        // without needing per-frame animation art.
        val stride = if (world.heroMoving) sin(world.strideTime * 13f) else 0f
        val height = 168f * (1f + abs(stride) * 0.035f)
        val width = height * region.regionWidth / region.regionHeight
        val lift = abs(stride) * 7f

        drawShadow(batch, x, y, width * 0.55f * (1f - abs(stride) * 0.08f))

        if (world.heroHurtTimer > 0f) batch.setColor(1f, 0.55f, 0.5f, 1f)
        val flip = !world.heroFacingRight
        batch.draw(
            region,
            x - width / 2f + if (flip) width else 0f,
            y + lift,
            if (flip) -width else width,
            height,
        )
        batch.color = Color.WHITE

        if (Synergy.WIND_SHIELD in world.gems.unlockedSynergies) drawWindShield(batch, world, x, y)
    }

    private fun drawWindShield(batch: SpriteBatch, world: BattleWorld, x: Float, y: Float) {
        batch.setColor(0.55f, 0.95f, 0.8f, 0.30f)
        batch.draw(primitives.ring, x - 165f, y - 165f * IsoProjection.TILT + 40f, 330f, 330f * IsoProjection.TILT)
        for (i in 0 until 3) {
            val angle = world.shieldAngle + i * 2.094f
            val orbX = x + cos(angle) * 158f
            val orbY = y + sin(angle) * 158f * IsoProjection.TILT + 40f
            batch.setColor(0.75f, 1f, 0.9f, 0.75f)
            batch.draw(primitives.glow, orbX - 26f, orbY - 26f, 52f, 52f)
        }
        batch.color = Color.WHITE
    }

    private fun drawEnemy(batch: SpriteBatch, world: BattleWorld, index: Int) {
        val type = world.enemyType[index] ?: return
        val stats = Balance.enemies.getValue(type)
        val region = sprites[stats.sprite]

        val scale = world.spawnScale(index)
        val bob = sin(world.enemyBob[index])
        val height = stats.drawHeight * scale
        val width = height * region.regionWidth / region.regionHeight
        val x = screenX(world.enemyX[index])
        val y = screenY(world.enemyY[index])
        // Fliers hover clear of the floor; walkers only bounce a little.
        val flying = stats.role == Balance.Role.DIVER
        val lift = if (flying) 46f + bob * 12f else abs(bob) * 5f

        drawShadow(batch, x, y, width * 0.5f * if (flying) 0.75f else 1f)

        when {
            world.enemyHurt[index] > 0f -> batch.setColor(1f, 0.6f, 0.55f, 1f)
            // A charge tell: the sprite flares before it launches.
            world.enemyWindup[index] > 0f -> batch.setColor(1f, 0.85f, 0.45f, 1f)
            scale < 1f -> batch.setColor(1f, 1f, 1f, scale)
        }
        val flip = !world.enemyFacingRight[index]
        batch.draw(
            region,
            x - width / 2f + if (flip) width else 0f,
            y + lift,
            if (flip) -width else width,
            height,
        )
        batch.color = Color.WHITE

        if (!stats.elite) {
            drawEnemyHealth(batch, world, index, x, y + height + lift + 12f, width * 0.8f)
        }
    }

    private fun drawEnemyHealth(
        batch: SpriteBatch,
        world: BattleWorld,
        index: Int,
        x: Float,
        y: Float,
        width: Float,
    ) {
        val fraction = world.enemyHealth[index] / world.enemyMaxHealth[index]
        if (fraction >= 0.999f) return

        batch.setColor(0f, 0f, 0f, 0.55f)
        batch.draw(primitives.pixel, x - width / 2f - 2f, y - 2f, width + 4f, 10f)
        batch.setColor(0.85f, 0.22f, 0.24f, 1f)
        batch.draw(primitives.pixel, x - width / 2f, y, width * fraction, 6f)
        batch.color = Color.WHITE
    }

    private fun drawShadow(batch: SpriteBatch, x: Float, y: Float, radius: Float) {
        batch.setColor(0.04f, 0.06f, 0.15f, 0.38f)
        batch.draw(primitives.glow, x - radius, y - radius * 0.32f, radius * 2f, radius * 0.64f)
        batch.color = Color.WHITE
    }

    private fun drawBolts(batch: SpriteBatch, world: BattleWorld) {
        for (i in world.boltActive.indices) {
            if (!world.boltActive[i]) continue
            val x = screenX(world.boltX[i])
            val y = screenY(world.boltY[i])

            batch.setColor(0.45f, 0.75f, 1f, 0.55f)
            batch.draw(primitives.glow, x - 26f, y - 26f, 52f, 52f)

            batch.setColor(0.85f, 0.95f, 1f, 1f)
            batch.draw(
                primitives.pixel,
                x, y,
                0f, 3f,
                46f, 6f,
                1f, 1f,
                world.boltAngle[i],
            )
            batch.color = Color.WHITE
        }
    }

    private fun drawEnemyShots(batch: SpriteBatch, world: BattleWorld) {
        for (i in world.shotActive.indices) {
            if (!world.shotActive[i]) continue
            val x = screenX(world.shotX[i])
            val y = screenY(world.shotY[i])

            batch.setColor(world.shotRed[i], world.shotGreen[i], world.shotBlue[i], 0.55f)
            batch.draw(primitives.glow, x - 34f, y - 34f, 68f, 68f)
            batch.setColor(1f, 1f, 1f, 0.9f)
            batch.draw(primitives.glow, x - 13f, y - 13f, 26f, 26f)
            batch.color = Color.WHITE
        }
    }

    private fun drawParticles(batch: SpriteBatch, world: BattleWorld) {
        val pool = world.particles
        for (i in pool.active.indices) {
            if (!pool.active[i]) continue
            val fraction = pool.fraction(i)
            val size = pool.size[i] * (0.35f + fraction * 0.65f)
            batch.setColor(pool.red[i], pool.green[i], pool.blue[i], fraction * pool.alpha[i])
            batch.draw(
                primitives.glow,
                screenX(pool.x[i]) - size,
                screenY(pool.y[i]) - size,
                size * 2f,
                size * 2f,
            )
        }
        batch.color = Color.WHITE
    }

    private fun drawFloaters(batch: SpriteBatch, world: BattleWorld) {
        for (i in world.floaterActive.indices) {
            if (!world.floaterActive[i]) continue
            val alpha = (world.floaterLife[i] / 0.8f).coerceIn(0f, 1f)
            font.color = if (world.floaterCrit[i]) {
                Color(1f, 0.75f, 0.25f, alpha)
            } else {
                Color(1f, 1f, 1f, alpha)
            }
            font.draw(
                batch,
                world.floaterValue[i].toString(),
                screenX(world.floaterX[i]),
                screenY(world.floaterY[i]) + 120f,
            )
        }
        font.color = Color.WHITE
    }

    private fun drawJoystick(batch: SpriteBatch, joystick: VirtualJoystick) {
        if (!joystick.active) return

        batch.setColor(1f, 1f, 1f, 0.22f)
        batch.draw(primitives.ring, joystick.originX - 130f, joystick.originY - 130f, 260f, 260f)
        batch.setColor(0.95f, 0.85f, 0.55f, 0.75f)
        batch.draw(primitives.glow, joystick.knobX - 46f, joystick.knobY - 46f, 92f, 92f)
        batch.color = Color.WHITE
    }

    private companion object {
        const val HERO_SLOT = -1

        // The arena artwork is wider than its walkable plate; these ratios map
        // the play-space ellipse onto the painted floor.
        const val FLOOR_PLATE_RATIO = 0.84f
        const val FLOOR_CENTER_RATIO = 0.52f
    }
}
