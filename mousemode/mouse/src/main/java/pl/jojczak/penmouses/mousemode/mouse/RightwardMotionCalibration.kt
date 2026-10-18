package pl.jojczak.penmouses.mousemode.mouse

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Two-second guided direction calibration: the user deliberately moves the S Pen right.
 * Returns only one of the four supported rotations, or null for ambiguous motion.
 * Raw Air Motion deltas use +X to the right and +Y upwards in the default mapping.
 */
internal class RightwardMotionCalibration {
    private var x = 0f
    private var y = 0f
    private var samples = 0

    fun add(deltaX: Float, deltaY: Float) {
        if (!deltaX.isFinite() || !deltaY.isFinite()) return
        val magnitude = hypot(deltaX, deltaY)
        if (magnitude < 0.001f || magnitude > 0.35f) return
        x += deltaX
        y += deltaY
        samples++
    }

    fun inferRotation(): Int? {
        if (samples < 5 || hypot(x, y) < 0.04f) return null
        // A diagonal or shaky trace is not sufficient to change cursor orientation.
        val primary = maxOf(abs(x), abs(y))
        val secondary = minOf(abs(x), abs(y))
        if (primary < secondary * 1.25f) return null
        return if (abs(x) >= abs(y)) {
            if (x >= 0f) 0 else 180
        } else {
            if (y >= 0f) 90 else 270
        }
    }

    fun summary(): String = "samples=$samples rawX=$x rawY=$y selected=${inferRotation()}"
}
