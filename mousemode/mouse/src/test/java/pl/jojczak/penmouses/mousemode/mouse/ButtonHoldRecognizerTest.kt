package pl.jojczak.penmouses.mousemode.mouse

import org.junit.Assert.*
import org.junit.Test

class ButtonHoldRecognizerTest {
    private var scheduled: (() -> Unit)? = null
    private var cancelled = false
    private var holdCount = 0
    private fun create() = ButtonHoldRecognizer({ _, task ->
        scheduled = task
        val cancel: () -> Unit = { cancelled = true }
        cancel
    }, 450L, { holdCount++ })

    @Test fun shortPressProducesOneTap() {
        val r = create()
        assertTrue(r.press())
        assertTrue(r.release())
        scheduled?.invoke()
        assertEquals(0, holdCount)
        assertTrue(cancelled)
    }

    @Test fun holdFiresOnceAndNeverProducesTap() {
        val r = create()
        assertTrue(r.press())
        scheduled?.invoke()
        scheduled?.invoke()
        assertEquals(1, holdCount)
        assertFalse(r.release())
    }

    @Test fun cancelledHoldDoesNotFireAndSecondPressWorks() {
        val r = create()
        r.press()
        val callback = scheduled
        r.cancel()
        callback?.invoke()
        assertEquals(0, holdCount)
        assertTrue(r.press())
        scheduled?.invoke()
        assertEquals(1, holdCount)
        assertFalse(r.release())
    }

    @Test fun duplicatePressIsIgnored() {
        val r = create()
        assertTrue(r.press())
        assertFalse(r.press())
        scheduled?.invoke()
        assertEquals(1, holdCount)
    }
}
