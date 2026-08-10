package com.olympussurge.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.olympussurge.engine.render.Primitives
import com.olympussurge.game.progression.UpgradeKind

/**
 * The between-waves upgrade choice: three cards, one tap.
 *
 * Layout is computed once from the design resolution so hit testing and drawing
 * cannot drift apart, and every card is large enough to hit with a thumb.
 */
class UpgradePanel(
    private val primitives: Primitives,
    private val font: BitmapFont,
    private val worldWidth: Float,
    private val worldHeight: Float,
) {
    private val cardWidth = 420f
    private val cardHeight = 470f
    private val spacing = 56f

    private val cardY = worldHeight / 2f - cardHeight / 2f - 20f

    fun cardX(index: Int): Float {
        val totalWidth = cardWidth * 3 + spacing * 2
        return worldWidth / 2f - totalWidth / 2f + index * (cardWidth + spacing)
    }

    /** Returns the index of the card under the point, or -1. */
    fun hitTest(x: Float, y: Float, cardCount: Int): Int {
        for (i in 0 until cardCount) {
            val left = cardX(i)
            if (x >= left && x <= left + cardWidth && y >= cardY && y <= cardY + cardHeight) {
                return i
            }
        }
        return -1
    }

    fun draw(batch: SpriteBatch, choices: List<UpgradeKind>) {
        batch.setColor(0.02f, 0.03f, 0.09f, 0.82f)
        batch.draw(primitives.pixel, 0f, 0f, worldWidth, worldHeight)
        batch.color = Color.WHITE

        font.color = Color.valueOf("FFD98CFF")
        font.draw(batch, "CHOOSE A BLESSING", worldWidth / 2f - 215f, worldHeight - 110f)
        font.color = Color.valueOf("BFD4FFFF")
        font.draw(
            batch,
            "The gods reward your surge",
            worldWidth / 2f - 200f,
            worldHeight - 162f,
        )
        font.color = Color.WHITE

        choices.forEachIndexed { index, kind -> drawCard(batch, index, kind) }
    }

    private fun drawCard(batch: SpriteBatch, index: Int, kind: UpgradeKind) {
        val x = cardX(index)
        val tint = Color(kind.rarity.tint.toInt())

        batch.setColor(0.07f, 0.11f, 0.24f, 0.96f)
        batch.draw(primitives.pixel, x, cardY, cardWidth, cardHeight)

        batch.setColor(tint.r, tint.g, tint.b, 0.9f)
        batch.draw(primitives.pixel, x, cardY, cardWidth, 6f)
        batch.draw(primitives.pixel, x, cardY + cardHeight - 6f, cardWidth, 6f)
        batch.draw(primitives.pixel, x, cardY, 6f, cardHeight)
        batch.draw(primitives.pixel, x + cardWidth - 6f, cardY, 6f, cardHeight)

        batch.setColor(tint.r, tint.g, tint.b, 0.28f)
        batch.draw(
            primitives.glow,
            x + cardWidth / 2f - 150f,
            cardY + cardHeight - 300f,
            300f,
            300f,
        )
        batch.color = Color.WHITE

        font.color = tint
        font.draw(batch, kind.rarity.label, x + 30f, cardY + cardHeight - 34f)

        font.color = Color.valueOf("FFF3D6FF")
        font.draw(batch, kind.title, x + 30f, cardY + 240f, cardWidth - 60f, -1, true)

        font.color = Color.valueOf("BCCBE8FF")
        font.draw(batch, kind.description, x + 30f, cardY + 160f, cardWidth - 60f, -1, true)

        font.color = Color.valueOf("FFD98CFF")
        font.draw(batch, "TAP TO TAKE", x + 30f, cardY + 56f)
        font.color = Color.WHITE
    }
}
