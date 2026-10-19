package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.*
import org.junit.Test

class AirMotionAccelerationTest {
    private val frameMs = 16L

    @Test fun noAccelerationPreservesOriginalSensitivityAndSign() {
        val engine = AirMotionAcceleration()
        val first = engine.convert(0.01f, 0.02f, frameMs, 100f, 0f)
        assertEquals(20f, first.dx, 0.001f)
        assertEquals(-40f, first.dy, 0.001f)
        val faster = engine.convert(0.1f, 0f, 2 * frameMs, 100f, 0f)
        assertEquals(200f, faster.dx, 0.001f)
    }

    @Test fun slowMovementRemainsPreciseAndFastMovementAccelerates() {
        val slow = AirMotionAcceleration().convert(0.002f, 0f, frameMs, 100f, 2f)
        val fast = AirMotionAcceleration().convert(0.06f, 0f, frameMs, 100f, 2f)
        assertEquals(4f, slow.dx, 0.001f)
        assertTrue(fast.dx > 1.35f * 0.06f * 100f * 20f)
        assertTrue(fast.dx < 2.5f * 0.06f * 100f * 20f)
    }

    @Test fun normalFastSamsungSampleDoesNotInstantlyDoubleItsMagnitude() {
        val engine = AirMotionAcceleration()
        engine.convert(0.04f, 0f, 1_000L, 41f, 1f)
        val fast = engine.convert(0.20f, 0f, 1_043L, 41f, 1f)
        val noBoost = 0.20f * 41f * 20f
        assertTrue(fast.dx >= noBoost)
        assertTrue(fast.dx < noBoost * 1.60f)
    }

    @Test fun increasingSensitivityIsLinearWithoutAcceleration() {
        val engine = AirMotionAcceleration()
        val before = engine.convert(0.015f, 0f, frameMs, 100f, 0f)
        engine.reset()
        val after = engine.convert(0.015f, 0f, frameMs, 400f, 0f)
        assertEquals(before.dx * 4, after.dx, 0.01f)
    }

    @Test fun realSamsungMillisecondTimeStampPreventsSaturation() {
        val engine = AirMotionAcceleration()
        engine.convert(0.01f, 0f, 3_088_296_000L, 67f, 2f)
        val second = engine.convert(0.005f, 0f, 3_088_296_100L, 67f, 2f)
        val base = 0.005f * 67f * 20f
        assertEquals(base, second.dx, 0.05f)
        assertTrue(second.dx < base * 1.2f)
    }

    @Test fun staleEventsResetVelocityAndDoNotSpike() {
        val engine = AirMotionAcceleration()
        engine.convert(0.2f, 0f, 100L, 80f, 2f)
        val after = engine.convert(0.01f, 0f, 800L, 80f, 2f)
        assertTrue(after.dx < 40f) // first sample after sleep cannot inherit saturated boost
    }

    @Test fun manualRotationMapsAxesPredictably() {
        val engine = AirMotionAcceleration()
        val base = engine.convert(0.01f, 0f, frameMs, 100f, 0f, 0)
        engine.reset()
        val turned = engine.convert(0.01f, 0f, frameMs, 100f, 0f, 90)
        assertEquals(0f, turned.dx, 0.001f)
        assertEquals(base.dx, turned.dy, 0.001f)
    }

    @Test fun discardsDuplicateAndOutOfOrderTimestamps() {
        val engine = AirMotionAcceleration()
        engine.convert(0.01f, 0f, 400L, 100f, 2f)
        assertEquals(0f, engine.convert(0.1f, 0f, 400L, 100f, 2f).dx, 0f)
        assertEquals(0f, engine.convert(0.1f, 0f, 399L, 100f, 2f).dx, 0f)
        assertTrue(engine.convert(0.01f, 0f, 420L, 100f, 2f).dx > 0f)
    }

    @Test fun invalidInputCannotPoisonState() {
        val engine = AirMotionAcceleration()
        assertEquals(0f, engine.convert(Float.NaN, 0f, frameMs, 100f, 2f).dx, 0f)
        val result = engine.convert(0.01f, 0f, frameMs, 100f, 0f)
        assertTrue(result.dx.isFinite())
        assertEquals(-result.dx, engine.convert(-0.01f, 0f, 2 * frameMs, 100f, 0f).dx, 0.001f)
    }
}
