package com.olympussurge.engine.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable

/**
 * Tiny generated textures used for bars, tracers and glows.
 *
 * Drawing these through the same SpriteBatch as the artwork avoids switching to
 * a ShapeRenderer mid-frame, which would break batching and cost draw calls.
 */
class Primitives : Disposable {

    /** Solid 1x1 white pixel: stretched into rectangles and lines. */
    val pixel: TextureRegion

    /** Soft radial falloff: glows, shadows and joystick rings. */
    val glow: TextureRegion

    /** Hard-edged ring used for the joystick outline. */
    val ring: TextureRegion

    private val textures = ArrayList<Texture>()

    init {
        pixel = TextureRegion(solidTexture())
        glow = TextureRegion(glowTexture(128))
        ring = TextureRegion(ringTexture(128, 0.86f))
    }

    private fun solidTexture(): Texture {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(1f, 1f, 1f, 1f)
        pixmap.fill()
        return Texture(pixmap).also { textures += it; pixmap.dispose() }
    }

    private fun glowTexture(size: Int): Texture {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = (size - 1) / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - center) / center
                val dy = (y - center) / center
                val distance = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(1f)
                val alpha = (1f - distance) * (1f - distance)
                pixmap.setColor(1f, 1f, 1f, alpha)
                pixmap.drawPixel(x, y)
            }
        }
        return smoothTexture(pixmap)
    }

    private fun ringTexture(size: Int, innerRatio: Float): Texture {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = (size - 1) / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - center) / center
                val dy = (y - center) / center
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                val alpha = when {
                    distance > 1f -> 0f
                    distance < innerRatio -> 0f
                    else -> 1f
                }
                pixmap.setColor(1f, 1f, 1f, alpha)
                pixmap.drawPixel(x, y)
            }
        }
        return smoothTexture(pixmap)
    }

    private fun smoothTexture(pixmap: Pixmap): Texture =
        Texture(pixmap).also {
            it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            textures += it
            pixmap.dispose()
        }

    override fun dispose() {
        textures.forEach(Texture::dispose)
        textures.clear()
    }
}
