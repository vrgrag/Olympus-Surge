package com.olympussurge.engine.input

import com.badlogic.gdx.InputAdapter
import kotlin.math.sqrt

/**
 * Floating one-finger joystick.
 *
 * The stick appears wherever the player first touches instead of at a fixed
 * spot, so the thumb never has to hunt for it and the hero starts moving on the
 * very first frame of contact. Coordinates are supplied in world space by the
 * caller, which keeps this class independent of the viewport.
 */
class VirtualJoystick(
    private val maxRadius: Float = 130f,
    private val deadZone: Float = 0.12f,
) : InputAdapter() {

    var active = false
        private set
    var originX = 0f
        private set
    var originY = 0f
        private set
    var knobX = 0f
        private set
    var knobY = 0f
        private set

    /** Normalized direction, zero when inside the dead zone. */
    var dirX = 0f
        private set
    var dirY = 0f
        private set

    private var pointerId = -1

    /** Screen-to-world conversion supplied by the scene. */
    var unproject: ((screenX: Int, screenY: Int, out: FloatArray) -> Unit)? = null

    private val scratch = FloatArray(2)

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (active) return false
        val convert = unproject ?: return false
        convert(screenX, screenY, scratch)

        pointerId = pointer
        active = true
        originX = scratch[0]
        originY = scratch[1]
        knobX = originX
        knobY = originY
        dirX = 0f
        dirY = 0f
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!active || pointer != pointerId) return false
        val convert = unproject ?: return false
        convert(screenX, screenY, scratch)

        var dx = scratch[0] - originX
        var dy = scratch[1] - originY
        val distance = sqrt(dx * dx + dy * dy)

        if (distance > maxRadius) {
            val scale = maxRadius / distance
            dx *= scale
            dy *= scale
        }
        knobX = originX + dx
        knobY = originY + dy

        val strength = (distance / maxRadius).coerceAtMost(1f)
        if (strength < deadZone || distance < 0.001f) {
            dirX = 0f
            dirY = 0f
        } else {
            dirX = dx / distance.coerceAtLeast(0.001f)
            dirY = dy / distance.coerceAtLeast(0.001f)
        }
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer != pointerId) return false
        release()
        return true
    }

    fun release() {
        active = false
        pointerId = -1
        dirX = 0f
        dirY = 0f
    }
}
