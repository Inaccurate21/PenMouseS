package pl.jojczak.penmouses.mousemode.mouse

import java.util.PriorityQueue
import org.junit.Assert.*
import org.junit.Test
import pl.jojczak.penmouses.mousemode.mouse.RealtimeDragController.Point
import pl.jojczak.penmouses.mousemode.mouse.RealtimeDragController.Result
import pl.jojczak.penmouses.mousemode.mouse.RealtimeDragController.Segment

class RealtimeDragControllerTest {
    private class Clock {
        private data class Task(val due: Long, val order: Int, val run: () -> Unit, var cancelled: Boolean = false)
        private var now = 0L
        private var order = 0
        private val tasks = PriorityQueue<Task>(compareBy<Task> { it.due }.thenBy { it.order })
        fun schedule(delay: Long, action: () -> Unit): () -> Unit {
            val task = Task(now + delay, order++, action)
            tasks.add(task)
            return { task.cancelled = true }
        }
        fun timeMs() = now
        fun advance(ms: Long) {
            val until = now + ms
            while (tasks.isNotEmpty() && tasks.peek().due <= until) {
                val task = tasks.remove()
                now = task.due
                if (!task.cancelled) task.run()
            }
            now = until
        }
    }

    private class Backend : RealtimeDragController.Backend {
        val segments = mutableListOf<Segment>()
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        var accept = true
        var throwOnDispatch = false
        var emergencyReleases = 0
        var resets = 0
        override fun dispatch(segment: Segment, completed: (Boolean) -> Unit): Boolean {
            if (throwOnDispatch) throw IllegalStateException("backend unavailable")
            segments.add(segment)
            callbacks.add(completed)
            return accept
        }
        fun complete(index: Int = callbacks.lastIndex, success: Boolean = true) = callbacks[index](success)
        override fun releaseBestEffort() { emergencyReleases++ }
        override fun reset() { resets++ }
    }

    private class Fixture(duration: Long = 16L, graceMs: Long = 0L) {
        val clock = Clock()
        val backend = Backend()
        val results = mutableListOf<Result>()
        val graceEvents = mutableListOf<String>()
        val brakeEvents = mutableListOf<String>()
        val controller = RealtimeDragController(
            backend, clock::schedule, duration, results::add,
            releaseGraceMs = graceMs, clockMs = clock::timeMs,
            onReleaseGrace = graceEvents::add,
            onReleaseBrake = brakeEvents::add
        )
    }

    @Test fun textSelectionStaysDownForLongPressAndReleasesWithoutFlingBrake() {
        val clock = Clock()
        val backend = Backend()
        val results = mutableListOf<Result>()
        val controller = RealtimeDragController(
            backend, clock::schedule, 32L, results::add,
            initialDownDurationMs = 620L, flingBrakeEnabled = false,
            clockMs = clock::timeMs,
        )
        controller.press(Point(100f, 300f))
        assertEquals(620L, backend.segments[0].durationMs)
        assertTrue(backend.segments[0].willContinue)
        controller.move(Point(140f, 300f))
        backend.complete(0)
        backend.complete(1)
        controller.move(Point(600f, 305f))
        backend.complete(2)
        controller.release()
        val up = backend.segments[3]
        assertFalse(up.willContinue)
        assertEquals(1L, up.durationMs)
        backend.complete(3)
        assertEquals(listOf(Result.RELEASED), results)
    }

    @Test fun pressDispatchesDownImmediatelyAndStationaryHoldDoesNotPoll() {
        val f = Fixture()
        assertTrue(f.controller.press(Point(10f, 20f)))
        assertEquals(1, f.backend.segments.size)
        assertFalse(f.backend.segments[0].continuing)
        assertTrue(f.backend.segments[0].willContinue)
        f.backend.complete()
        f.clock.advance(2_000L)
        assertTrue(f.controller.isActive)
        assertEquals(1, f.backend.segments.size)
        assertTrue(f.results.isEmpty())
    }

