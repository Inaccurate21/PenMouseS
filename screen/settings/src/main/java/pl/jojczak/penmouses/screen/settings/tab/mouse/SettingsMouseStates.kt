package pl.jojczak.penmouses.screen.settings.tab.mouse

import pl.jojczak.penmouses.core.common.utils.CursorType
import pl.jojczak.penmouses.screen.settings.mvi.SettingsCursorViewAction
import pl.jojczak.penmouses.screen.settings.mvi.SettingsTabCursorState

internal data class SettingsMouseState(
    override val cursorType: CursorType = CursorType.Light,
    val sPenSensitivity: Float = 0.0f,
    val cursorHideDelay: Float = 0f,
    val sPenSleepEnabled: Boolean = false,
    val cursorSize: Float = 0f,
    val cursorAlpha: Float = 0f,
    val realtimeDragEnabled: Boolean = false,
    val dragSegmentMs: Float = 16f,
) : SettingsTabCursorState()

internal sealed class SettingsMouseViewAction : SettingsCursorViewAction() {
    data class SPenSleepEnabled(val enabled: Boolean) : SettingsMouseViewAction()
    data class RealtimeDragEnabled(val enabled: Boolean) : SettingsMouseViewAction()
}
