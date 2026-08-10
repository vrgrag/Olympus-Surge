package com.olympussurge.game.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import com.olympussurge.engine.render.Primitives
import kotlin.math.roundToInt

/**
 * Loading screen drawn by the game itself.
 *
 * It lives in the GL scene rather than as a Compose overlay so the bar and the
 * asset manager share a thread and a frame: the bar shows the real fraction of
 * textures resident on the GPU and it vanishes on the exact frame the arena is
 * ready.
 *
 * Two backdrops ship with the game, one per orientation. Only the one the
 * device actually needs is uploaded, and it is released the moment loading
 * finishes rather than lingering for the whole run.
 */
class LoadingScreen(
    private val primitives: Primitives,
    private val font: BitmapFont,
    private val worldWidth: Float,
    private val worldHeight: Float,
) : Disposable {

    private var landscape: Texture? = null
    private var portrait: Texture? = null

    /** Drives the "Loading." / ".." / "..." cycle; real time, not frame count. */
    private var dotClock = 0f

    /**
     * Draws one frame of the loading screen.
     *
     * [progress] is the raw asset-manager fraction and [done] is true only on
     * the frame every upload has finished. The bar is capped just below full
     * until then, so the last sliver is a promise the loader has actually
     * kept — never a bar that sits at 97% waiting on something.
     */
    fun draw(batch: SpriteBatch, progress: Float, done: Boolean) {
        val shown = if (done) 1f else progress.coerceIn(0f, 0.97f)
        val isPortrait = Gdx.graphics.height > Gdx.graphics.width
        if (!done) dotClock += Gdx.graphics.deltaTime

        batch.color = Color.WHITE
        drawBackdrop(batch, isPortrait)

        // The art is busy at the bottom, so the readout sits on its own scrim.
        batch.setColor(0.02f, 0.04f, 0.11f, 0.62f)
        batch.draw(primitives.pixel, 0f, 0f, worldWidth, worldHeight * 0.26f)
        batch.color = Color.WHITE

        drawBar(batch, shown)
        drawLabels(batch, shown, done)
    }

    /** Cycles "Loading." -> "Loading.." -> "Loading..." -> "Loading." forever. */
    private fun loadingDots(): String {
        val step = ((dotClock / 0.45f).toInt() % 3) + 1
        return "Loading" + ".".repeat(step)
    }

    /**
     * Names the phase behind the current fraction so the bar is not just a
     * number: an empty manager reads as engine start-up, the bulk of the
     * range is texture upload, and the capped tail is the last hand-off
     * before combat, honestly labelled as still-in-progress.
     */
    private fun stageLabel(fraction: Float, done: Boolean): String = when {
        done -> "Entering the arena"
        fraction <= 0.001f -> "Initializing engine"
        fraction < 0.95f -> "Loading assets"
        else -> "Preparing arena"
    }

    /**
     * Fills the viewport with the backdrop, cropping the overhang instead of
     * stretching it: the arts are 2.22:1 and 1:2.22 while the viewport is
     * 16:9, so squashing would be obvious.
     */
    private fun drawBackdrop(batch: SpriteBatch, isPortrait: Boolean) {
        val texture = backdrop(isPortrait) ?: return

        val targetAspect = worldWidth / worldHeight
        val sourceAspect = texture.width.toFloat() / texture.height
        var regionWidth = texture.width
        var regionHeight = texture.height

        if (sourceAspect > targetAspect) {
            regionWidth = (texture.height * targetAspect).roundToInt()
        } else {
            regionHeight = (texture.width / targetAspect).roundToInt()
        }
        val regionX = (texture.width - regionWidth) / 2
        val regionY = (texture.height - regionHeight) / 2

        // This overload already maps the source rect top-down onto a bottom-up
        // world, so asking it to flip again is what turned the art upside down.
        batch.draw(
            texture,
            0f, 0f, worldWidth, worldHeight,
            regionX, regionY, regionWidth, regionHeight,
            false, false,
        )
    }

    private fun drawBar(batch: SpriteBatch, fraction: Float) {
        val barX = 150f
        val barWidth = worldWidth - barX * 2f
        val barY = worldHeight * 0.115f
        val barHeight = 30f

        batch.setColor(0.03f, 0.06f, 0.16f, 0.94f)
        batch.draw(primitives.pixel, barX - 5f, barY - 5f, barWidth + 10f, barHeight + 10f)
        batch.setColor(0.09f, 0.16f, 0.34f, 1f)
        batch.draw(primitives.pixel, barX, barY, barWidth, barHeight)

        val fillWidth = barWidth * fraction
        if (fillWidth > 1f) {
            batch.setColor(0.18f, 0.48f, 0.95f, 1f)
            batch.draw(primitives.pixel, barX, barY, fillWidth, barHeight)
            batch.setColor(1f, 0.85f, 0.55f, 0.85f)
            batch.draw(primitives.pixel, barX, barY + barHeight - 8f, fillWidth, 8f)
            // A bright cap on the leading edge keeps the eye on the growth.
            batch.setColor(1f, 1f, 1f, 0.75f)
            batch.draw(primitives.pixel, barX + fillWidth - 4f, barY, 4f, barHeight)
        }
        batch.color = Color.WHITE
    }

    private fun drawLabels(batch: SpriteBatch, fraction: Float, done: Boolean) {
        val barX = 150f
        val titleY = worldHeight * 0.115f + 106f
        val stageY = titleY - 46f

        font.color = Color.valueOf("FFD98CFF")
        font.draw(batch, if (done) "READY" else loadingDots(), barX, titleY)

        font.color = Color.valueOf("8FA3D0FF")
        font.draw(batch, stageLabel(fraction, done), barX, stageY)

        font.color = if (done) Color.valueOf("9BFFC8FF") else Color.valueOf("9FC4FFFF")
        font.draw(
            batch,
            "${(fraction * 100).toInt()}%",
            worldWidth - barX - 100f,
            titleY,
        )
        font.color = Color.WHITE
    }

    private fun backdrop(isPortrait: Boolean): Texture? = if (isPortrait) {
        portrait ?: load("ui/loading_portrait.jpg").also { portrait = it }
    } else {
        landscape ?: load("ui/loading_landscape.jpg").also { landscape = it }
    }

    /**
     * A missing or unreadable backdrop must never take the run down with it;
     * the bar alone still communicates progress.
     */
    private fun load(path: String): Texture? = runCatching {
        Texture(Gdx.files.internal(path)).apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }.getOrNull()

    /** Frees the backdrop as soon as the arena is up; it is never shown again. */
    fun releaseArt() {
        landscape?.dispose()
        landscape = null
        portrait?.dispose()
        portrait = null
    }

    override fun dispose() {
        releaseArt()
    }
}
