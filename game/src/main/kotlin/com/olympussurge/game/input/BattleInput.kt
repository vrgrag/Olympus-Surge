package com.olympussurge.game.input

import com.badlogic.gdx.InputProcessor
import com.olympussurge.engine.input.VirtualJoystick

/**
 * Single entry point for touches during a run.
 *
 * Routing lives here rather than in an InputMultiplexer because the rules are
 * modal: while an overlay is up the joystick must not receive anything, and a
 * tap on the pause button must never also start a move.
 */
class BattleInput(
    val joystick: VirtualJoystick,
    private val toWorld: (screenX: Int, screenY: Int, out: FloatArray) -> Unit,
) : InputProcessor {

    /** Set by the scene each frame; decides which surface owns touches. */
    var overlayVisible = false

    var onPauseTapped: () -> Unit = {}
    var onOverlayTapped: (worldX: Float, worldY: Float) -> Unit = { _, _ -> }
    var isPauseTarget: (worldX: Float, worldY: Float) -> Boolean = { _, _ -> false }

    private val point = FloatArray(2)

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        toWorld(screenX, screenY, point)

        if (overlayVisible) {
            onOverlayTapped(point[0], point[1])
            return true
        }
        if (isPauseTarget(point[0], point[1])) {
            onPauseTapped()
            return true
        }
        return joystick.touchDown(screenX, screenY, pointer, button)
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean =
        !overlayVisible && joystick.touchDragged(screenX, screenY, pointer)

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        joystick.touchUp(screenX, screenY, pointer, button)

    override fun touchCancelled(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int,
    ): Boolean {
        joystick.release()
        return true
    }

    override fun keyDown(keycode: Int): Boolean = false
    override fun keyUp(keycode: Int): Boolean = false
    override fun keyTyped(character: Char): Boolean = false
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false
    override fun scrolled(amountX: Float, amountY: Float): Boolean = false
}
