package pl.jojczak.penmouses.mousemode.mouse

import kotlin.math.hypot

/**
 * Converts Samsung's normalized Air Motion deltas to screen pixels.
 * Low-speed movement remains precise; intentional quick sweeps gain extra travel.
 * Samsung SDK documents AirMotionEvent.timeStamp in nanoseconds.
 */
internal class AirMotionAcceleration {
    data class Pixels(val dx: Float, val dy: Float)

    private var previousTimestampNs: Long? = null

    fun reset() {
        previousTimestampNs = null
    }

    fun convert(
        deltaX: Float,
        deltaY: Float,
        timestampNs: Long,
        sensitivity: Float,
        acceleration: Float,
        rotationDegrees: Int = 0,
    ): Pixels {
        if (!deltaX.isFinite() || !deltaY.isFinite()) return Pixels(0f, 0f)

        val previous = previousTimestampNs
        // Ignore stale out-of-order BLE samples rather than replaying old motion.
        if (previous != null && timestampNs > 0L && timestampNs <= previous) return Pixels(0f, 0f)
        val elapsedNs = if (previous != null && timestampNs > previous) {
            (timestampNs - previous).coerceIn(MIN_INTERVAL_NS, MAX_INTERVAL_NS)
        } else {
            DEFAULT_INTERVAL_NS
        }
        previousTimestampNs = timestampNs.takeIf { it > 0L }

        // Per-second speed is independent of how many BLE samples the main looper coalesces.
        val speed = hypot(deltaX, deltaY) * NANOS_PER_SECOND / elapsedNs.toFloat()
        val proportion = ((speed - ACCEL_START) / (ACCEL_FULL - ACCEL_START)).coerceIn(0f, 1f)
        val extraBoost = acceleration.coerceIn(0f, 3f) * proportion * proportion
        val pixelsPerUnit = BASE_PIXELS_PER_UNIT * sensitivity.coerceIn(1f, 400f) * (1f + extraBoost)
        val x = deltaX * pixelsPerUnit
        val y = -deltaY * pixelsPerUnit
        return when (rotationDegrees) {
            90 -> Pixels(-y, x)
            180 -> Pixels(-x, -y)
            270 -> Pixels(y, -x)
            else -> Pixels(x, y)
        }
    }

    private companion object {
        const val BASE_PIXELS_PER_UNIT = 20f // Original PenMouse S conversion.
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val MIN_INTERVAL_NS = 8_000_000L
        const val DEFAULT_INTERVAL_NS = 16_000_000L
        const val MAX_INTERVAL_NS = 100_000_000L
        const val ACCEL_START = 0.25f
        const val ACCEL_FULL = 2.5f
    }
}
