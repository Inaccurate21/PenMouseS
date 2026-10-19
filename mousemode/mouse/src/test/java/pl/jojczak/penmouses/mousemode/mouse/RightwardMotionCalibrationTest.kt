package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.*
import org.junit.Test

class RightwardMotionCalibrationTest {
    @Test fun recognizesAllFourPossibleOrientations() {
        val samples = mapOf(
            0 to Pair(0.03f, 0f), 90 to Pair(0f, 0.03f),
            180 to Pair(-0.03f, 0f), 270 to Pair(0f, -0.03f)
        )
        samples.forEach { (expected, direction) ->
            val c = RightwardMotionCalibration()
            repeat(8) { c.add(direction.first, direction.second) }
            assertEquals(expected, c.inferRotation())
        }
    }

    @Test fun rejectsAmbiguousOrInsufficientMotion() {
        val c = RightwardMotionCalibration()
        repeat(10) { c.add(0.01f, 0.01f) }
        assertNull(c.inferRotation())
        val d = RightwardMotionCalibration()
        d.add(0.1f, 0f)
        assertNull(d.inferRotation())
    }

    @Test fun ignoresNonFiniteAndOutlierSamples() {
        val c = RightwardMotionCalibration()
        c.add(Float.NaN, 0f)
        c.add(500f, 0f)
        repeat(7) { c.add(-0.015f, 0f) }
        assertEquals(180, c.inferRotation())
    }
}
