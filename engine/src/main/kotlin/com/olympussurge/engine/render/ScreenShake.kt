package com.olympussurge.engine.render

import kotlin.math.sin
import kotlin.random.Random

/**
 * Decaying camera shake driven by trauma rather than by a fixed animation.
 *
 * Events add trauma; the offset scales with its square so small hits barely
 * register while a boss slam is unmistakable. Trauma always bleeds off at the
 * same rate, so overlapping impacts blend instead of stacking into a mess.
 */
class ScreenShake(
    private val maxOffset: Float = 34f,
    private val frequency: Float = 22f,
    private val decayPerSecond: Float = 1.6f,
) {
    private var trauma = 0f
    private var time = 0f
    private val seedX = Random.nextFloat() * 100f
    private val seedY = Random.nextFloat() * 100f

    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    fun add(amount: Float) {
        trauma = (trauma + amount).coerceAtMost(1f)
    }

    fun update(step: Float) {
        if (trauma <= 0f) {
            offsetX = 0f
            offsetY = 0f
            return
        }

        time += step
        trauma = (trauma - decayPerSecond * step).coerceAtLeast(0f)

        val magnitude = trauma * trauma * maxOffset
        offsetX = magnitude * sin((time + seedX) * frequency)
        offsetY = magnitude * sin((time + seedY) * frequency * 1.31f)
    }

    fun reset() {
        trauma = 0f
        offsetX = 0f
        offsetY = 0f
    }
}
