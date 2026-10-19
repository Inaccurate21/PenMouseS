package pl.jojczak.penmouses.mousemode.mouse

/**
 * Maps S Pen button holds and double presses without synthesizing stray clicks.
 *
 * With both custom actions disabled MouseMode keeps its original immediate drag code.
 * With a custom double press the first short click waits for the second press.
 * A sustained press still begins the normal drag (after a brief 120ms detection window),
 * unless the user explicitly assigns an alternative hold action.
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
) {
    private val holdDelay = holdThresholdMs.coerceIn(250L, 900L)
    private var pressed = false
    private var secondPress = false
    private var dragging = false
    private var holdTriggered = false
    private var pendingSingle = false
    private var movementSincePress = 0f
    private var cancelPressTimer: (() -> Unit)? = null
    private var cancelPendingSingle: (() -> Unit)? = null

    fun down() {
        if (pressed) return
        pressed = true
        movementSincePress = 0f
        dragging = false
        holdTriggered = false
        secondPress = pendingSingle
        if (secondPress) {
            cancelPendingSingle?.invoke()
            cancelPendingSingle = null
            pendingSingle = false
        }
        if (holdAction != 0) {
            cancelPressTimer = schedule(holdDelay) {
                if (pressed && !holdTriggered && !dragging) {
                    if (secondPress) singleTap() // second press became a hold, not a double
                    secondPress = false
                    holdTriggered = true
                    holdShortcut(holdAction)
                }
            }
        } else if (doubleAction != 0) {
            cancelPressTimer = schedule(DRAG_START_DELAY_MS) {
                if (pressed && !dragging && !holdTriggered) {
                    if (secondPress) singleTap()
                    secondPress = false
                    dragging = true
                    dragStart()
                }
            }
        } else {
            dragging = true
            dragStart()
        }
    }

    /**
     * A real pointer movement during a held press is a drag, not a double click.
     * This removes the fixed 120 ms startup delay for fast gallery photo swipes.
     * Small hand tremors (< 28 px total) are intentionally ignored.
     */
    fun onPointerMotion(distancePx: Float) {
        if (!pressed || dragging || holdTriggered ||
            holdAction != 0 || doubleAction == 0 || !distancePx.isFinite()) return
        movementSincePress += distancePx.coerceAtLeast(0f)
        if (movementSincePress < 28f) return
        cancelPressTimer?.invoke()
        cancelPressTimer = null
        if (secondPress) singleTap()
        secondPress = false
        dragging = true
        dragStart()
    }

    fun up() {
        if (!pressed) return
        pressed = false
        cancelPressTimer?.invoke()
        cancelPressTimer = null
        if (dragging) {
            dragging = false
            dragRelease()
        } else if (!holdTriggered) {
            if (secondPress) {
                secondPress = false
                doubleShortcut(doubleAction)
            } else if (doubleAction != 0) {
                pendingSingle = true
                cancelPendingSingle = schedule(DOUBLE_PRESS_WINDOW_MS) {
                    if (pendingSingle) {
                        pendingSingle = false
                        singleTap()
                    }
                }
            } else {
                singleTap()
            }
        }
        holdTriggered = false
    }

    fun cancel() {
        cancelPressTimer?.invoke()
        cancelPressTimer = null
        cancelPendingSingle?.invoke()
        cancelPendingSingle = null
        pressed = false
        secondPress = false
        movementSincePress = 0f
        pendingSingle = false
        dragging = false
        holdTriggered = false
    }

    private companion object {
        const val DRAG_START_DELAY_MS = 120L
        const val DOUBLE_PRESS_WINDOW_MS = 280L
    }
}
