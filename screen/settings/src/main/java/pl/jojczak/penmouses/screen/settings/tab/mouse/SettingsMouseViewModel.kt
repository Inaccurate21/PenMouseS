package pl.jojczak.penmouses.screen.settings.tab.mouse

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.ModeStatus
import pl.jojczak.penmouses.core.common.utils.CursorType
import pl.jojczak.penmouses.core.common.utils.PrefKeys
import pl.jojczak.penmouses.core.common.utils.PreferencesManager
import pl.jojczak.penmouses.screen.settings.mvi.SettingsCursorViewAction
import pl.jojczak.penmouses.screen.settings.mvi.SettingsTabCursorViewModel
import pl.jojczak.penmouses.screen.settings.mvi.SettingsViewAction
import javax.inject.Inject

@HiltViewModel
internal class SettingsMouseViewModel @Inject constructor(
    preferencesManager: PreferencesManager,
    @ApplicationContext context: Context,
): SettingsTabCursorViewModel<SettingsMouseState>(
    preferencesManager = preferencesManager,
    startState = SettingsMouseState(),
    modeStatus = ModeStatus.Mouse,
    context = context
) {
    private val sharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val positionListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PrefKeys.MOUSE_BATTERY_OVERLAY_X.name || key == PrefKeys.MOUSE_BATTERY_OVERLAY_Y.name) {
            updateState { it.copy(
                batteryOverlayX = preferencesManager.get(PrefKeys.MOUSE_BATTERY_OVERLAY_X),
                batteryOverlayY = preferencesManager.get(PrefKeys.MOUSE_BATTERY_OVERLAY_Y),
            ) }
        }
    }

    init { sharedPreferences.registerOnSharedPreferenceChangeListener(positionListener) }

    override fun onCleared() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(positionListener)
        super.onCleared()
    }

    override fun refreshSettingsData() = updateState {
        SettingsMouseState(
            cursorType = preferencesManager.get(PrefKeys.MOUSE_CURSOR_TYPE),
            sPenSensitivity = preferencesManager.get(PrefKeys.MOUSE_SENSITIVITY),
            mouseAcceleration = preferencesManager.get(PrefKeys.MOUSE_ACCELERATION),
            axisRotation = preferencesManager.get(PrefKeys.MOUSE_AXIS_ROTATION),
            cursorHideDelay = preferencesManager.get(PrefKeys.MOUSE_CURSOR_HIDE_DELAY),
            sPenSleepEnabled = preferencesManager.get(PrefKeys.MOUSE_SLEEP_ENABLED),
            cursorSize = preferencesManager.get(PrefKeys.MOUSE_CURSOR_SIZE),
            cursorAlpha = preferencesManager.get(PrefKeys.MOUSE_CURSOR_ALPHA),
            realtimeDragEnabled = preferencesManager.get(PrefKeys.MOUSE_REALTIME_DRAG),
            dragSegmentMs = preferencesManager.get(PrefKeys.MOUSE_DRAG_SEGMENT_MS),
            batteryOverlayEnabled = preferencesManager.get(PrefKeys.MOUSE_BATTERY_OVERLAY),
            batteryOverlayUnlocked = preferencesManager.get(PrefKeys.MOUSE_BATTERY_OVERLAY_UNLOCKED),
            batteryOverlayAlpha = preferencesManager.get(PrefKeys.MOUSE_BATTERY_OVERLAY_ALPHA),
            batteryOverlayX = preferencesManager.get(PrefKeys.MOUSE_BATTERY_OVERLAY_X),
            batteryOverlayY = preferencesManager.get(PrefKeys.MOUSE_BATTERY_OVERLAY_Y),
            batteryOverlayColor = preferencesManager.get(PrefKeys.MOUSE_BATTERY_OVERLAY_COLOR),
        )
    }

    override fun onViewAction(action: SettingsViewAction) = when (action) {
        is SettingsViewAction.RefreshData -> refreshSettingsData()
        is SettingsViewAction.SavePreference<*> -> savePreference(action)
        is SettingsViewAction.UpdatePreference<*> -> updatePreference(action)
        is SettingsCursorViewAction.CursorTypeChanged -> onCursorTypeChange(action.cursorType)
        is SettingsCursorViewAction.CustomCursorFileSelected -> loadCustomCursorImage(action.uri)
        is SettingsMouseViewAction.SPenSleepEnabled -> onSPenSleepEnabledChange(action.enabled)
        is SettingsMouseViewAction.RecalibrateMotion -> {
            AppToServiceEvent.event.tryEmit(AppToServiceEvent.Event.RecalibrateMotion)
        }
        is SettingsMouseViewAction.BatteryOverlayEnabled -> {
            updateState { it.copy(batteryOverlayEnabled = action.enabled) }
            preferencesManager.put(PrefKeys.MOUSE_BATTERY_OVERLAY, action.enabled)
            tryToPingService()
        }
        is SettingsMouseViewAction.BatteryOverlayUnlocked -> {
            updateState { it.copy(batteryOverlayUnlocked = action.unlocked) }
            preferencesManager.put(PrefKeys.MOUSE_BATTERY_OVERLAY_UNLOCKED, action.unlocked)
            tryToPingService()
        }
        is SettingsMouseViewAction.RealtimeDragEnabled -> {
            updateState { it.copy(realtimeDragEnabled = action.enabled) }
            preferencesManager.put(PrefKeys.MOUSE_REALTIME_DRAG, action.enabled)
            tryToPingService()
        }
        else -> {}
    }

    override fun updatePreference(action: SettingsViewAction.UpdatePreference<*>) = when(action.key) {
        PrefKeys.MOUSE_AXIS_ROTATION -> updateState { it.copy(axisRotation = action.value as Float) }
        PrefKeys.MOUSE_ACCELERATION -> updateState { it.copy(mouseAcceleration = action.value as Float) }

        PrefKeys.MOUSE_SENSITIVITY -> {
            updateState { it.copy(sPenSensitivity = action.value as Float) }
        }

        PrefKeys.MOUSE_CURSOR_HIDE_DELAY -> {
            updateState { it.copy(cursorHideDelay = action.value as Float) }
        }

        PrefKeys.MOUSE_CURSOR_SIZE -> {
            updateState { it.copy(cursorSize = action.value as Float) }
        }

        PrefKeys.MOUSE_CURSOR_ALPHA -> {
            updateState { it.copy(cursorAlpha = action.value as Float) }
        }

        PrefKeys.MOUSE_DRAG_SEGMENT_MS -> updateState { it.copy(dragSegmentMs = action.value as Float) }
        PrefKeys.MOUSE_BATTERY_OVERLAY_ALPHA -> updateState { it.copy(batteryOverlayAlpha = action.value as Float) }
        PrefKeys.MOUSE_BATTERY_OVERLAY_X -> updateState { it.copy(batteryOverlayX = action.value as Float) }
        PrefKeys.MOUSE_BATTERY_OVERLAY_Y -> updateState { it.copy(batteryOverlayY = action.value as Float) }
        PrefKeys.MOUSE_BATTERY_OVERLAY_COLOR -> updateState { it.copy(batteryOverlayColor = action.value as Float) }

        else -> {}
    }

    override fun onCursorTypeChange(cursorType: CursorType) {
        updateState { it.copy(cursorType = cursorType) }
        super.onCursorTypeChange(cursorType)
    }

    private fun onSPenSleepEnabledChange(enabled: Boolean) {
        updateState { it.copy(sPenSleepEnabled = enabled) }
        preferencesManager.put(PrefKeys.MOUSE_SLEEP_ENABLED, enabled)
        tryToPingService()
    }
}
