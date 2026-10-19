package pl.jojczak.penmouses.mousemode.mouse

import kotlin.math.exp
import kotlin.math.hypot

/**
 * Compresses only unusually large one-frame movements from Samsung Air Motion.
 * Unlike a hard clamp at 850px it is continuous: adjusting acceleration cannot
 * suddenly cause a jump in the output merely by crossing a hard threshold.
 * Ordinary sub-300px movement is completely unchanged.
 */
internal object MotionJumpLimiter {
    data class Result(val dx: Float, val dy: Float, val rawPixels: Float, val limitedPixels: Float)

    fun stabilize(dx: Float, dy: Float): Result {
        val distance = hypot(dx, dy)
        if (!distance.isFinite() || distance <= 0f) {
            return Result(0f, 0f, 0f, 0f)
        }
        if (distance <= LINEAR_RANGE_PX) return Result(dx, dy, distance, distance)
        val effective = LINEAR_RANGE_PX + SOFT_RANGE_PX *
            (1f - exp(-((distance - LINEAR_RANGE_PX) / DECAY_PX)))
        val factor = effective / distance
        return Result(dx * factor, dy * factor, distance, effective)
    }

    private const val LINEAR_RANGE_PX = 300f
    private const val SOFT_RANGE_PX = 120f
    private const val DECAY_PX = 280f
}
