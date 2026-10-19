package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class MotionJumpLimiterTest {
    @Test fun smallMovementKeepsExactSensitivityAndDirection() {
        val m = MotionJumpLimiter.stabilize(120f, -55f)
        assertEquals(120f, m.dx, 0.0001f)
        assertEquals(-55f, m.dy, 0.0001f)
    }

    @Test fun extremeSensorSpikeCannotTeleportAcrossEntireScreen() {
        val m = MotionJumpLimiter.stabilize(690f, 2600f)
        assertTrue(m.rawPixels > 2600f)
        assertTrue(m.limitedPixels <= 420.01f)
        assertEquals(m.limitedPixels, hypot(m.dx, m.dy), 0.01f)
    }

    @Test fun accelerationChangesNeverCreateDiscontinuousJump() {
        val before = MotionJumpLimiter.stabilize(299.99f, 0f)
        val after = MotionJumpLimiter.stabilize(300.01f, 0f)
        assertTrue(after.dx > before.dx)
        assertTrue(after.dx - before.dx < 0.02f)
        var previous = 0f
        for (px in 0..3000 step 10) {
            val out = MotionJumpLimiter.stabilize(px.toFloat(), 0f).dx
            assertTrue(out + 0.001f >= previous)
            previous = out
        }
    }

    @Test fun keepsDirectionAndHandlesInvalidValues() {
        val out = MotionJumpLimiter.stabilize(-1000f, 0f)
        assertTrue(out.dx < 0f)
        assertEquals(0f, out.dy, 0f)
        assertEquals(0f, MotionJumpLimiter.stabilize(Float.NaN, 1f).limitedPixels, 0f)
    }
}
