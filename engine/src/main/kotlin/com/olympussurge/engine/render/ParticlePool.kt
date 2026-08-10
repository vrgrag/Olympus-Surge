package com.olympussurge.engine.render

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fixed-capacity particle pool for combat feedback.
 *
 * Particles are stored in parallel arrays and reused in place, so a wave of
 * explosions costs no allocation and no garbage collection pause. When the
 * pool is full, new requests are dropped rather than growing it: a frame that
 * is already saturated with sparks will not miss a few more.
 */
class ParticlePool(private val capacity: Int) {

    val active = BooleanArray(capacity)
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val size = FloatArray(capacity)
    val life = FloatArray(capacity)
    val maxLife = FloatArray(capacity)
    val red = FloatArray(capacity)
    val green = FloatArray(capacity)
    val blue = FloatArray(capacity)
    /** Extra alpha multiplier so different bursts can differ in intensity. */
    val alpha = FloatArray(capacity)

    private val velocityX = FloatArray(capacity)
    private val velocityY = FloatArray(capacity)
    private val drag = FloatArray(capacity)
    private val gravity = FloatArray(capacity)

    private var cursor = 0

    /** Emits [count] particles in a cone or a full ring around a point. */
    fun burst(
        originX: Float,
        originY: Float,
        count: Int,
        speed: Float,
        spread: Float,
        direction: Float,
        life: Float,
        size: Float,
        r: Float,
        g: Float,
        b: Float,
        alpha: Float = 1f,
        gravity: Float = 0f,
        drag: Float = 2.4f,
        random: Random = Random.Default,
    ) {
        repeat(count) {
            val index = claim() ?: return
            val angle = direction + (random.nextFloat() - 0.5f) * spread
            val magnitude = speed * (0.55f + random.nextFloat() * 0.65f)

            this.active[index] = true
            this.x[index] = originX
            this.y[index] = originY
            this.velocityX[index] = cos(angle) * magnitude
            this.velocityY[index] = sin(angle) * magnitude
            this.life[index] = life * (0.7f + random.nextFloat() * 0.6f)
            this.maxLife[index] = this.life[index]
            this.size[index] = size * (0.7f + random.nextFloat() * 0.7f)
            this.red[index] = r
            this.green[index] = g
            this.blue[index] = b
            this.alpha[index] = alpha
            this.gravity[index] = gravity
            this.drag[index] = drag
        }
    }

    fun update(step: Float) {
        for (i in 0 until capacity) {
            if (!active[i]) continue

            life[i] -= step
            if (life[i] <= 0f) {
                active[i] = false
                continue
            }

            velocityY[i] -= gravity[i] * step
            val damping = 1f - (drag[i] * step).coerceIn(0f, 0.95f)
            velocityX[i] *= damping
            velocityY[i] *= damping

            x[i] += velocityX[i] * step
            y[i] += velocityY[i] * step
        }
    }

    /** Remaining life as 0..1, used for fade and shrink. */
    fun fraction(index: Int): Float =
        if (maxLife[index] <= 0f) 0f else life[index] / maxLife[index]

    fun clear() {
        active.fill(false)
    }

    /**
     * Round-robin allocation: scanning from the last hand-out spreads reuse
     * evenly instead of always recycling the same low indices.
     */
    private fun claim(): Int? {
        for (offset in 0 until capacity) {
            val index = (cursor + offset) % capacity
            if (!active[index]) {
                cursor = (index + 1) % capacity
                return index
            }
        }
        return null
    }
}
