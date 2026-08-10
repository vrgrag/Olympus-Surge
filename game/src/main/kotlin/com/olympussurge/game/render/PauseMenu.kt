package com.olympussurge.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Align
import com.olympussurge.engine.render.Primitives

/**
 * The pause overlay: a framed panel with the state of the run and two ways out.
 *
 * Layout is derived once from the design resolution and shared by drawing and
 * hit testing, so a button can never be somewhere other than where it looks.
 */
class PauseMenu(
    private val primitives: Primitives,
    private val font: BitmapFont,
    private val worldWidth: Float,
    private val worldHeight: Float,
) {

    enum class Action { RESUME, EXIT }

    private val panelWidth = 820f
    private val panelHeight = 600f
    private val panelX = (worldWidth - panelWidth) / 2f
    private val panelY = (worldHeight - panelHeight) / 2f

    private val buttonWidth = 600f
    private val buttonHeight = 96f
    private val buttonX = (worldWidth - buttonWidth) / 2f
    private val resumeY = panelY + 172f
    private val exitY = panelY + 52f

    fun hitTest(x: Float, y: Float): Action? = when {
        inButton(x, y, resumeY) -> Action.RESUME
        inButton(x, y, exitY) -> Action.EXIT
        else -> null
    }

    private fun inButton(x: Float, y: Float, buttonY: Float): Boolean =
        x >= buttonX && x <= buttonX + buttonWidth &&
            y >= buttonY && y <= buttonY + buttonHeight

    fun draw(
        batch: SpriteBatch,
        arenaName: String,
        wave: Int,
        waveCount: Int,
        kills: Int,
        gems: Int,
        seconds: Int,
    ) {
        batch.setColor(0.01f, 0.02f, 0.06f, 0.84f)
        batch.draw(primitives.pixel, 0f, 0f, worldWidth, worldHeight)

        drawPanel(batch)

        var y = panelY + panelHeight - 56f
        font.color = GOLD
        font.draw(batch, "PAUSED", panelX, y, panelWidth, Align.center, false)

        y -= 54f
        font.color = MUTED
        font.draw(batch, arenaName.uppercase(), panelX, y, panelWidth, Align.center, false)

        y -= 84f
        drawStats(batch, y, wave, waveCount, kills, gems, seconds)

        drawButton(batch, resumeY, "RESUME", GOLD)
        drawButton(batch, exitY, "EXIT TO MENU", RED)
        font.color = Color.WHITE
    }

    private fun drawPanel(batch: SpriteBatch) {
        batch.setColor(0.04f, 0.07f, 0.17f, 0.97f)
        batch.draw(primitives.pixel, panelX, panelY, panelWidth, panelHeight)

        batch.setColor(GOLD.r, GOLD.g, GOLD.b, 0.85f)
        val edge = 4f
        batch.draw(primitives.pixel, panelX, panelY, panelWidth, edge)
        batch.draw(primitives.pixel, panelX, panelY + panelHeight - edge, panelWidth, edge)
        batch.draw(primitives.pixel, panelX, panelY, edge, panelHeight)
        batch.draw(primitives.pixel, panelX + panelWidth - edge, panelY, edge, panelHeight)
        batch.color = Color.WHITE
    }

    /** Four readouts on one line, each label sitting above its number. */
    private fun drawStats(
        batch: SpriteBatch,
        y: Float,
        wave: Int,
        waveCount: Int,
        kills: Int,
        gems: Int,
        seconds: Int,
    ) {
        val labels = arrayOf("WAVE", "KILLS", "GEMS", "TIME")
        val values = arrayOf(
            "$wave/$waveCount",
            kills.toString(),
            gems.toString(),
            "%d:%02d".format(seconds / 60, seconds % 60),
        )
        val columnWidth = (panelWidth - 80f) / labels.size

        for (i in labels.indices) {
            val columnX = panelX + 40f + i * columnWidth
            font.color = BLUE
            font.draw(batch, labels[i], columnX, y, columnWidth, Align.center, false)
            font.color = MARBLE
            font.draw(batch, values[i], columnX, y - 46f, columnWidth, Align.center, false)
        }
    }

    private fun drawButton(batch: SpriteBatch, y: Float, label: String, tint: Color) {
        batch.setColor(0.10f, 0.17f, 0.40f, 0.98f)
        batch.draw(primitives.pixel, buttonX, y, buttonWidth, buttonHeight)

        batch.setColor(tint.r, tint.g, tint.b, 0.75f)
        val edge = 3f
        batch.draw(primitives.pixel, buttonX, y, buttonWidth, edge)
        batch.draw(primitives.pixel, buttonX, y + buttonHeight - edge, buttonWidth, edge)
        batch.draw(primitives.pixel, buttonX, y, edge, buttonHeight)
        batch.draw(primitives.pixel, buttonX + buttonWidth - edge, y, edge, buttonHeight)
        batch.color = Color.WHITE

        font.color = tint
        font.draw(
            batch,
            label,
            buttonX,
            y + buttonHeight / 2f + 14f,
            buttonWidth,
            Align.center,
            false,
        )
    }

    private companion object {
        val GOLD: Color = Color.valueOf("FFD98CFF")
        val RED: Color = Color.valueOf("FF8A7AFF")
        val BLUE: Color = Color.valueOf("4FA8FFFF")
        val MARBLE: Color = Color.valueOf("F4F1E8FF")
        val MUTED: Color = Color.valueOf("9FB3D9FF")
    }
}
