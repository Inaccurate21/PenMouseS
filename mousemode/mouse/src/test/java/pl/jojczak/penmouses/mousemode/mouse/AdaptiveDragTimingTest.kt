package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.*
import org.junit.Test

class AdaptiveDragTimingTest {
    @Test fun respectsDisabledOrWarmupState() {
        val off = AdaptiveDragTiming(false)
        repeat(20) { off.observe(5, 30) }
        assertEquals(5L, off.choose(5))
        val on = AdaptiveDragTiming(true)
        repeat(4) { on.observe(5, 30) }
        assertEquals(5L, on.choose(5))
    }
    @Test fun backsOffOnlyWhenAndroidCallbacksAreConsistentlySlow() {
        val on = AdaptiveDragTiming(true)
        repeat(15) { on.observe(5, 30) }
        assertTrue(on.choose(5) in 17L..24L)
        repeat(30) { on.observe(5, 6) }
        assertEquals(5L, on.choose(5))
    }
    @Test fun leavesReleaseStrokeAlone() {
        val on = AdaptiveDragTiming(true)
        repeat(10) { on.observe(1, 60) }
        assertEquals(5L, on.choose(5))
        repeat(10) { on.observe(5, 5000) }
        assertEquals(5L, on.choose(5))
    }
}
