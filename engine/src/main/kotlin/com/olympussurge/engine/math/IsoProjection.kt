package com.olympussurge.engine.math

import kotlin.math.sqrt

/**
 * Converts between the flat arena plane the simulation runs on and the tilted
 * view the player sees.
 *
 * Gameplay reasons about a plain 2D circle: distances, radii and collisions are
 * all euclidean. Rendering squashes that plane vertically and lifts sprites off
 * the floor, which reads as a camera looking down at roughly 45 degrees while
 * keeping the combat math trivial.
 */
object IsoProjection {

    /** Vertical squash of the ground plane. */
    const val TILT = 0.58f

    fun screenX(worldX: Float): Float = worldX

    fun screenY(worldY: Float): Float = worldY * TILT

    /** Depth key for painter's-algorithm sorting: further back draws first. */
    fun depth(worldY: Float): Float = worldY

    fun worldY(screenY: Float): Float = screenY / TILT

    /**
     * Scales a movement vector so travelling "up" the screen does not feel
     * faster than travelling sideways despite the squashed projection.
     */
    fun normalizeInput(dx: Float, dy: Float, out: FloatArray) {
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0.0001f) {
            out[0] = 0f
            out[1] = 0f
            return
        }
        out[0] = dx / length
        out[1] = dy / length
    }
}
