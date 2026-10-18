package pl.jojczak.penmouses.mousemode.mouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.samsung.android.sdk.penremote.ButtonEvent
import pl.jojczak.penmouses.core.common.notifications.NotificationsManager
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.ModeStatus
import pl.jojczak.penmouses.core.common.spen.SPenManager
import pl.jojczak.penmouses.core.common.spen.listener.ButtonAction
import pl.jojczak.penmouses.core.common.spen.listener.ConnectionResultCallback
import pl.jojczak.penmouses.core.common.utils.DiagnosticRecorder
import pl.jojczak.penmouses.core.common.utils.PrefKeys
import pl.jojczak.penmouses.core.common.utils.PreferencesManager
import pl.jojczak.penmouses.core.common.utils.getDisplaySize
import pl.jojczak.penmouses.mousemode.base.PenConst
import pl.jojczak.penmouses.mousemode.base.PenRunnable
import pl.jojczak.penmouses.mousemode.basecursor.CursorMode
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.roundToInt
import pl.jojczak.penmouses.core.ui.R as coreR

class MouseMode(
    dispatchGesture: (GestureDescription, AccessibilityService.GestureResultCallback?, Handler?) -> Boolean,
    notificationsManager: NotificationsManager,
    preferences: PreferencesManager,
    sPenManager: SPenManager,
    stopService: () -> Unit,
    context: Context,
) : CursorMode<MouseAnimator>(
    notificationsManager = notificationsManager,
    dispatchGesture = dispatchGesture,
    preferences = preferences,
    sPenManager = sPenManager,
    stopService = stopService,
    context = context,
    modeStatus = ModeStatus.Mouse,
    animatorFactory = { view -> MouseAnimator(view) }
) {
    private var prefSensitivity = cursorPreferences.getSensitivity()
    private var prefAcceleration = preferences.get(PrefKeys.MOUSE_ACCELERATION)
    private var prefRotation = preferences.get(PrefKeys.MOUSE_AXIS_ROTATION).toInt()
    private val airMotionAcceleration = AirMotionAcceleration()
    private var prefHideDelay = cursorPreferences.getHideDelay()
    private var prefIsSleepEnabled = cursorPreferences.isSleepEnabled()
    private var realtimeEnabled = preferences.get(PrefKeys.MOUSE_REALTIME_DRAG)
    private var dragController: RealtimeDragController? = null
    private val sPenBatteryOverlay = SPenBatteryOverlay(context, preferences)
    private var legacyButtonDown = false
    private var ignoreUntilRelease = false
    private var motionX: Float? = null
    private var motionY: Float? = null
    private var receiverRegistered = false
    private var dragGeneration = 0L
    private var lastDiagnosticMotionMs = 0L
    private var buttonHoldAction = preferences.get(PrefKeys.MOUSE_HOLD_ACTION).toInt()
    private var holdRecognizer = buildHoldRecognizer()
    private var rightwardCalibration: RightwardMotionCalibration? = null
    private var finishCalibration: Runnable? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) stopService()
        }
    }

    private fun newDragController(): RealtimeDragController {
        val generation = ++dragGeneration
        val backend = AndroidDragBackend(dispatchGesture, preferences.get(PrefKeys.MOUSE_ADAPTIVE_DRAG))
        return RealtimeDragController(
            backend = backend,
            schedule = backend::schedule,
            segmentDurationMs = preferences.get(PrefKeys.MOUSE_DRAG_SEGMENT_MS).toLong(),
            onFinished = { result ->
                if (generation != dragGeneration || !canStartJobs) return@RealtimeDragController
                cursorAnimator.releaseCursor()
                ignoreUntilRelease = sPenManager.isSPenButtonDown
                hideCursor()
                if (result != RealtimeDragController.Result.RELEASED &&
                    result != RealtimeDragController.Result.CLOSED) {
                    Toast.makeText(context, context.getString(coreR.string.realtime_drag_interrupted), Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    private fun buildHoldRecognizer() = ButtonHoldRecognizer(
        schedule = { duration, action ->
            val task = Runnable { action() }
            mainHandler.postDelayed(task, duration)
            { mainHandler.removeCallbacks(task) }
        },
        thresholdMs = preferences.get(PrefKeys.MOUSE_HOLD_THRESHOLD_MS).toLong(),
        onHold = {
            DiagnosticRecorder.record("hold", "activated action=$buttonHoldAction")
            when (buttonHoldAction) {
                1 -> performTouchAtCursor(550L)
                2 -> performDoubleTapAtCursor()
            }
        }
    )

    /** Accessibility gestures are touchscreen input, not native mouse secondary button events. */
    private fun performTouchAtCursor(durationMs: Long) {
        val point = getCursorPos()
        val staticPath = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(staticPath, 0L, durationMs)
        val accepted = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        DiagnosticRecorder.record("hold", "touch durationMs=$durationMs accepted=$accepted")
    }

    private fun performDoubleTapAtCursor() {
        val point = getCursorPos()
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 45L))
            .addStroke(GestureDescription.StrokeDescription(path, 125L, 45L))
            .build()
        val accepted = dispatchGesture(gesture, null, null)
        DiagnosticRecorder.record("hold", "doubleTap accepted=$accepted")
    }

    private var path = Path()
    private var pathStartTime: Long = 0
    private var cursorStartPos = Point()

    private var isHidden = false
    private var isSleeping = false


    override fun start() {
        super.start()

        cursorAnimator.targetOpacity = cursorPreferences.getOpacity(modeStatus = ModeStatus.Mouse)
        DiagnosticRecorder.record("mode", "start mouse realtime=$realtimeEnabled")
        if (realtimeEnabled) dragController = newDragController()
        sPenBatteryOverlay.refresh()
        ContextCompat.registerReceiver(context, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        sPenManager.onConnectionLost = { stopService() }

        sPenManager.connect(object : ConnectionResultCallback() {
            override fun onSuccess() {
                DiagnosticRecorder.record("connection", "S Pen connected")
                if (!canStartJobs) return
                pingCursor()
                mainHandler.postDelayed({
                    if (!canStartJobs) return@postDelayed
                    sPenManager.registerButtonEventListener(
                        ::onButtonEvent,
                        ::showErrorAndStopService
                    )
                    sPenManager.registerAirMotionEventListener(
                        ::onAirMotionEvent,
                        ::showErrorAndStopService
                    )
                }, PenConst.DELAY_TO_EVENT_REGISTER_MS)
            }

            override fun onFailure(errorCode: Int) {
                DiagnosticRecorder.record("connection", "failed code=$errorCode")
                showErrorAndStopService(errorCode)
            }
        })
    }

    fun refreshBatteryStatus() = sPenBatteryOverlay.refresh()

    /**
     * Calibrate using a deliberate rightward S Pen sweep, not an unreliable
     * automatic orientation guess from arbitrary cursor movements.
     */
    fun startDirectionCalibration() {
        if (!canStartJobs || dragController?.isActive == true ||
            legacyButtonDown || sPenManager.isSPenButtonDown) {
            Toast.makeText(context, "Zwolnij przycisk S Pena i spróbuj ponownie", Toast.LENGTH_SHORT).show()
            return
        }
        finishCalibration?.let(mainHandler::removeCallbacks)
        rightwardCalibration = RightwardMotionCalibration()
        airMotionAcceleration.reset()
        DiagnosticRecorder.record("calibration", "start: move the S Pen right for 3s")
        Toast.makeText(context, "Teraz przesuwaj S Pen w PRAWO przez 3 sekundy", Toast.LENGTH_LONG).show()
        val task = Runnable {
            finishCalibration = null
            val sample = rightwardCalibration ?: return@Runnable
            rightwardCalibration = null
            val rotation = sample.inferRotation()
            DiagnosticRecorder.record("calibration", sample.summary())
            if (rotation == null) {
                Toast.makeText(context, "Kalibracja niepewna — powtórz wyraźniejszy ruch", Toast.LENGTH_LONG).show()
            } else {
                // One guided movement alone is not enough to safely apply a correction.
                // Keep the existing orientation and provide a suggestion for manual testing.
                DiagnosticRecorder.record("calibration", "suggestedAxis=${rotation} currentAxis=${prefRotation} autoApply=false")
                Toast.makeText(context, "Test wskazuje ${rotation}°. Nie zmieniono osi — ustaw ją ręcznie, jeśli chcesz.", Toast.LENGTH_LONG).show()
            }
        }
        finishCalibration = task
        mainHandler.postDelayed(task, 3_000L)
    }

    fun recalibrateMotion() {
        if (!canStartJobs) return
        DiagnosticRecorder.record("motion", "manual sensor listener restart")
        airMotionAcceleration.reset()
        motionX = null
        motionY = null
        sPenManager.unregisterAirMotionEventListener()
        sPenManager.registerAirMotionEventListener(::onAirMotionEvent, ::showErrorAndStopService)
    }

    override fun stop() {
        finishCalibration?.let(mainHandler::removeCallbacks)
        finishCalibration = null
        rightwardCalibration = null
        holdRecognizer.cancel()
        DiagnosticRecorder.record("mode", "stop mouse")
        ++dragGeneration
        dragController?.close()
        dragController = null
        sPenBatteryOverlay.hide()
        legacyButtonDown = false
        airMotionAcceleration.reset()
        sPenManager.onConnectionLost = null
        if (receiverRegistered) {
            context.unregisterReceiver(screenOffReceiver)
            receiverRegistered = false
        }
        super.stop()
        sPenManager.disconnect()
    }

    private fun onButtonEvent(@ButtonAction type: Int, timeStamp: Long) {
        if (!canStartJobs) return
        DiagnosticRecorder.record("button", "type=$type realtime=$realtimeEnabled timestamp=$timeStamp")
        if (type == ButtonEvent.ACTION_UP && ignoreUntilRelease) {
            ignoreUntilRelease = false
            return
        }
        if (ignoreUntilRelease) return
        if (buttonHoldAction != 0) {
            when (type) {
                ButtonEvent.ACTION_DOWN -> {
                    showCursor()
                    if (holdRecognizer.press()) {
                        cursorAnimator.clickCursor()
                        DiagnosticRecorder.record("hold", "pressed mode=$buttonHoldAction")
                    }
                }
                ButtonEvent.ACTION_UP -> {
                    val shortTap = holdRecognizer.release()
                    cursorAnimator.releaseCursor()
                    if (shortTap) {
                        performTouchAtCursor(55L)
                        DiagnosticRecorder.record("hold", "shortTap")
                    } else {
                        DiagnosticRecorder.record("hold", "release after hold / unmatched")
                    }
                    hideCursor()
                }
            }
            return
        }
        if (realtimeEnabled) {
            if (type == ButtonEvent.ACTION_DOWN) {
                showCursor()
                val point = getCursorPos()
                if (dragController?.press(RealtimeDragController.Point(point.x.toFloat(), point.y.toFloat())) == true) {
                    cursorAnimator.clickCursor()
                }
            } else if (type == ButtonEvent.ACTION_UP) {
                dragController?.release()
            }
            return
        }
        if (type == ButtonEvent.ACTION_DOWN) {
            if (legacyButtonDown) return
            legacyButtonDown = true
            showCursor()
            val cursorPosition = getCursorPos()
            // Use one known monotonic clock; Samsung timestamp units vary by SDK/framework.
            pathStartTime = SystemClock.uptimeMillis()
            cursorStartPos = Point(cursorPosition)
            cursorAnimator.clickCursor()

            path = Path()
            path.moveTo(cursorPosition.x.toFloat(), cursorPosition.y.toFloat())

            mainHandler.post(updateStrokePathJob)
        } else if (type == ButtonEvent.ACTION_UP) {
            if (!legacyButtonDown) return
            legacyButtonDown = false
            hideCursor()
            cursorAnimator.releaseCursor()

            mainHandler.removeCallbacks(updateStrokePathJob)

            performGesture(duration = SystemClock.uptimeMillis() - pathStartTime)
        }
    }

    private fun onAirMotionEvent(deltaX: Float, deltaY: Float, timeStamp: Long) {
        if (!canStartJobs || !deltaX.isFinite() || !deltaY.isFinite()) return
        if (rightwardCalibration != null) {
            rightwardCalibration?.add(deltaX, deltaY)
            return
        }
        pingCursor()

        val (screenWidth, screenHeight) = getDisplaySize(getDisplay())

        val position = getCursorPos()
        val movement = airMotionAcceleration.convert(deltaX, deltaY, timeStamp, prefSensitivity, prefAcceleration, prefRotation)
        if (DiagnosticRecorder.isEnabled() && SystemClock.uptimeMillis() - lastDiagnosticMotionMs > 160L) {
            lastDiagnosticMotionMs = SystemClock.uptimeMillis()
            DiagnosticRecorder.record("motion", "deltaX=$deltaX deltaY=$deltaY timestamp=$timeStamp pxX=${movement.dx} pxY=${movement.dy} axis=$prefRotation displayRotation=${getDisplay().rotation}")
        }
        motionX = ((motionX ?: position.x.toFloat()) + movement.dx).coerceIn(0f, (screenWidth - 1).coerceAtLeast(0).toFloat())
        motionY = ((motionY ?: position.y.toFloat()) + movement.dy).coerceIn(0f, (screenHeight - 1).coerceAtLeast(0).toFloat())
        updateCursorLayoutParams {
            x = motionX!!.roundToInt()
            y = motionY!!.roundToInt()
        }
        if (realtimeEnabled) {
            val point = getCursorPos()
            dragController?.move(RealtimeDragController.Point(point.x.toFloat(), point.y.toFloat()))
        }
    }

    private val updateStrokePathJob = object : PenRunnable(canStartJobs) {
        override fun callback() {
            if (!legacyButtonDown) return

            val duration = SystemClock.uptimeMillis() - pathStartTime
            if (duration >= PenConst.MAX_CLICK_TIME_MS) {
                legacyButtonDown = false
                cursorAnimator.releaseCursor()
                performGesture(PenConst.MAX_CLICK_TIME_MS)
                return
            }

            val cursorPosition = getCursorPos()
            path.lineTo(cursorPosition.x.toFloat(), cursorPosition.y.toFloat())
            mainHandler.postDelayed(this, PenConst.CLICK_PEN_UPDATE_INTERVAL_MS)
        }
    }

    private fun performGesture(duration: Long) {
        val cursorPosition = getCursorPos()
        val dx = (cursorPosition.x - cursorStartPos.x).toFloat()
        val dy = (cursorPosition.y - cursorStartPos.y).toFloat()
        val distance = sqrt(dx.pow(2) + dy.pow(2))

        val (mDuration, mPath) = if (distance < CURSOR_MOVE_THRESHOLD_PX) {
            val staticPath = Path()
            staticPath.moveTo(cursorPosition.x.toFloat(), cursorPosition.y.toFloat())

            duration to staticPath
        } else {
            (duration / 2) to path
        }

        val stroke = GestureDescription.StrokeDescription(mPath, 0, mDuration.coerceIn(1L, PenConst.MAX_CLICK_TIME_MS))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun pingCursor() {
        showCursor()
        hideCursor()
    }

    private fun showCursor() {
        mainHandler.removeCallbacks(fadeOutCursorJob)
        mainHandler.removeCallbacks(sleepJob)
        if (isHidden || isSleeping) {
            isHidden = false
            notificationsManager.showIdleNotification(context)
            cursorAnimator.fadeInCursor()
        }
        if (isSleeping) {
            isSleeping = false
            sPenManager.registerAirMotionEventListener(
                ::onAirMotionEvent,
                ::showErrorAndStopService
            )
            return
        }
    }

    private fun hideCursor() {
        if (!canStartJobs || dragController?.isActive == true || legacyButtonDown) return
        if (prefHideDelay == PrefKeys.MOUSE_CURSOR_HIDE_DELAY.range.endInclusive.toLong()) {
            if (prefIsSleepEnabled) {
                mainHandler.postDelayed(sleepJob, prefHideDelay + SLEEP_DELAY_MS_HIDING_DISABLED)
            }
        } else {
            mainHandler.postDelayed(fadeOutCursorJob, prefHideDelay)
            if (prefIsSleepEnabled) {
                mainHandler.postDelayed(sleepJob, prefHideDelay + SLEEP_DELAY_MS)
            }
        }
    }

    private val fadeOutCursorJob = PenRunnable.create(canStartJobs) {
        isHidden = true
        cursorAnimator.fadeOutCursor()
        notificationsManager.showMouseHiddenNotification(context)
    }

    private val sleepJob = PenRunnable.create(canStartJobs) {
        isSleeping = true
        cursorAnimator.fadeOutCursor()
        sPenManager.unregisterAirMotionEventListener()
        notificationsManager.showMouseSleepNotification(context)
    }

    override fun preferencesUpdated() {
        super.preferencesUpdated()
        legacyButtonDown = false
        holdRecognizer.cancel()
        mainHandler.removeCallbacks(updateStrokePathJob)
        cursorAnimator.releaseCursor()
        ++dragGeneration
        dragController?.close()
        ignoreUntilRelease = sPenManager.isSPenButtonDown
        realtimeEnabled = preferences.get(PrefKeys.MOUSE_REALTIME_DRAG)
        dragController = if (realtimeEnabled) newDragController() else null
        sPenBatteryOverlay.refresh()
        prefHideDelay = cursorPreferences.getHideDelay()
        prefSensitivity = cursorPreferences.getSensitivity()
        prefAcceleration = preferences.get(PrefKeys.MOUSE_ACCELERATION)
        buttonHoldAction = preferences.get(PrefKeys.MOUSE_HOLD_ACTION).toInt()
        holdRecognizer = buildHoldRecognizer()
        prefRotation = preferences.get(PrefKeys.MOUSE_AXIS_ROTATION).toInt()
        DiagnosticRecorder.record("preferences", "axis=$prefRotation sensitivity=$prefSensitivity acceleration=$prefAcceleration")
        airMotionAcceleration.reset()
        prefIsSleepEnabled = cursorPreferences.isSleepEnabled()
        cursorAnimator.targetOpacity = cursorPreferences.getOpacity(modeStatus = ModeStatus.Mouse)
        pingCursor()
    }

    companion object {
        private const val CURSOR_MOVE_THRESHOLD_PX = 50
        private const val SLEEP_DELAY_MS = 60000L // 60s
        private const val SLEEP_DELAY_MS_HIDING_DISABLED = 300000L // 5min
    }
}
