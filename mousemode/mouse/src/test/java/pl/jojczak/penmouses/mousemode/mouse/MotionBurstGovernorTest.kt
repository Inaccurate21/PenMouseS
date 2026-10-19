package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class MotionBurstGovernorTest {
    private var now = 1_000L
    private fun subject() = MotionBurstGovernor { now }

    @Test fun ordinaryMotionKeepsExistingSensitivityAndAxes() {
        val limiter = subject()
        val first = limiter.filter(30f, -12f)
        assertEquals(30f, first.dx, 0.0001f)
        assertEquals(-12f, first.dy, 0.0001f)
        now += 120L
        val next = limiter.filter(-43f, 16f)
        assertEquals(-43f, next.dx, 0.0001f)
        assertEquals(16f, next.dy, 0.0001f)
    }

    @Test fun repeatedLargeFramesCannotTeleportScreenWithin130ms() {
        val limiter = subject()
        var traveled = 0f
        repeat(4) {
            traveled += limiter.filter(410f, 0f).acceptedPixels
            now += 42L
        }
        // Old limiter passed 4 x ~410px (1640px) in the same interval.
        assertTrue(traveled > 270f)
        assertTrue(traveled < 380f)
    }

    @Test fun neverReplaysDroppedDistanceAfterPenStops() {
        val limiter = subject()
        val first = limiter.filter(1000f, 0f)
        assertTrue(first.reducedPixels > 800f)
        now += 200L
        val idle = limiter.filter(0f, 0f)
        assertEquals(0f, idle.acceptedPixels, 0f)
        now += 100L
        val next = limiter.filter(30f, 0f)
        assertEquals(30f, next.dx, 0.0001f)
    }

    @Test fun restoredFullBudgetAfterIdleAndManualReset() {
        val limiter = subject()
        limiter.filter(2000f, 0f)
        now += 600L
        val afterIdle = limiter.filter(2000f, 0f)
        assertEquals(160f, afterIdle.acceptedPixels, 0.001f)
        limiter.reset()
        val afterReset = limiter.filter(120f, 0f)
        assertEquals(120f, afterReset.acceptedPixels, 0.001f)
    }

    @Test fun diagonalDirectionAndReversalArePreserved() {
        val limiter = subject()
        val first = limiter.filter(-300f, 400f)
        assertEquals(160f, hypot(first.dx, first.dy), 0.001f)
        assertTrue(first.dx < 0f && first.dy > 0f)
        now += 100L
        val reversed = limiter.filter(300f, -400f)
        assertTrue(reversed.dx > 0f && reversed.dy < 0f)
    }

    @Test fun invalidSampleAndClockAnomaliesDoNotProduceJump() {
        val limiter = subject()
        assertEquals(0f, limiter.filter(Float.NaN, 10f).acceptedPixels, 0f)
        now += 50L
        limiter.filter(500f, 0f)
        now -= 900L
        assertEquals(160f, limiter.filter(500f, 0f).acceptedPixels, 0f)
    }
}
