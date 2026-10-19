package pl.jojczak.penmouses.mousemode.mouse

import kotlin.math.hypot

/**
 * A rate-limited motion budget for Samsung Air Motion input.
 *
 * The existing per-sample limiter cannot prevent several ~400 px impulses
 * arriving 30-45 ms apart. This controller limits their *combined* distance,
 * not just each sample. Dropped distance is NEVER queued/replayed: cursor
 * motion stops immediately when the S Pen stops.
 *
 * Ordinary small movements are passed through when the budget is available.
 * It intentionally does not modify sensitivity, acceleration or drag timing.
 */
internal class MotionBurstGovernor(
    private val nowMs: () -> Long,
) {
    data class Result(
        val dx: Float,
        val dy: Float,
        val requestedPixels: Float,
        val acceptedPixels: Float,
        val budgetPixels: Float,
        val elapsedMs: Long,
    ) {
        val reducedPixels: Float get() = requestedPixels - acceptedPixels
    }

    private var previousMs: Long? = null
    private var budgetPixels: Float = BURST_CAPACITY_PX

    fun reset() {
        previousMs = null
        budgetPixels = BURST_CAPACITY_PX
    }

    fun filter(dx: Float, dy: Float): Result {
        val time = nowMs()
        val previous = previousMs
        val elapsed = if (previous == null || time < previous) IDLE_RESET_MS
            else (time - previous).coerceAtMost(IDLE_RESET_MS)
        budgetPixels = if (elapsed >= IDLE_RESET_MS) BURST_CAPACITY_PX
        else (budgetPixels + elapsed * MAX_SPEED_PX_PER_MS).coerceAtMost(BURST_CAPACITY_PX)
        previousMs = time

        val requested = hypot(dx, dy)
        if (!requested.isFinite() || requested <= 0f) {
            return Result(0f, 0f, 0f, 0f, budgetPixels, elapsed)
        }

        val accepted = requested.coerceAtMost(budgetPixels)
        budgetPixels -= accepted
        val scale = accepted / requested
        return Result(dx * scale, dy * scale, requested, accepted, budgetPixels, elapsed)
    }

    private companion object {
        // Up to ~1,700px/s sustained, with at most 160px immediate movement.
        // ~40ms spaced large samples can no longer accumulate into a teleport.
        const val MAX_SPEED_PX_PER_MS = 1.7f
        const val BURST_CAPACITY_PX = 160f
        const val IDLE_RESET_MS = 500L
    }
}
