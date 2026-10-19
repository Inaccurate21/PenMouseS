package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTraceWindowTest {
    @Test fun keepsOnlyImmediatePrecedingFramesAndTrailingFrames() {
        val lines = mutableListOf<String>()
        val trace = MotionTraceWindow(beforeCount = 2, afterCount = 2, log = lines::add)
        trace.add("old", false)
        trace.add("pre1", false)
        trace.add("pre2", false)
        trace.add("burst", true)
        trace.add("post1", false)
        trace.add("post2", false)
        trace.add("quiet", false)
        assertEquals(
            listOf("seq=2 pre1", "seq=3 pre2", "seq=4 burst",
                "seq=5 post1", "seq=6 post2"),
            lines
        )
    }

    @Test fun overlappingBurstsNeverDuplicateFrames() {
        val lines = mutableListOf<String>()
        val trace = MotionTraceWindow(beforeCount = 3, afterCount = 2, log = lines::add)
        trace.add("a", false)
        trace.add("first", true)
        trace.add("middle", false)
        trace.add("second", true)
        trace.add("tail1", false)
        trace.add("tail2", false)
        assertEquals(
            listOf("seq=1 a", "seq=2 first", "seq=3 middle",
                "seq=4 second", "seq=5 tail1", "seq=6 tail2"),
            lines
        )
    }

    @Test fun resetStartsFreshTraceWithoutStaleHistory() {
        val lines = mutableListOf<String>()
        val trace = MotionTraceWindow(beforeCount = 2, afterCount = 1, log = lines::add)
        trace.add("previousSession", false)
        trace.reset()
        trace.add("newSession", true)
        assertEquals(listOf("seq=1 newSession"), lines)
    }
}
