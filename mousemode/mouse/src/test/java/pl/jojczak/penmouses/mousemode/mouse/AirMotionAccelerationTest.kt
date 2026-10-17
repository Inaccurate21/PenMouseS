package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.*
import org.junit.Test

class AirMotionAccelerationTest {
    private val frameNs = 16_000_000L

    @Test fun noAccelerationMatchesOriginalSensitivityAtAnySpeed() {
        val engine = AirMotionAcceleration()
        val first = engine.convert(0.01f, 0.02f, frameNs, 100f, 0f)
        assertEquals(20f, first.dx, 0.001f)
        assertEquals(-40f, first.dy, 0.001f)
        val fast = engine.convert(0.1f, 0f, 2 * frameNs, 100f, 0f)
        assertEquals(200f, fast.dx, 0.001f)
    }

    @Test fun fastSweepsTravelFurtherThanSlowFineMotion() {
        val slow = AirMotionAcceleration().convert(0.002f, 0f, frameNs, 100f, 2f)
        val fast = AirMotionAcceleration().convert(0.06f, 0f, frameNs, 100f, 2f)
        assertEquals(4f, slow.dx, 0.001f)
        assertTrue(fast.dx > 2.5f * 0.06f * 100f * 20f)
    }

    @Test fun expandedSensitivityRangeSupportsFastScreenTraversal() {
        val engine = AirMotionAcceleration()
        val originalMax = engine.convert(0.015f, 0f, frameNs, 100f, 0f)
        engine.reset()
        val boosted = engine.convert(0.015f, 0f, frameNs, 400f, 0f)
        assertEquals(originalMax.dx * 4, boosted.dx, 0.01f)
    }

    @Test fun longerSampleIntervalsDoNotProduceSpuriousAcceleration() {
        val engine = AirMotionAcceleration()
        engine.convert(0.01f, 0f, frameNs, 100f, 2f)
        val slow = engine.convert(0.01f, 0f, frameNs + 100_000_000L, 100f, 2f)
        assertEquals(20f, slow.dx, 0.001f)
    }

    @Test fun badInputDoesNotPoisonStateOrProduceNaN() {
        val engine = AirMotionAcceleration()
        assertEquals(0f, engine.convert(Float.NaN, 0f, frameNs, 100f, 2f).dx, 0f)
        val result = engine.convert(0.01f, 0f, frameNs, 100f, 2f)
        assertTrue(result.dx.isFinite())
        assertEquals(-result.dx, engine.convert(-0.01f, 0f, 2 * frameNs, 100f, 2f).dx, 1f)
    }
}
