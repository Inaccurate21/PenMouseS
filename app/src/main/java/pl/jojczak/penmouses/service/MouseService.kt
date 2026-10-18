package pl.jojczak.penmouses.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import pl.jojczak.penmouses.core.common.notifications.NotificationsManager
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.Event
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.ModeStatus
import pl.jojczak.penmouses.core.common.spen.SPenManager
import pl.jojczak.penmouses.core.common.utils.PreferencesManager
import pl.jojczak.penmouses.core.common.utils.DiagnosticRecorder
import pl.jojczak.penmouses.core.common.utils.PrefKeys
import pl.jojczak.penmouses.mousemode.base.BaseMode
import pl.jojczak.penmouses.mousemode.mouse.MouseMode
import pl.jojczak.penmouses.mousemode.point.PointMode
import pl.jojczak.penmouses.mousemode.scroll.ScrollMode
import javax.inject.Inject

@AndroidEntryPoint
class MouseService : AccessibilityService() {

    private val serviceScope = CoroutineScope(context = SupervisorJob() + Dispatchers.Main.immediate)
    private var eventCollectorJob: Job? = null
    private var modeChangeJob: Job? = null
    private var currentMode: BaseMode? = null
    private var lastBatteryCaptureMs = 0L

    @Inject
    lateinit var preferences: PreferencesManager

    @Inject
    lateinit var sPenManager: SPenManager

    @Inject
    lateinit var notificationsManager: NotificationsManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        DiagnosticRecorder.record("service", "accessibility connected")
        DiagnosticRecorder.setEnabled(preferences.get(PrefKeys.MOUSE_DIAGNOSTICS_ENABLED))
        registerReceiver()
    }

    private fun registerReceiver() {
        cancelAppToServiceEventObserver()
        eventCollectorJob = serviceScope.launch {
            AppToServiceEvent.event.collect(collector = ::eventManager)
        }
    }

    private fun eventManager(event: Event) {
        Log.d(TAG, "Received event: $event")
        DiagnosticRecorder.record("service-event", event.toString())
        when (event) {
            is Event.Start -> stopCurrentStartNew(newMode = event.mode)
            is Event.Stop -> stopMode()
            is Event.PreferencesUpdated -> currentMode?.preferencesUpdated()
            is Event.RecalibrateMotion -> (currentMode as? MouseMode)?.recalibrateMotion()
            is Event.CalibrateRightwardMotion -> (currentMode as? MouseMode)?.startDirectionCalibration()
        }
    }

    private fun stopCurrentStartNew(newMode: ModeStatus) {
        modeChangeJob?.cancel()
        stopMode(stopMode = ModeStatus.Loading)
        modeChangeJob = serviceScope.launch {
            delay(timeMillis = DELAY_BETWEEN_MODES)
            currentMode = getNewMode(newMode = newMode)
            currentMode?.start()
            coroutineContext.ensureActive()
            AppToServiceEvent.serviceStatus.tryEmit(value = newMode)
        }
    }

    private fun getNewMode(newMode: ModeStatus) = when (newMode) {
        ModeStatus.Mouse -> MouseMode(
            notificationsManager = notificationsManager,
            dispatchGesture = ::dispatchGesture,
            preferences = preferences,
            sPenManager = sPenManager,
            stopService = ::stopMode,
            context = this,
        )

        ModeStatus.Point -> PointMode(
            notificationsManager = notificationsManager,
            dispatchGesture = ::dispatchGesture,
            preferences = preferences,
            sPenManager = sPenManager,
            stopService = ::stopMode,
            context = this,
        )

        ModeStatus.Scroll -> ScrollMode(
            notificationsManager = notificationsManager,
            dispatchGesture = ::dispatchGesture,
            preferences = preferences,
            sPenManager = sPenManager,
            stopService = ::stopMode,
            context = this,
        )

        else -> null
    }

    private fun stopMode(stopMode: ModeStatus = ModeStatus.Off) {
        modeChangeJob?.cancel()
        val currentModeName = currentMode?.let { it::class.simpleName } ?: "null"
        Log.d(TAG, "Stopping current mode ($currentModeName)")
        DiagnosticRecorder.record("service", "stop mode=$currentModeName -> $stopMode")
        val mode = currentMode
        currentMode = null
        mode?.stop()
        sPenManager.disconnect()
        AppToServiceEvent.serviceStatus.tryEmit(value = stopMode)
    }

    private fun cancelAppToServiceEventObserver() {
        eventCollectorJob?.cancel()
        eventCollectorJob = null
    }

    override fun onInterrupt() {
        Log.e(TAG, "Accessibility service interrupted")
        val power = getSystemService(android.os.PowerManager::class.java)
        DiagnosticRecorder.record("service", "accessibility interrupted active=${currentMode?.javaClass?.simpleName ?: "none"} interactive=${power?.isInteractive}")
        stopMode()
    }

    override fun onDestroy() {
        DiagnosticRecorder.record("service", "destroy")
        connected = false
        stopMode()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Detect the known Samsung Air Actions warning without retaining any UI text.
        // A third-party service cannot prevent the Samsung system warning from displaying.
        if (preferences.get(PrefKeys.MOUSE_DIAGNOSTICS_ENABLED) &&
            event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val pkgName = event.packageName?.toString().orEmpty()
            if (pkgName == "com.samsung.android.service.aircommand" ||
                pkgName == "com.android.systemui") {
                val unsupported = event.text.any { message ->
                    val lower = message?.toString()?.lowercase(java.util.Locale.ROOT).orEmpty()
                    lower.contains("nie obsługuje tej akcji") ||
                        lower.contains("does not support this action") ||
                        lower.contains("doesn't support this action")
                }
                if (unsupported) DiagnosticRecorder.record("system-air-actions", "unsupportedActionWarning detected package=$pkgName")
            }
        }
        if (!(preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY) || preferences.get(PrefKeys.MOUSE_DIAGNOSTICS_ENABLED))) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != "com.android.settings" && pkg != "com.samsung.android.service.aircommand") return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBatteryCaptureMs < 800L) return
        lastBatteryCaptureMs = now
        DiagnosticRecorder.record("battery", "eventOnPenSettings package=$pkg type=${event.eventType}")
        val level = SamsungPenBatteryCapture.extract(rootInActiveWindow, allowHeadingOnly = pkg == "com.samsung.android.service.aircommand") ?: return
        DiagnosticRecorder.record("battery", "capturedLevel=$level source=visibleSamsungSpenSettings")
        if (level != preferences.get(pl.jojczak.penmouses.core.common.utils.PrefKeys.S_PEN_OBSERVED_LEVEL)
            || System.currentTimeMillis() - preferences.get(pl.jojczak.penmouses.core.common.utils.PrefKeys.S_PEN_OBSERVED_AT) > 60_000L) {
            preferences.put(pl.jojczak.penmouses.core.common.utils.PrefKeys.S_PEN_OBSERVED_LEVEL, level)
            preferences.put(pl.jojczak.penmouses.core.common.utils.PrefKeys.S_PEN_OBSERVED_AT, System.currentTimeMillis())
            (currentMode as? MouseMode)?.refreshBatteryStatus()
        }
    }

    companion object {
        @Volatile var connected: Boolean = false
            private set
        private const val TAG = "MouseService"
        private const val DELAY_BETWEEN_MODES = 1000L
    }
}
