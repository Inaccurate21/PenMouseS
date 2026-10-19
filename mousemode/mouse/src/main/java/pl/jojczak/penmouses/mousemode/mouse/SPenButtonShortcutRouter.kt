package pl.jojczak.penmouses.mousemode.mouse

/**
 * Maps S Pen button clicks and holds onto actions without overlapping gestures.
 * Default configuration preserves immediate pointer-down / pointer-up drag.
 * When multi-click shortcuts are enabled, a short click waits briefly so that
 * the second/third button press can be distinguished.
 */
internal class SPenButtonShortcutRouter(
    private val holdAction: Int,
    private val doubleAction: Int,
    holdThresholdMs: Long,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val dragStart: () -> Unit,
    private val dragRelease: () -> Unit,
    private val singleTap: () -> Unit,
    private val holdShortcut: (Int) -> Unit,
    private val doubleShortcut: (Int) -> Unit,
    private val tripleAction: Int = 0,
    private val tripleShortcut: (Int) -> Unit = {},
    private val selectionRelease: () -> Unit = {},
) {
    private val holdDelay = holdThresholdMs.coerceIn(250L, 900L)
    private var pressed = false
    private var dragging = false
    private var holdTriggered = false
    private var movementSincePress = 0f
    private var precedingClicks = 0
    private var pendingClicks = 0
    private var cancelPressTimer: (() -> Unit)? = null
    private var cancelPendingClicks: (() -> Unit)? = null

    private val multiClickEnabled get() = doubleAction != 0 || tripleAction != 0

    fun down() {
        if (pressed) return
        pressed = true
        dragging = false
        holdTriggered = false
        movementSincePress = 0f
        precedingClicks = pendingClicks
        pendingClicks = 0
        cancelPendingClicks?.invoke()
        cancelPendingClicks = null
        if (holdAction != 0) {
            cancelPressTimer = schedule(holdDelay) {
                if (pressed && !dragging && !holdTriggered) {
                    flushPrecedingClicks()
                    holdTriggered = true
                    holdShortcut(holdAction)
                }
            }
        } else if (multiClickEnabled) {
            cancelPressTimer = schedule(DRAG_START_DELAY_MS) {
                if (pressed && !dragging && !holdTriggered) {
                    flushPrecedingClicks()
                    dragging = true
                    dragStart()
                }
            }
        } else {
            dragging = true
            dragStart()
        }
    }

    /** Faster drag start after 28px of actual cursor movement in multi-click mode. */
    fun onPointerMotion(distancePx: Float) {
        if (!pressed || dragging || holdTriggered ||
            holdAction != 0 || !multiClickEnabled || !distancePx.isFinite()) return
        movementSincePress += distancePx.coerceAtLeast(0f)
        if (movementSincePress < 28f) return
        cancelPressTimer?.invoke()
        cancelPressTimer = null
        flushPrecedingClicks()
        dragging = true
        dragStart()
    }

    fun up() {
        if (!pressed) return
        pressed = false
        cancelPressTimer?.invoke()
        cancelPressTimer = null
        when {
            dragging -> {
                dragging = false
                dragRelease()
            }
            holdTriggered -> {
                if (holdAction == TEXT_SELECTION_HOLD_ACTION) selectionRelease()
            }
            !multiClickEnabled -> singleTap()
            else -> {
                val clicks = precedingClicks + 1
                precedingClicks = 0
                if (tripleAction != 0 && clicks == 3) {
                    tripleShortcut(tripleAction)
                } else if (tripleAction == 0 && doubleAction != 0 && clicks == 2) {
                    doubleShortcut(doubleAction)
                } else {
                    pendingClicks = clicks
                    cancelPendingClicks = schedule(MULTI_PRESS_WINDOW_MS) {
                        flushPendingClicks()
                    }
                }
            }
        }
        holdTriggered = false
        precedingClicks = 0
    }

    private fun flushPrecedingClicks() {
        val count = precedingClicks
        precedingClicks = 0
        flushClicks(count)
    }

    private fun flushPendingClicks() {
        val count = pendingClicks
        pendingClicks = 0
        cancelPendingClicks = null
        flushClicks(count)
    }

    private fun flushClicks(count: Int) {
        when (count) {
            1 -> singleTap()
            2 -> {
                if (doubleAction != 0) doubleShortcut(doubleAction)
                else { singleTap(); singleTap() }
            }
        }
    }

    fun cancel() {
        cancelPressTimer?.invoke()
        cancelPressTimer = null
        cancelPendingClicks?.invoke()
        cancelPendingClicks = null
        if (pressed && holdTriggered && holdAction == TEXT_SELECTION_HOLD_ACTION) {
            selectionRelease()
        }
        pressed = false
        dragging = false
        holdTriggered = false
        movementSincePress = 0f
        precedingClicks = 0
        pendingClicks = 0
    }

    companion object {
        const val TEXT_SELECTION_HOLD_ACTION = 5
        private const val DRAG_START_DELAY_MS = 120L
        private const val MULTI_PRESS_WINDOW_MS = 280L
    }
}
