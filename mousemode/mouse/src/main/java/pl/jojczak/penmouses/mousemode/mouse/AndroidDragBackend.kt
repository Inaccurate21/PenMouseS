package pl.jojczak.penmouses.mousemode.mouse

import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper

internal class AndroidDragBackend(
    private val dispatchGesture: (GestureDescription, GestureResultCallback?, Handler?) -> Boolean,
) : RealtimeDragController.Backend {
    // Not the mode handler: graceful release must still work after BaseMode.stop().
    val handler = Handler(Looper.getMainLooper())
    private var previous: StrokeDescription? = null
    private var end = RealtimeDragController.Point(0f, 0f)

    fun schedule(delayMs: Long, task: () -> Unit): () -> Unit {
        val runnable = Runnable { task() }
        handler.postDelayed(runnable, delayMs)
        return { handler.removeCallbacks(runnable) }
    }

    override fun dispatch(segment: RealtimeDragController.Segment, completed: (Boolean) -> Unit): Boolean {
        val path = Path().apply {
            moveTo(segment.from.x, segment.from.y)
            if (segment.to != segment.from) lineTo(segment.to.x, segment.to.y)
        }
        val stroke = if (segment.continuing) {
            checkNotNull(previous).continueStroke(path, 0L, segment.durationMs, segment.willContinue)
        } else {
            StrokeDescription(path, 0L, segment.durationMs, segment.willContinue)
        }
        val accepted = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) = completed(true)
                override fun onCancelled(gestureDescription: GestureDescription) = completed(false)
            },
            handler,
        )
        if (accepted) {
            previous = stroke
            end = segment.to
        }
        return accepted
    }

    override fun releaseBestEffort() {
        val stroke = previous ?: return
        if (!stroke.willContinue()) return
        // A continuation only; a new independent stroke could accidentally click something.
        runCatching {
            val path = Path().apply { moveTo(end.x, end.y) }
            val release = stroke.continueStroke(path, 0L, 1L, false)
            dispatchGesture(GestureDescription.Builder().addStroke(release).build(), null, handler)
        }
    }

    override fun reset() { previous = null }
}
