package pl.jojczak.penmouses.mousemode.mouse

/** Android-independent, single-threaded state machine. Never dispatches overlapping segments. */
internal class RealtimeDragController(
    private val backend: Backend,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val segmentDurationMs: Long = 16L,
    private val onFinished: (Result) -> Unit = {},
    private val releaseGraceMs: Long = 0L,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val onReleaseGrace: (String) -> Unit = {},
    private val onReleaseBrake: (String) -> Unit = {},

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
        fun suggestedDurationMs(configuredMs: Long): Long = configuredMs
    }

    enum class Result { RELEASED, CLOSED, HOLD_LIMIT, CANCELLED, REJECTED, CALLBACK_TIMEOUT }

    var isActive = false
        private set
    private var pressed = false
    private var closed = false
    private var continuing = false
    private var end = Point(0f, 0f)
    private var target = end
    private var pressStart = end
    // Horizontal swipes need a stationary pointer-up tail to avoid Gallery
    // interpreting a final high-velocity move as a multi-photo fling.
    private var brakeOnRelease = false
    private var inFlight = false
    private var serial = 0L
    private var cancelWatchdog: (() -> Unit)? = null
    private var cancelHoldLimit: (() -> Unit)? = null
    private var finishResult = Result.RELEASED
    private var pressStartedAtMs = 0L
    private var gracePending = false
    private var cancelGrace: (() -> Unit)? = null
    val awaitingLateMotion: Boolean get() = isActive && gracePending

    fun press(point: Point): Boolean {
        if (closed || isActive || !point.x.isFinite() || !point.y.isFinite()) return false
        isActive = true
        pressed = true
        continuing = false
        end = point
        target = point
        pressStart = point
        brakeOnRelease = false
        finishResult = Result.RELEASED
        gracePending = false
        pressStartedAtMs = clockMs()
        cancelGrace?.invoke()
        cancelGrace = null
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
        if (gracePending) {
            val sampleDistance = kotlin.math.hypot(point.x - target.x, point.y - target.y)
            target = point
            if (sampleDistance >= MIN_LATE_SAMPLE_PX) {
                onReleaseGrace("sample-captured px=$sampleDistance")
                completeRelease()
            } else {
                pump()
            }
            return
        }
        target = point
        pump()
    }

    fun release() {
        if (!isActive) return
        if (gracePending) {
            completeRelease()
            return
        }
        val heldMs = clockMs() - pressStartedAtMs
        val horizontalDistance = kotlin.math.abs(target.x - pressStart.x)
        val verticalDistance = kotlin.math.abs(target.y - pressStart.y)
        brakeOnRelease = !closed && finishResult == Result.RELEASED &&
            horizontalDistance >= MIN_HORIZONTAL_SWIPE_PX &&
            horizontalDistance >= verticalDistance * HORIZONTAL_DOMINANCE
        if (brakeOnRelease) {
            onReleaseBrake("armed dx=${target.x - pressStart.x} dy=${target.y - pressStart.y} " +
                "holdMs=${heldMs} stationaryReleaseMs=$FLING_BRAKE_MS")
        }
        // Do not accept delayed sensor samples after a completed horizontal swipe.
        // Previously a single release could add another ~160 px to a Gallery fling.
        val eligible = !brakeOnRelease && !closed && finishResult == Result.RELEASED &&
            releaseGraceMs > 0L && heldMs in MIN_GRACE_HOLD_MS..MAX_GRACE_HOLD_MS
        if (eligible) {
            gracePending = true
            onReleaseGrace("waiting heldMs=$heldMs waitMs=$releaseGraceMs")
            cancelGrace = schedule(releaseGraceMs.coerceIn(20L, 160L)) {
                if (isActive && gracePending) {
                    onReleaseGrace("timeout")
                    completeRelease()
                }
            }
        } else {
            completeRelease()
        }
    }

    private fun completeRelease() {
        cancelGrace?.invoke()
        cancelGrace = null
        gracePending = false
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
            durationMs = when {
                !continuing -> 1L
                !pressed && brakeOnRelease && end == target -> FLING_BRAKE_MS
                !pressed && end != target -> segmentDurationMs.coerceIn(12L, 32L)
                !pressed -> 1L
                else -> backend.suggestedDurationMs(segmentDurationMs.coerceIn(1L, 32L))
            },
            continuing = continuing,
            willContinue = pressed || (brakeOnRelease && end != target),
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
        brakeOnRelease = false
        cancelWatchdog?.invoke()
        cancelHoldLimit?.invoke()
        cancelGrace?.invoke()
        cancelWatchdog = null
        cancelHoldLimit = null
        cancelGrace = null
        gracePending = false
        backend.reset()
        onFinished(result)
    }

    companion object {
        const val MAX_HOLD_MS = 30_000L
        private const val CALLBACK_TIMEOUT_MS = 1_000L
        private const val MIN_GRACE_HOLD_MS = 115L
        private const val MAX_GRACE_HOLD_MS = 480L
        private const val MIN_LATE_SAMPLE_PX = 28f
        private const val MIN_HORIZONTAL_SWIPE_PX = 180f
        private const val HORIZONTAL_DOMINANCE = 1.5f
        private const val FLING_BRAKE_MS = 96L
    }
}
