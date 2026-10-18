package pl.jojczak.penmouses.mousemode.mouse

/**
 * Recognizes a short S Pen press versus a sustained hold without requiring Android.
 * onHold is invoked exactly once if the press remains active until the threshold.
 */
internal class ButtonHoldRecognizer(
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val thresholdMs: Long,
    private val onHold: () -> Unit,
) {
    private var cancelScheduled: (() -> Unit)? = null
    private var pressed = false
    private var held = false

    fun press(): Boolean {
        if (pressed) return false
        pressed = true
        held = false
        cancelScheduled = schedule(thresholdMs.coerceIn(250L, 900L)) {
            if (pressed && !held) {
                held = true
                onHold()
            }
        }
        return true
    }

    /** True if the action should be a single, short left tap. */
    fun release(): Boolean {
        if (!pressed) return false
        pressed = false
        cancelScheduled?.invoke()
        cancelScheduled = null
        return !held
    }

    fun cancel() {
        pressed = false
        held = false
        cancelScheduled?.invoke()
        cancelScheduled = null
    }
}
