package pl.jojczak.penmouses.mousemode.mouse

import kotlin.math.hypot

/**
 * Converts Samsung Air Motion deltas to pixels. On the tested S24 Ultra,
 * event.timeStamp increments in MILLISECONDS (confirmed against wall-clock deltas).
 * The previous implementation wrongly treated it as nanoseconds, saturating acceleration.
 */
internal class AirMotionAcceleration {
    data class Pixels(val dx: Float, val dy: Float)

    private var previousTimestampMs: Long? = null
    private var filteredSpeed = 0f

    fun reset() {
        previousTimestampMs = null
        filteredSpeed = 0f
    }

    fun convert(
        deltaX: Float,
        deltaY: Float,
        timestampMs: Long,
        sensitivity: Float,
        acceleration: Float,
        rotationDegrees: Int = 0,
    ): Pixels {
        if (!deltaX.isFinite() || !deltaY.isFinite()) return Pixels(0f, 0f)

        val previous = previousTimestampMs
        if (timestampMs > 0L && previous != null && timestampMs <= previous) {
            return Pixels(0f, 0f) // Out-of-order or duplicated BLE event.
        }
        val elapsedMs = if (timestampMs > 0L && previous != null) {
            timestampMs - previous
        } else {
            DEFAULT_INTERVAL_MS
        }
        if (timestampMs > 0L) previousTimestampMs = timestampMs

        // Ignore speed estimation from a stale connection (e.g. S Pen sleep/wake).
        if (elapsedMs > STALE_INTERVAL_MS) filteredSpeed = 0f
        val interval = elapsedMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS).toFloat()
        val instantSpeed = hypot(deltaX, deltaY) * 1000f / interval
        // Low-pass velocity, not the coordinates: no artificial pointer lag.
        filteredSpeed = if (filteredSpeed == 0f || elapsedMs > STALE_INTERVAL_MS) {
            instantSpeed
        } else {
            SPEED_EMA * instantSpeed + (1f - SPEED_EMA) * filteredSpeed
        }
        val normalized = ((filteredSpeed - ACCEL_START) / (ACCEL_FULL - ACCEL_START)).coerceIn(0f, 1f)
        val boost = 1f + acceleration.coerceIn(0f, 3f) * normalized * normalized
        val scalar = BASE_PIXELS_PER_UNIT * sensitivity.coerceIn(1f, 400f) * boost

        val x = deltaX * scalar
        val y = -deltaY * scalar
        return when (rotationDegrees) {
            90 -> Pixels(-y, x)
            180 -> Pixels(-x, -y)
            270 -> Pixels(y, -x)
            else -> Pixels(x, y)
        }
    }

    private companion object {
        const val BASE_PIXELS_PER_UNIT = 20f
        const val MIN_INTERVAL_MS = 4L
        const val DEFAULT_INTERVAL_MS = 16L
        const val MAX_INTERVAL_MS = 120L
        const val STALE_INTERVAL_MS = 500L
        const val ACCEL_START = 0.25f
        const val ACCEL_FULL = 3.0f
        const val SPEED_EMA = 0.55f
    }
}