    @Test fun releaseBeforeDownCompletesIsSerializedAndLiftsPointer() {
        val f = Fixture()
        f.controller.press(Point(10f, 20f))
        f.controller.release()
        assertEquals(1, f.backend.segments.size)
        f.backend.complete(0)
        assertEquals(2, f.backend.segments.size)
        assertTrue(f.backend.segments[1].continuing)
        assertFalse(f.backend.segments[1].willContinue)
        assertEquals(f.backend.segments[0].to, f.backend.segments[1].from)
        f.backend.complete(1)
        assertFalse(f.controller.isActive)
        assertEquals(listOf(Result.RELEASED), f.results)
    }

    @Test fun motionUsesLatestSampleWithoutBuildingQueue() {
        val f = Fixture()
        f.controller.press(Point(10f, 20f))
        repeat(1000) { f.controller.move(Point(it.toFloat(), 30f)) }
        assertEquals(1, f.backend.segments.size)
        f.backend.complete(0)
        assertEquals(Point(999f, 30f), f.backend.segments[1].to)
        assertEquals(16L, f.backend.segments[1].durationMs)
        f.controller.move(Point(1000f, 40f))
        f.controller.move(Point(1001f, 50f))
        assertEquals(2, f.backend.segments.size)
        f.backend.complete(1)
        assertEquals(Point(1001f, 50f), f.backend.segments[2].to)
    }

    @Test fun releaseFreezesTargetAndCannotBeTurnedIntoFurtherDrag() {
        val f = Fixture()
        f.controller.press(Point(10f, 20f))
        f.backend.complete()
        f.controller.move(Point(30f, 40f))
        f.controller.move(Point(50f, 60f))
        f.controller.release()
        f.controller.move(Point(900f, 900f))
        f.backend.complete(1)
        assertEquals(Point(50f, 60f), f.backend.segments[2].to)
        assertFalse(f.backend.segments[2].willContinue)
        f.backend.complete(2)
        assertEquals(listOf(Result.RELEASED), f.results)
    }

    @Test fun duplicatePressDoesNotCreateSecondPointer() {
        val f = Fixture()
        f.controller.press(Point(0f, 0f))
        assertFalse(f.controller.press(Point(1f, 1f)))
        assertEquals(1, f.backend.segments.size)
    }

    @Test fun cancellationEndsGestureWithoutAutomaticRestart() {
        val f = Fixture()
        f.controller.press(Point(0f, 0f))
        f.backend.complete(success = false)
        f.controller.move(Point(50f, 50f))
        f.controller.release()
        assertEquals(1, f.backend.segments.size)
        assertFalse(f.controller.isActive)
        assertEquals(listOf(Result.CANCELLED), f.results)
    }

    @Test fun missingCallbackTriggersWatchdogAndIgnoresLateCompletion() {
        val f = Fixture()
        f.controller.press(Point(0f, 0f))
        f.clock.advance(1_001L)
        assertEquals(1, f.backend.emergencyReleases)
        assertEquals(listOf(Result.CALLBACK_TIMEOUT), f.results)
        f.backend.complete(0)
        assertEquals(1, f.backend.segments.size)
        assertFalse(f.controller.isActive)
    }

    @Test fun rejectedDispatchEndsSafely() {
        val f = Fixture()
        f.backend.accept = false
        assertFalse(f.controller.press(Point(0f, 0f)))
        assertEquals(listOf(Result.REJECTED), f.results)
        assertEquals(1, f.backend.emergencyReleases)
    }

    @Test fun throwingBackendIsHandledLikeRejection() {
        val f = Fixture()
        f.backend.throwOnDispatch = true
        assertFalse(f.controller.press(Point(0f, 0f)))
        assertEquals(listOf(Result.REJECTED), f.results)
    }

    @Test fun closeWaitsForInFlightThenReleasesAndRefusesNewPress() {
        val f = Fixture()
        f.controller.press(Point(0f, 0f))
        f.controller.close()
        assertFalse(f.controller.press(Point(1f, 1f)))
        f.backend.complete(0)
        assertFalse(f.backend.segments[1].willContinue)
        f.backend.complete(1)
        assertEquals(listOf(Result.CLOSED), f.results)
        assertFalse(f.controller.press(Point(1f, 1f)))
    }

