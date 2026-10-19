package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.*
import org.junit.Test

class SPenButtonShortcutRouterTest {
    private data class Task(val millis: Long, val call: () -> Unit, var cancelled: Boolean = false)
    private val tasks = mutableListOf<Task>()
    private val calls = mutableListOf<String>()
    private fun schedule(ms: Long, action: () -> Unit): () -> Unit {
        val task = Task(ms, action)
        tasks.add(task)
        return { task.cancelled = true }
    }
    private fun fire(ms: Long) {
        val candidates = tasks.filter { it.millis == ms && !it.cancelled }.toList()
        for (task in candidates) { task.cancelled = true; task.call() }
    }
    private fun router(hold: Int = 0, double: Int = 0) = SPenButtonShortcutRouter(
        hold, double, 450L, ::schedule,
        dragStart = { calls.add("dragDown") }, dragRelease = { calls.add("dragUp") },
        singleTap = { calls.add("tap") },
        holdShortcut = { calls.add("hold:$it") }, doubleShortcut = { calls.add("double:$it") }
    )

    @Test fun defaultHoldPreservesImmediateDrag() {
        val r = router()
        r.down()
        assertEquals(listOf("dragDown"), calls)
        r.up()
        assertEquals(listOf("dragDown", "dragUp"), calls)
    }

    @Test fun customHoldEmitsContextWithoutStrayTap() {
        val r = router(hold = 1)
        r.down()
        fire(450)
        r.up()
        assertEquals(listOf("hold:1"), calls)
    }

    @Test fun customHoldStillAllowsQuickLeftTap() {
        val r = router(hold = 3)
        r.down()
        r.up()
        assertEquals(listOf("tap"), calls)
    }

    @Test fun doublePressEmitsOnlyAssignedAction() {
        val r = router(double = 1)
        r.down()
        r.up()
        r.down()
        r.up()
        fire(280)
        assertEquals(listOf("double:1"), calls)
    }

    @Test fun singleTapDeliveredAfterDoubleWindow() {
        val r = router(double = 3)
        r.down()
        r.up()
        assertTrue(calls.isEmpty())
        fire(280)
        assertEquals(listOf("tap"), calls)
    }

    @Test fun doubleEnabledStillAllowsNormalLongDrag() {
        val r = router(double = 4)
        r.down()
        fire(120)
        r.up()
        assertEquals(listOf("dragDown", "dragUp"), calls)
    }

    @Test fun secondLongPressDoesNotDiscardOriginalClick() {
        val r = router(double = 2)
        r.down()
        r.up()
        r.down()
        fire(120)
        r.up()
        assertEquals(listOf("tap", "dragDown", "dragUp"), calls)
    }

    @Test fun cancelStopsAllPendingClickActions() {
        val r = router(double = 1)
        r.down()
        r.up()
        r.cancel()
        fire(280)
        assertTrue(calls.isEmpty())
    }
}
