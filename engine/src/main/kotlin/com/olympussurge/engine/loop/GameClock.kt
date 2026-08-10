package com.olympussurge.engine.loop

/**
 * Semi-fixed timestep driver.
 *
 * Simulation always advances in equal [step] slices so physics, cooldowns and
 * wave timers stay deterministic regardless of frame rate, while rendering can
 * interpolate between the last two states. Frames longer than [maxFrameTime]
 * are clamped instead of replayed, so a GC pause or an app switch cannot make
 * the world jump forward.
 */
class GameClock(
    val step: Float = 1f / 60f,
    private val maxFrameTime: Float = 0.25f,
    private val maxStepsPerFrame: Int = 5,
) {
    private var accumulator = 0f

    /** Fraction of a step already elapsed, for render interpolation. */
    var alpha = 0f
        private set

    var paused = false

    /** Steps consumed by the most recent [advance]; useful for diagnostics. */
    var stepsLastFrame = 0
        private set

    fun reset() {
        accumulator = 0f
        alpha = 0f
        stepsLastFrame = 0
    }

    /**
     * Feeds real elapsed time and runs [tick] for every whole simulation step.
     */
    inline fun advance(deltaTime: Float, tick: (Float) -> Unit) {
        if (paused) {
            onPausedFrame()
            return
        }
        var steps = beginFrame(deltaTime)
        while (steps > 0) {
            tick(step)
            steps--
        }
        endFrame()
    }

    @PublishedApi
    internal fun onPausedFrame() {
        alpha = 0f
        stepsLastFrame = 0
    }

    /** Accumulates [deltaTime] and returns how many steps should run now. */
    @PublishedApi
    internal fun beginFrame(deltaTime: Float): Int {
        accumulator += deltaTime.coerceIn(0f, maxFrameTime)
        var steps = 0
        while (accumulator >= step && steps < maxStepsPerFrame) {
            accumulator -= step
            steps++
        }
        // Drop the backlog we refused to simulate; replaying it later would
        // produce a visible speed-up right after a stall.
        if (accumulator >= step) accumulator = 0f
        stepsLastFrame = steps
        return steps
    }

    @PublishedApi
    internal fun endFrame() {
        alpha = accumulator / step
    }
}