    @Test fun safetyLimitReleasesStationaryHoldAfterThirtySeconds() {
        val f = Fixture()
        f.controller.press(Point(10f, 20f))
        f.backend.complete(0)
        f.clock.advance(29_999L)
        assertEquals(1, f.backend.segments.size)
        f.clock.advance(1L)
        assertFalse(f.backend.segments[1].willContinue)
        f.backend.complete(1)
        assertEquals(listOf(Result.HOLD_LIMIT), f.results)
    }

    @Test fun oldCallbacksCannotAffectNewGesture() {
        val f = Fixture()
        f.controller.press(Point(0f, 0f))
        f.backend.complete(0, false)
        f.controller.press(Point(1f, 1f))
        f.backend.complete(0)
        assertTrue(f.controller.isActive)
        assertEquals(2, f.backend.segments.size)
    }

    @Test fun sequentialClicksStartFreshStrokes() {
        val f = Fixture()
        repeat(2) {
            f.controller.press(Point(10f, 20f))
            f.backend.complete()
            f.controller.release()
            f.backend.complete()
        }
        assertEquals(4, f.backend.segments.size)
        assertFalse(f.backend.segments[2].continuing)
        assertEquals(listOf(Result.RELEASED, Result.RELEASED), f.results)
        f.clock.advance(40_000L)
        assertEquals(4, f.backend.segments.size)
    }


    @Test fun optInLateSampleCapturedBeforeGestureEnds() {
        val f = Fixture(graceMs = 140L)
        val p = f.controller
        assertTrue(p.press(Point(300f, 400f)))
        f.backend.complete(0)
        f.clock.advance(170L)
        p.release()
        assertTrue(p.awaitingLateMotion)
        assertEquals(1, f.backend.segments.size)
        f.clock.advance(45L)
        p.move(Point(480f, 400f))
        assertFalse(p.awaitingLateMotion)
        assertEquals(2, f.backend.segments.size)
        assertEquals(Point(480f, 400f), f.backend.segments[1].to)
        assertFalse(f.backend.segments[1].willContinue)
        assertTrue(f.backend.segments[1].durationMs >= 12L)
        f.backend.complete(1)
        assertEquals(listOf(Result.RELEASED), f.results)
        assertTrue(f.graceEvents.any { it.contains("sample-captured") })
        p.move(Point(700f, 400f))
        assertEquals(2, f.backend.segments.size)
    }

    @Test fun optInTimeoutReleasesWithoutLateSensorSample() {
        val f = Fixture(graceMs = 140L)
        f.controller.press(Point(300f, 400f))
        f.backend.complete()
        f.clock.advance(165L)
        f.controller.release()
        assertTrue(f.controller.awaitingLateMotion)
        f.clock.advance(139L)
        assertEquals(1, f.backend.segments.size)
        f.clock.advance(1L)
        assertFalse(f.controller.awaitingLateMotion)
        assertEquals(2, f.backend.segments.size)
        assertFalse(f.backend.segments[1].willContinue)
        f.backend.complete()
        assertEquals(listOf(Result.RELEASED), f.results)
        assertTrue(f.graceEvents.any { it == "timeout" })
    }

    @Test fun optInShortTapAndLongHoldDoNotWait() {
        val f = Fixture(graceMs = 140L)
        f.controller.press(Point(100f, 100f))
        f.backend.complete()
        f.clock.advance(70L)
        f.controller.release()
        assertFalse(f.controller.awaitingLateMotion)
        f.backend.complete()
        f.controller.press(Point(100f, 100f))
        f.backend.complete()
        f.clock.advance(600L)
        f.controller.release()
        assertFalse(f.controller.awaitingLateMotion)
        f.backend.complete()
        assertEquals(listOf(Result.RELEASED, Result.RELEASED), f.results)
    }

    @Test fun closingWhileWaitingForLateMotionLiftsPointerImmediately() {
        val f = Fixture(graceMs = 140L)
        f.controller.press(Point(100f, 100f))
        f.backend.complete()
        f.clock.advance(170L)
        f.controller.release()
        assertTrue(f.controller.awaitingLateMotion)
        f.controller.close()
        assertFalse(f.controller.awaitingLateMotion)
        assertEquals(2, f.backend.segments.size)
        f.backend.complete(1)
        assertFalse(f.controller.isActive)
        f.clock.advance(400L)
        assertEquals(2, f.backend.segments.size)
    }


