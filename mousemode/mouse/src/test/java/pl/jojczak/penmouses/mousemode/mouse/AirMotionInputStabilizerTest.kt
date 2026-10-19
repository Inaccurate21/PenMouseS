package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirMotionInputStabilizerTest {
    @Test fun microMovementsAreUnchanged() {
        val f = AirMotionInputStabilizer()
        assertEquals(0.001f, f.filter(0.001f, 0f, 1_000L).dx, 0.000001f)
        assertEquals(-0.008f, f.filter(-0.008f, 0f, 1_043L).dx, 0.000001f)
    }

    @Test fun abruptRiseAndSustainedMotionAreSmoothedButNotFrozen() {
        val f = AirMotionInputStabilizer()
        f.filter(0.04f, 0f, 1_000L)
        val rise = f.filter(0.20f, 0f, 1_043L)
        assertEquals(0.12f, rise.dx, 0.00001f)
        val continued = f.filter(0.20f, 0f, 1_086L)
        assertTrue(continued.dx > rise.dx)
        assertTrue(continued.dx < 0.20f)
    }

    @Test fun reversalImmediatelyFollowsCorrectDirection() {
        val f = AirMotionInputStabilizer()
        f.filter(0.15f, 0.03f, 1_000L)
        val reversed = f.filter(-0.18f, -0.04f, 1_043L)
        assertTrue(reversed.dx < 0f && reversed.dy < 0f)
        assertEquals(-0.081f, reversed.dx, 0.00001f)
    }

    @Test fun zeroInputNeverReplaysOldMovement() {
        val f = AirMotionInputStabilizer()
        f.filter(0.22f, 0f, 1_000L)
        val zero = f.filter(0f, 0f, 1_043L)
        assertEquals(0f, zero.dx, 0f)
        assertEquals(0f, zero.dy, 0f)
        assertEquals(0.01f, f.filter(0.01f, 0f, 1_086L).dx, 0f)
    }

    @Test fun gapOrResetDoesNotInheritOldDirection() {
        val f = AirMotionInputStabilizer()
        f.filter(0.20f, 0f, 1_000L)
        assertEquals(-0.20f, f.filter(-0.20f, 0f, 1_301L).dx, 0f)
        f.reset()
        assertEquals(0.2f, f.filter(0.2f, 0f, 1_340L).dx, 0f)
    }

    @Test fun invalidInputCannotCorruptFollowingMovements() {
        val f = AirMotionInputStabilizer()
        f.filter(0.20f, 0f, 1_000L)
        assertEquals(0f, f.filter(Float.NaN, 0f, 1_043L).dx, 0f)
        assertEquals(-0.20f, f.filter(-0.20f, 0f, 1_086L).dx, 0f)
    }
}
