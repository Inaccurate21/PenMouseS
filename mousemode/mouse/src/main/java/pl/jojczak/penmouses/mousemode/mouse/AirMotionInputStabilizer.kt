package pl.jojczak.penmouses.mousemode.mouse

import kotlin.math.hypot

/**
 * Conservative, zero-queued-distance conditioning of Samsung Air Motion deltas.
 *
 * - Fine movement is passed through unchanged.
 * - Larger consecutive samples are blended to reduce rapid speed fluctuations.
 * - An abrupt reversal follows the NEW direction immediately, but starts at
 *   reduced magnitude (does not continue in the old direction).
 * - Small / stopped input is NOT replaced by an old nonzero vector: no glide.
 * - This class does not schedule work, change button actions or inject gestures.
 */
internal class AirMotionInputStabilizer {
    data class Result(val dx: Float, val dy: Float, val mode: String)

    private var lastX: Float? = null
    private var lastY = 0f
    private var lastTimestampMs: Long? = null

    fun reset() {
        lastX = null
        lastY = 0f
        lastTimestampMs = null
    }

    fun filter(dx: Float, dy: Float, timestampMs: Long): Result {
        if (!dx.isFinite() || !dy.isFinite()) {
            reset()
            return Result(0f, 0f, "invalid")
        }

        val magnitude = hypot(dx, dy)
        if (magnitude < PRECISION_RANGE) {
            // In particular, zero input must never move the pointer.
            lastX = null
            lastY = 0f
            lastTimestampMs = timestampMs
            return Result(dx, dy, "precision")
        }

        val previousX = lastX
        val previousTime = lastTimestampMs
        val dt = if (previousTime == null) -1L else timestampMs - previousTime
        val result = if (previousX == null || dt <= 0L || dt > MAX_INTERVAL_MS) {
            Result(dx, dy, "fresh")
        } else {
            val previousMagnitude = hypot(previousX, lastY)
            if (magnitude < SMOOTHING_THRESHOLD || previousMagnitude < SMOOTHING_THRESHOLD) {
                Result(dx, dy, "precision")
            } else {
                val dot = dx * previousX + dy * lastY
                if (dot < -REVERSAL_COSINE * magnitude * previousMagnitude) {
                    // No mixing with prior direction. Avoid cursor running backwards
                    // after a deliberate direction change.
                    Result(dx * REVERSAL_FACTOR, dy * REVERSAL_FACTOR, "reversal")
                } else {
                    Result(
                        previousX * (1f - SMOOTHING_ALPHA) + dx * SMOOTHING_ALPHA,
                        lastY * (1f - SMOOTHING_ALPHA) + dy * SMOOTHING_ALPHA,
                        "smooth",
                    )
                }
            }
        }
        lastX = result.dx
        lastY = result.dy
        lastTimestampMs = timestampMs
        return result
    }

    private companion object {
        const val PRECISION_RANGE = 0.002f
        const val SMOOTHING_THRESHOLD = 0.025f
        const val MAX_INTERVAL_MS = 200L
        const val REVERSAL_COSINE = 0.25f
        const val REVERSAL_FACTOR = 0.45f
        const val SMOOTHING_ALPHA = 0.5f
    }
}
