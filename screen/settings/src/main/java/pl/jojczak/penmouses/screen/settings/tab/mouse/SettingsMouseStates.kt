package pl.jojczak.penmouses.screen.settings.tab.mouse

import pl.jojczak.penmouses.core.common.utils.CursorType
import pl.jojczak.penmouses.screen.settings.mvi.SettingsCursorViewAction
import pl.jojczak.penmouses.screen.settings.mvi.SettingsTabCursorState

internal data class SettingsMouseState(
    override val cursorType: CursorType = CursorType.Light,
    val sPenSensitivity: Float = 0.0f,
    val mouseAcceleration: Float = 1.5f,
    val axisRotation: Float = 0f,
    val diagnosticsEnabled: Boolean = false,
    val cursorHideDelay: Float = 0f,
    val sPenSleepEnabled: Boolean = false,
    val cursorSize: Float = 0f,
    val cursorAlpha: Float = 0f,
    val realtimeDragEnabled: Boolean = false,
    val dragSegmentMs: Float = 16f,
    val batteryOverlayEnabled: Boolean = false,
    val batteryOverlayUnlocked: Boolean = false,
    val batteryOverlayAlpha: Float = 0.9f,
    val batteryOverlayX: Float = 0.8f,
    val batteryOverlayY: Float = 0.08f,
    val batteryOverlayColor: Float = 0f,
) : SettingsTabCursorState()

internal sealed class SettingsMouseViewAction : SettingsCursorViewAction() {
    data class SPenSleepEnabled(val enabled: Boolean) : SettingsMouseViewAction()
    data class DiagnosticsEnabled(val enabled: Boolean) : SettingsMouseViewAction()
    data object RecalibrateMotion : SettingsMouseViewAction()
    data object CalibrateRightwardMotion : SettingsMouseViewAction()
    data class BatteryOverlayEnabled(val enabled: Boolean) : SettingsMouseViewAction()
    data class BatteryOverlayUnlocked(val unlocked: Boolean) : SettingsMouseViewAction()
    data class RealtimeDragEnabled(val enabled: Boolean) : SettingsMouseViewAction()
}
