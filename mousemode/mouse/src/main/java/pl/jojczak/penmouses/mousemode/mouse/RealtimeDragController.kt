package pl.jojczak.penmouses.mousemode.mouse

/** Android-independent, single-threaded state machine. Never dispatches overlapping segments. */
internal class RealtimeDragController(
    private val backend: Backend,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val segmentDurationMs: Long = 16L,
    private val onFinished: (Result) -> Unit = {},
) {
    data class Point(val x: Float, val y: Float)
    data class Segment(
        val from: Point,
        val to: Point,
        val durationMs: Long,
        val continuing: Boolean,
        val willContinue: Boolean,
    )

    interface Backend {
        fun dispatch(segment: Segment, completed: (Boolean) -> Unit): Boolean
        /** Only used when a callback stalls. Must not inject a fresh tap. */
        fun releaseBestEffort()
        fun reset()
    }

    enum class Result { RELEASED, CLOSED, HOLD_LIMIT, CANCELLED, REJECTED, CALLBACK_TIMEOUT }

    var isActive = false
        private set
    private var pressed = false
    private var closed = false
    private var continuing = false
    private var end = Point(0f, 0f)
    private var target = end
    private var inFlight = false
    private var serial = 0L
    private var cancelWatchdog: (() -> Unit)? = null
    private var cancelHoldLimit: (() -> Unit)? = null
    private var finishResult = Result.RELEASED

    fun press(point: Point): Boolean {
        if (closed || isActive || !point.x.isFinite() || !point.y.isFinite()) return false
        isActive = true
        pressed = true
        continuing = false
        end = point
        target = point
        finishResult = Result.RELEASED
        cancelHoldLimit = schedule(MAX_HOLD_MS) {
            if (isActive) {
                finishResult = Result.HOLD_LIMIT
                release()
            }
        }
        pump()
        return isActive
    }

    fun move(point: Point) {
        if (!isActive || !pressed || !point.x.isFinite() || !point.y.isFinite()) return
        target = point
        pump()
    }

    fun release() {
        if (!isActive) return
        pressed = false
        pump()
    }

    /** May finish asynchronously: the backend's handler must outlive the owning mode. */
    fun close() {
        closed = true
        if (isActive) {
            finishResult = Result.CLOSED
            release()
        }
    }

    private fun pump() {
        if (!isActive || inFlight) return
        if (continuing && pressed && end == target) return // Keep pointer down without polling.
        val segment = Segment(
            from = end,
            to = target,
            durationMs = if (!continuing || !pressed) 1L else segmentDurationMs.coerceIn(8L, 40L),
            continuing = continuing,
            willContinue = pressed,
        )
        val request = ++serial
        inFlight = true
        cancelWatchdog = schedule(CALLBACK_TIMEOUT_MS) {
            if (isActive && inFlight && serial == request) {
                backend.releaseBestEffort()
                finish(Result.CALLBACK_TIMEOUT)
            }
        }
        val accepted = try {
            backend.dispatch(segment) { success ->
                if (!isActive || serial != request || !inFlight) return@dispatch
                cancelWatchdog?.invoke()
                cancelWatchdog = null
                inFlight = false
                if (!success) {
                    finish(Result.CANCELLED)
                } else {
                    end = segment.to
                    continuing = true
                    if (!segment.willContinue) finish(finishResult) else pump()
                }
            }
        } catch (_: RuntimeException) {
            false
        }
        if (!accepted && isActive && serial == request) {
            backend.releaseBestEffort()
            finish(Result.REJECTED)
        }
    }

    private fun finish(result: Result) {
        ++serial // Discard late callbacks from this gesture.
        isActive = false
        pressed = false
        inFlight = false
        continuing = false
        cancelWatchdog?.invoke()
        cancelHoldLimit?.invoke()
        cancelWatchdog = null
        cancelHoldLimit = null
        backend.reset()
        onFinished(result)
    }

    companion object {
        const val MAX_HOLD_MS = 30_000L
        private const val CALLBACK_TIMEOUT_MS = 1_000L
    }
}