    @Test fun horizontalGallerySwipeEndsInStationaryReleaseAndSkipsLateSensorSample() {
        val f = Fixture(duration = 32L, graceMs = 140L)
        val p = f.controller
        p.press(Point(1200f, 800f))
        f.backend.complete(0)
        f.clock.advance(220L)
        p.move(Point(850f, 820f))
        f.backend.complete(1)
        p.move(Point(600f, 830f))
        p.release()
        assertFalse(p.awaitingLateMotion)
        assertEquals(Point(850f, 820f), f.backend.segments[2].from)
        assertEquals(Point(600f, 830f), f.backend.segments[2].to)
        assertTrue(f.backend.segments[2].willContinue)
        f.backend.complete(2)
        val tail = f.backend.segments[3]
        assertTrue(tail.continuing)
        assertFalse(tail.willContinue)
        assertEquals(tail.from, tail.to)
        assertEquals(96L, tail.durationMs)
        p.move(Point(300f, 900f))
        assertEquals(4, f.backend.segments.size)
        f.backend.complete(3)
        assertEquals(listOf(Result.RELEASED), f.results)
        assertTrue(f.brakeEvents.any { it.contains("stationaryReleaseMs=96") })
        assertTrue(f.graceEvents.isEmpty())
    }

    @Test fun galleryBrakeWorksWhenLastMovementWasAlreadyDispatched() {
        val f = Fixture()
        val p = f.controller
        p.press(Point(600f, 300f))
        f.backend.complete(0)
        p.move(Point(280f, 295f))
        f.backend.complete(1)
        p.release()
        val tail = f.backend.segments[2]
        assertEquals(tail.from, tail.to)
        assertFalse(tail.willContinue)
        assertEquals(96L, tail.durationMs)
        f.backend.complete(2)
        assertEquals(listOf(Result.RELEASED), f.results)
    }

    @Test fun shortClickAndVerticalDragKeepOriginalReleaseTiming() {
        val f = Fixture(graceMs = 140L)
        val p = f.controller
        p.press(Point(500f, 500f))
        f.backend.complete(0)
        p.move(Point(502f, 504f))
        f.backend.complete(1)
        p.release()
        assertFalse(p.awaitingLateMotion)
        assertEquals(1L, f.backend.segments[2].durationMs)
        f.backend.complete(2)

        p.press(Point(500f, 500f))
        f.backend.complete(3)
        f.clock.advance(160L)
        p.move(Point(510f, 100f))
        f.backend.complete(4)
        p.release()
        assertTrue(p.awaitingLateMotion)
        f.clock.advance(140L)
        assertEquals(1L, f.backend.segments[5].durationMs)
        f.backend.complete(5)
        assertTrue(f.brakeEvents.isEmpty())
    }

    @Test fun interruptedHorizontalDragNeverInjectsAnotherTap() {
        val f = Fixture()
        val p = f.controller
        p.press(Point(1000f, 500f))
        f.backend.complete(0)
        p.move(Point(600f, 500f))
        f.backend.complete(1)
        p.close()
        val tail = f.backend.segments[2]
        assertFalse(tail.willContinue)
        assertEquals(1L, tail.durationMs)
        f.backend.complete(2)
        assertEquals(listOf(Result.CLOSED), f.results)
    }

    @Test fun durationIsClampedAndNonFiniteMotionIgnored() {
        for ((input, expected) in listOf(0L to 1L, 100L to 32L)) {
            val f = Fixture(input)
            assertFalse(f.controller.press(Point(Float.NaN, 0f)))
            f.controller.press(Point(1f, 1f))
            f.controller.move(Point(Float.POSITIVE_INFINITY, 5f))
            f.controller.move(Point(2f, 2f))
            f.backend.complete(0)
            assertEquals(expected, f.backend.segments[1].durationMs)
            assertEquals(Point(2f, 2f), f.backend.segments[1].to)
        }
    }
}
