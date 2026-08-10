package com.olympussurge.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.olympussurge.engine.assets.SpriteLibrary
import com.olympussurge.engine.render.Primitives
import com.olympussurge.game.gems.GemType
import com.olympussurge.game.world.BattleWorld

/**
 * Combat HUD: health, gem tally, wave state and the pause control.
 *
 * It is drawn inside the game's own batch rather than as a Compose overlay so
 * the readouts update on the same frame as the world they describe.
 */
class BattleHud(
    private val sprites: SpriteLibrary,
    private val primitives: Primitives,
    private val font: BitmapFont,
    private val worldWidth: Float,
    private val worldHeight: Float,
) {
    /** Pause button hit box in world coordinates. */
    val pauseX get() = worldWidth - 96f
    val pauseY get() = worldHeight - 96f
    val pauseRadius = 46f

    fun draw(batch: SpriteBatch, world: BattleWorld) {
        drawHealth(batch, world)
        drawObjective(batch, world)
        drawWave(batch, world)
        drawGemTally(batch, world)
        drawPauseButton(batch)
        drawSynergyBanner(batch, world)
        if (world.bossActive) drawBossBar(batch, world)
    }

    /**
     * The level goal sits dead centre with its own bar: it is the one thing the
     * player must keep an eye on, and it differs from level to level.
     */
    private fun drawObjective(batch: SpriteBatch, world: BattleWorld) {
        val width = 420f
        val x = worldWidth / 2f - width / 2f
        val y = worldHeight - 58f

        batch.setColor(0.03f, 0.05f, 0.12f, 0.66f)
        batch.draw(primitives.pixel, x - 14f, y - 44f, width + 28f, 74f)

        font.color = Color.valueOf("FFE7A8FF")
        font.draw(batch, world.objectiveText, x + 6f, y + 20f)

        batch.setColor(1f, 1f, 1f, 0.16f)
        batch.draw(primitives.pixel, x, y - 18f, width, 9f)
        batch.setColor(0.98f, 0.82f, 0.42f, 1f)
        batch.draw(primitives.pixel, x, y - 18f, width * world.objectiveProgress, 9f)
        batch.color = Color.WHITE
        font.color = Color.WHITE
    }

    private fun drawBossBar(batch: SpriteBatch, world: BattleWorld) {
        val width = 760f
        val x = worldWidth / 2f - width / 2f
        val y = 118f

        batch.setColor(0.05f, 0.02f, 0.05f, 0.78f)
        batch.draw(primitives.pixel, x - 8f, y - 8f, width + 16f, 40f)
        batch.setColor(0.35f, 0.06f, 0.08f, 1f)
        batch.draw(primitives.pixel, x, y, width, 24f)
        batch.setColor(0.94f, 0.28f, 0.22f, 1f)
        batch.draw(primitives.pixel, x, y, width * world.bossHealthFraction, 24f)
        batch.color = Color.WHITE

        font.color = Color.valueOf("FFC9A0FF")
        font.draw(batch, world.currentWave.name.uppercase(), x + 6f, y + 60f)
        font.color = Color.WHITE
    }

    private fun drawHealth(batch: SpriteBatch, world: BattleWorld) {
        val x = 48f
        val y = worldHeight - 78f
        val width = 460f
        val fraction = (world.heroHealth / world.heroMaxHealth).coerceIn(0f, 1f)

        batch.setColor(0.03f, 0.05f, 0.12f, 0.72f)
        batch.draw(primitives.pixel, x - 6f, y - 6f, width + 12f, 42f)

        batch.setColor(0.72f, 0.16f, 0.20f, 1f)
        batch.draw(primitives.pixel, x, y, width, 30f)
        batch.setColor(0.98f, 0.36f, 0.32f, 1f)
        batch.draw(primitives.pixel, x, y, width * fraction, 30f)

        batch.setColor(1f, 1f, 1f, 0.22f)
        batch.draw(primitives.pixel, x, y + 20f, width * fraction, 8f)
        batch.color = Color.WHITE

        font.color = Color.WHITE
        font.draw(
            batch,
            "${world.heroHealth.toInt()} / ${world.heroMaxHealth.toInt()}",
            x + 14f,
            y + 24f,
        )
    }

    private fun drawWave(batch: SpriteBatch, world: BattleWorld) {
        val wave = world.currentWave
        val label = if (wave.elite) "ELITE  ${wave.name}" else wave.name
        val x = worldWidth - 430f

        font.color = if (wave.elite) Color.valueOf("FF9A6BFF") else Color.valueOf("FFD98CFF")
        font.draw(batch, label, x, worldHeight - 150f)

        font.color = Color.valueOf("CFE0FFFF")
        font.draw(batch, "Enemies left ${world.enemiesRemaining}", x, worldHeight - 196f)
        font.color = Color.WHITE
    }

    private fun drawGemTally(batch: SpriteBatch, world: BattleWorld) {
        var x = 52f
        val y = 52f
        for (type in GemType.entries) {
            val region = sprites[type.sprite]
            val size = 62f
            val ratio = region.regionHeight.toFloat() / region.regionWidth

            batch.setColor(type.tintR, type.tintG, type.tintB, 0.35f)
            batch.draw(primitives.glow, x - 12f, y - 12f, size + 24f, size + 24f)
            batch.color = Color.WHITE
            batch.draw(region, x, y + (size - size * ratio) / 2f, size, size * ratio)

            font.color = Color.WHITE
            font.draw(batch, world.gems.countOf(type).toString(), x + size + 6f, y + 44f)
            x += size + 62f
        }
    }

    private fun drawPauseButton(batch: SpriteBatch) {
        batch.setColor(0.04f, 0.07f, 0.18f, 0.75f)
        batch.draw(
            primitives.glow,
            pauseX - pauseRadius,
            pauseY - pauseRadius,
            pauseRadius * 2f,
            pauseRadius * 2f,
        )
        batch.setColor(0.95f, 0.84f, 0.55f, 1f)
        batch.draw(primitives.pixel, pauseX - 14f, pauseY - 18f, 9f, 36f)
        batch.draw(primitives.pixel, pauseX + 5f, pauseY - 18f, 9f, 36f)
        batch.color = Color.WHITE
    }

    private fun drawSynergyBanner(batch: SpriteBatch, world: BattleWorld) {
        val synergy = world.gems.unlockedSynergies.lastOrNull() ?: return
        font.color = Color.valueOf("9BE7FFFF")
        font.draw(batch, "Synergy: ${synergy.displayName}", 52f, 150f)
        font.color = Color.WHITE
    }
}
