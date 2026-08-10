package com.olympussurge.game.arena

/**
 * The playable disc of an Olympus arena.
 *
 * Combat happens on a flat circle in world units; the isometric look is applied
 * only at draw time. Keeping the play space circular means "am I still on the
 * arena" is a single distance check, and the edge reads clearly to the player.
 */
class Arena(
    val radiusX: Float,
    val radiusY: Float,
    val floorSprite: String,
    val backgroundSprite: String,
) {
    /** Clamps a position back onto the arena ellipse, in place. */
    fun clampToArena(position: FloatArray) {
        val nx = position[0] / radiusX
        val ny = position[1] / radiusY
        val distance = nx * nx + ny * ny
        if (distance <= 1f) return
        val scale = 1f / kotlin.math.sqrt(distance)
        position[0] *= scale
        position[1] *= scale
    }

    companion object {
        fun of(level: com.olympussurge.game.config.LevelDef): Arena = Arena(
            radiusX = level.radiusX,
            radiusY = level.radiusY,
            floorSprite = level.floorSprite,
            backgroundSprite = level.backgroundSprite,
        )
    }
}
