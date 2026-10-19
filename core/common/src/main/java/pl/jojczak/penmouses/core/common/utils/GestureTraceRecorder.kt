package pl.jojczak.penmouses.core.common.utils

import android.content.Context
import android.os.SystemClock

/**
 * Optional detailed touch injection trace for S Pen troubleshooting.
 *
 * Coordinates are ONLY the points our own code sends to Android. No screenshots,
 * view content, names, identifiers, or data from other apps are retained.
 * Gallery reactions are inferred from accessibility event types, NEVER asserted as
 * proof that an individual photo changed.
 */
object GestureTraceRecorder {
    @Volatile private var enabled = false
    @Volatile private var lastInjectionAtMs = 0L
    @Volatile private var lastSegment = 0L
    private var nextSegment = 0L

    fun initialize(context: Context) {
        enabled = PreferencesManager(context).get(PrefKeys.MOUSE_GESTURE_TRACE_ENABLED)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        DiagnosticRecorder.record("gesture-trace", "detailed tracing enabled=$value")
    }

    fun isEnabled() = enabled && DiagnosticRecorder.isEnabled()

    @Synchronized fun segment(
        fromX: Float, fromY: Float, toX: Float, toY: Float,
        requestedMs: Long, continuing: Boolean, willContinue: Boolean
    ): Long {
        if (!isEnabled()) return -1L
        val id = ++nextSegment
        lastSegment = id
        lastInjectionAtMs = SystemClock.uptimeMillis()
        DiagnosticRecorder.record(
            "gesture-path",
            "id=$id from=(${fromX.toInt()},${fromY.toInt()}) to=(${toX.toInt()},${toY.toInt()}) " +
                "dx=${(toX - fromX).toInt()} dy=${(toY - fromY).toInt()} " +
                "requestedMs=$requestedMs continuing=$continuing willContinue=$willContinue"
        )
        return id
    }

    fun result(id: Long, status: String, elapsedMs: Long) {
        if (id < 0L || !isEnabled()) return
        DiagnosticRecorder.record("gesture-path-result", "id=$id status=$status callbackElapsedMs=$elapsedMs")
    }

    fun galleryEvent(type: String, scrollDeltaX: Int, scrollDeltaY: Int) {
        if (!isEnabled()) return
        val elapsed = SystemClock.uptimeMillis() - lastInjectionAtMs
        // Only monitor UI reactions around our own injected gestures.
        if (lastSegment <= 0L || elapsed !in 0L..1600L) return
        DiagnosticRecorder.record(
            "gallery-reaction",
            "nearSegment=$lastSegment delayMs=$elapsed event=$type " +
                "scrollDeltaX=$scrollDeltaX scrollDeltaY=$scrollDeltaY; " +
                "UI-event-is-not-proof-of-photo-change"
        )
    }

    fun dragFinished(result: String) {
        if (!isEnabled()) return
        DiagnosticRecorder.record("gesture-trace", "dragFinished=$result; check gallery-reaction events separately")
    }
}
