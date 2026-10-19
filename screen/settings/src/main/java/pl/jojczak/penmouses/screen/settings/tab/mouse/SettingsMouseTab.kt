package pl.jojczak.penmouses.screen.settings.tab.mouse

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.ModeStatus
import pl.jojczak.penmouses.core.common.utils.CursorType
import pl.jojczak.penmouses.core.common.utils.PrefKey
import pl.jojczak.penmouses.core.common.utils.PrefKeys
import pl.jojczak.penmouses.core.common.utils.DiagnosticRecorder
import pl.jojczak.penmouses.core.ui.theme.PenMouseSDevicePreview
import pl.jojczak.penmouses.core.ui.theme.pad_m
import pl.jojczak.penmouses.core.ui.theme.radius_m
import pl.jojczak.penmouses.screen.settings.R
import pl.jojczak.penmouses.screen.settings.components.cursorIconComponent
import pl.jojczak.penmouses.screen.settings.components.horizontalDivider
import pl.jojczak.penmouses.screen.settings.components.settingsSlider
import pl.jojczak.penmouses.screen.settings.mvi.SettingsCursorViewAction
import pl.jojczak.penmouses.screen.settings.mvi.SettingsViewAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsMouseTab(
    contentPadding: PaddingValues,
    refreshDataTrigger: Boolean,
    viewModel: SettingsMouseViewModel = hiltViewModel()
) {
    val viewState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val success = runCatching {
                val stream = context.contentResolver.openOutputStream(uri) ?: error("Cannot open output")
                stream.bufferedWriter().use { it.write(DiagnosticRecorder.snapshot(context)) }
            }.isSuccess
            Toast.makeText(context, if (success) R.string.settings_diagnostics_saved
                else R.string.settings_diagnostics_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(refreshDataTrigger) {
        if (!refreshDataTrigger) viewModel.onViewAction(SettingsViewAction.RefreshData)
    }

    // @formatter:off
    SettingsMouseTabContent(
        viewState = viewState,
        contentPadding = contentPadding,
        onPrefChanging = { key, value -> viewModel.onViewAction(SettingsViewAction.UpdatePreference(key, value)) },
        onPrefChanged = { key, value -> viewModel.onViewAction(SettingsViewAction.SavePreference(key, value)) },
        onCursorTypeChange = { viewModel.onViewAction(SettingsCursorViewAction.CursorTypeChanged(it)) },
        onCustomCursorFileSelected = { viewModel.onViewAction(SettingsCursorViewAction.CustomCursorFileSelected(it))},
        onSPenSleepEnabledChange = { viewModel.onViewAction(SettingsMouseViewAction.SPenSleepEnabled(it)) },
        onRealtimeDragChange = { viewModel.onViewAction(SettingsMouseViewAction.RealtimeDragEnabled(it)) },
        onAdaptiveDragChange = { viewModel.onViewAction(SettingsMouseViewAction.AdaptiveDragEnabled(it)) },
        onLateMotionChange = { viewModel.onViewAction(SettingsMouseViewAction.CaptureLateMotionEnabled(it)) },
        onRecalibrateMotion = { viewModel.onViewAction(SettingsMouseViewAction.RecalibrateMotion) },
        onCalibrateRightward = { viewModel.onViewAction(SettingsMouseViewAction.CalibrateRightwardMotion) },
        onDiagnosticsEnabled = { viewModel.onViewAction(SettingsMouseViewAction.DiagnosticsEnabled(it)) },
        onGestureTraceEnabled = { viewModel.onViewAction(SettingsMouseViewAction.GestureTraceEnabled(it)) },
        onExportDiagnostics = { exportLauncher.launch("PenMouseS-diagnostyka-" + System.currentTimeMillis() + ".txt") },
        onClearDiagnostics = { DiagnosticRecorder.clear() },
        onMarkIssue = { DiagnosticRecorder.record("USER_MARK", "user marked a cursor or drag problem") },
        onBatteryOverlayEnabled = { viewModel.onViewAction(SettingsMouseViewAction.BatteryOverlayEnabled(it)) },
        onBatteryOverlayUnlocked = { viewModel.onViewAction(SettingsMouseViewAction.BatteryOverlayUnlocked(it)) },
        onOverlayShowTime = { viewModel.onViewAction(SettingsMouseViewAction.BatteryOverlayShowTime(it)) },
        onOverlayCompact = { viewModel.onViewAction(SettingsMouseViewAction.BatteryOverlayCompact(it)) }
    )
    // @formatter:on
}

@Composable
private fun SettingsMouseTabContent(
    viewState: SettingsMouseState,
    contentPadding: PaddingValues,
    onPrefChanging: (PrefKey<Float>, Float) -> Unit = { _, _ -> },
    onPrefChanged: (PrefKey<Float>, Float) -> Unit = { _, _ -> },
    onCursorTypeChange: (CursorType) -> Unit = {},
    onCustomCursorFileSelected: (Uri) -> Unit = {},
    onSPenSleepEnabledChange: (Boolean) -> Unit = {},
    onRealtimeDragChange: (Boolean) -> Unit = {},
    onAdaptiveDragChange: (Boolean) -> Unit = {},
    onLateMotionChange: (Boolean) -> Unit = {},
    onRecalibrateMotion: () -> Unit = {},
    onCalibrateRightward: () -> Unit = {},
    onDiagnosticsEnabled: (Boolean) -> Unit = {},
    onGestureTraceEnabled: (Boolean) -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    onClearDiagnostics: () -> Unit = {},
    onMarkIssue: () -> Unit = {},
    onBatteryOverlayEnabled: (Boolean) -> Unit = {},
    onBatteryOverlayUnlocked: (Boolean) -> Unit = {},
    onOverlayShowTime: (Boolean) -> Unit = {},
    onOverlayCompact: (Boolean) -> Unit = {}
) {
    val sectionPreferences = LocalContext.current.getSharedPreferences("mouse_settings_sections", Context.MODE_PRIVATE)
    var gesturesOpen by remember { mutableStateOf(sectionPreferences.getBoolean("gestures", true)) }
    var axisOpen by remember { mutableStateOf(sectionPreferences.getBoolean("axes", false)) }
    var overlayOpen by remember { mutableStateOf(sectionPreferences.getBoolean("overlay", true)) }
    var diagnosticsOpen by remember { mutableStateOf(sectionPreferences.getBoolean("diagnostics", false)) }
    var cursorOpen by remember { mutableStateOf(sectionPreferences.getBoolean("cursor", true)) }

    LazyColumn(
    contentPadding = contentPadding,
    modifier = Modifier.fillMaxSize()
) {
    settingsSectionHeader(R.string.settings_section_gestures, gesturesOpen) {
        gesturesOpen = !gesturesOpen
        DiagnosticRecorder.record("settings", "gesturesExpanded=$gesturesOpen")
        sectionPreferences.edit().putBoolean("gestures", gesturesOpen).apply()
    }
    if (gesturesOpen) {
    settingsSlider(
        text = R.string.settings_s_pen_sensitivity_slider_label,
        value = viewState.sPenSensitivity,
        prefKey = PrefKeys.MOUSE_SENSITIVITY,
        onPrefChanging = onPrefChanging,
        onPrefChanged = onPrefChanged
    )

    horizontalDivider()

    settingsSlider(
        text = R.string.settings_mouse_acceleration,
        value = viewState.mouseAcceleration,
        valueDisplay = { "%.1f".format(it) },
        prefKey = PrefKeys.MOUSE_ACCELERATION,
        onPrefChanging = onPrefChanging,
        onPrefChanged = onPrefChanged
    )

    horizontalDivider()


    shortcutDropdown(
        label = R.string.settings_button_hold_action,
        selected = viewState.holdAction.toInt(),
        choices = listOf(
            0 to R.string.settings_button_action_drag,
            1 to R.string.settings_button_action_context,
            2 to R.string.settings_button_action_doubletap,
            3 to R.string.settings_button_action_back,
            4 to R.string.settings_button_action_home,
        ),
        onSelected = { action ->
            onPrefChanging(PrefKeys.MOUSE_HOLD_ACTION, action.toFloat())
            onPrefChanged(PrefKeys.MOUSE_HOLD_ACTION, action.toFloat())
        }
    )
    shortcutDropdown(
        label = R.string.settings_button_double_action,
        selected = viewState.doublePressAction.toInt(),
        choices = listOf(
            0 to R.string.settings_button_action_standard_double,
            1 to R.string.settings_button_action_context,
            2 to R.string.settings_button_action_doubletap,
            3 to R.string.settings_button_action_back,
            4 to R.string.settings_button_action_home,
        ),
        onSelected = { action ->
            onPrefChanging(PrefKeys.MOUSE_DOUBLE_PRESS_ACTION, action.toFloat())
            onPrefChanged(PrefKeys.MOUSE_DOUBLE_PRESS_ACTION, action.toFloat())
        }
    )
    if (viewState.holdAction >= 1f) {
        settingsSlider(
            text = R.string.settings_button_hold_threshold,
            value = viewState.holdThresholdMs,
            valueDisplay = { "${it.toInt()} ms" },
            prefKey = PrefKeys.MOUSE_HOLD_THRESHOLD_MS,
            onPrefChanging = onPrefChanging,
            onPrefChanged = onPrefChanged
        )
        item {
            Text(stringResource(R.string.settings_button_hold_explainer),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(pad_m))
        }
    }
    if (viewState.doublePressAction != 0f) {
        item {
            Text(stringResource(R.string.settings_button_double_explainer),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(pad_m))
        }
    }
    item {
        Row(modifier = Modifier.padding(pad_m).clip(RoundedCornerShape(radius_m))
            .clickable { onRealtimeDragChange(!viewState.realtimeDragEnabled) }) {
            Checkbox(checked = viewState.realtimeDragEnabled, onCheckedChange = onRealtimeDragChange)
            Column {
                Text(stringResource(R.string.settings_realtime_drag))
                Text(stringResource(R.string.settings_realtime_drag_info), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (viewState.realtimeDragEnabled) {
        item {
            Row(modifier = Modifier.padding(pad_m)
                .clickable { onLateMotionChange(!viewState.captureLateMotionEnabled) }) {
                Checkbox(
                    checked = viewState.captureLateMotionEnabled,
                    onCheckedChange = onLateMotionChange
                )
                Column {
                    Text(stringResource(R.string.settings_capture_late_motion))
                    Text(stringResource(R.string.settings_capture_late_motion_info),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(modifier = Modifier.padding(pad_m).clickable { onAdaptiveDragChange(!viewState.adaptiveDragEnabled) }) {
                Checkbox(checked = viewState.adaptiveDragEnabled, onCheckedChange = onAdaptiveDragChange)
                Column {
                    Text(stringResource(R.string.settings_adaptive_drag))
                    Text(stringResource(R.string.settings_adaptive_drag_info), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        settingsSlider(
            text = R.string.settings_drag_segment_ms,
            value = viewState.dragSegmentMs,
            prefKey = PrefKeys.MOUSE_DRAG_SEGMENT_MS,
            onPrefChanging = onPrefChanging,
            onPrefChanged = onPrefChanged,
        )
    }
    }
    settingsSectionHeader(R.string.settings_section_axis, axisOpen) {
        axisOpen = !axisOpen
        DiagnosticRecorder.record("settings", "axesExpanded=$axisOpen")
        sectionPreferences.edit().putBoolean("axes", axisOpen).apply()
    }
    if (axisOpen) {
    item {
        TextButton(onClick = onRecalibrateMotion) {
            Text(stringResource(R.string.settings_recalibrate_motion))
        }
    }
    item {
        TextButton(onClick = onCalibrateRightward) { Text(stringResource(R.string.settings_calibrate_rightward)) }
    }
    settingsSlider(
        text = R.string.settings_axis_rotation,
        value = viewState.axisRotation,
        valueDisplay = { "${it.toInt()}°" },
        prefKey = PrefKeys.MOUSE_AXIS_ROTATION,
        onPrefChanging = onPrefChanging,
        onPrefChanged = onPrefChanged
    )
    }
    settingsSectionHeader(R.string.settings_section_overlay, overlayOpen) {
        overlayOpen = !overlayOpen
        DiagnosticRecorder.record("settings", "overlayExpanded=$overlayOpen")
        sectionPreferences.edit().putBoolean("overlay", overlayOpen).apply()
    }
    if (overlayOpen) {
    item {
        Column(modifier = Modifier.padding(pad_m)) {
            Text(stringResource(R.string.settings_spen_battery_overlay_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_spen_battery_overlay_hint), style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.clickable { onBatteryOverlayEnabled(!viewState.batteryOverlayEnabled) }) {
                Checkbox(checked = viewState.batteryOverlayEnabled, onCheckedChange = onBatteryOverlayEnabled)
                Text(stringResource(R.string.settings_spen_battery_overlay_enable), modifier = Modifier.padding(pad_m))
            }
            if (viewState.batteryOverlayEnabled) {
                Row(modifier = Modifier.clickable { onOverlayCompact(!viewState.batteryOverlayCompact) }) {
                    Checkbox(checked = viewState.batteryOverlayCompact, onCheckedChange = onOverlayCompact)
                    Text(stringResource(R.string.settings_overlay_compact), modifier = Modifier.padding(pad_m))
                }
                if (!viewState.batteryOverlayCompact) {
                    Row(modifier = Modifier.clickable { onOverlayShowTime(!viewState.batteryOverlayShowTime) }) {
                        Checkbox(checked = viewState.batteryOverlayShowTime, onCheckedChange = onOverlayShowTime)
                        Text(stringResource(R.string.settings_overlay_time), modifier = Modifier.padding(pad_m))
                    }
                }
                Row(modifier = Modifier.clickable { onBatteryOverlayUnlocked(!viewState.batteryOverlayUnlocked) }) {
                    Checkbox(checked = viewState.batteryOverlayUnlocked, onCheckedChange = onBatteryOverlayUnlocked)
                    Text(stringResource(R.string.settings_spen_battery_overlay_unlock), modifier = Modifier.padding(pad_m))
                }
            }
        }
    }
    if (viewState.batteryOverlayEnabled) {
        settingsSlider(
            text = R.string.settings_overlay_size,
            value = viewState.batteryOverlayScale,
            valueDisplay = { "${(it * 100).toInt()}%" },
            prefKey = PrefKeys.MOUSE_BATTERY_OVERLAY_SCALE,
            onPrefChanging = onPrefChanging,
            onPrefChanged = onPrefChanged
        )
        settingsSlider(
            text = R.string.settings_spen_battery_overlay_alpha,
            value = viewState.batteryOverlayAlpha,
            valueDisplay = { "${(it * 100).toInt()}%" },
            prefKey = PrefKeys.MOUSE_BATTERY_OVERLAY_ALPHA,
            onPrefChanging = onPrefChanging,
            onPrefChanged = onPrefChanged
        )
        settingsSlider(
            text = R.string.settings_spen_battery_overlay_x,
            value = viewState.batteryOverlayX,
            valueDisplay = { "${(it * 100).toInt()}%" },
            prefKey = PrefKeys.MOUSE_BATTERY_OVERLAY_X,
            onPrefChanging = onPrefChanging,
            onPrefChanged = onPrefChanged
        )
        settingsSlider(
            text = R.string.settings_spen_battery_overlay_y,
            value = viewState.batteryOverlayY,
            valueDisplay = { "${(it * 100).toInt()}%" },
            prefKey = PrefKeys.MOUSE_BATTERY_OVERLAY_Y,
            onPrefChanging = onPrefChanging,
            onPrefChanged = onPrefChanged
        )
        settingsSlider(
            text = R.string.settings_spen_battery_overlay_color,
            value = viewState.batteryOverlayColor,
            valueDisplay = { listOf("Czarny", "Biały", "Niebieski", "Zielony")[it.toInt().coerceIn(0, 3)] },
            prefKey = PrefKeys.MOUSE_BATTERY_OVERLAY_COLOR,
            onPrefChanging = onPrefChanging,
            onPrefChanged = onPrefChanged
        )
    }
    }
    settingsSectionHeader(R.string.settings_section_diagnostics, diagnosticsOpen) {
        diagnosticsOpen = !diagnosticsOpen
        DiagnosticRecorder.record("settings", "diagnosticsExpanded=$diagnosticsOpen")
        sectionPreferences.edit().putBoolean("diagnostics", diagnosticsOpen).apply()
    }
    if (diagnosticsOpen) {
    item {
        Column(modifier = Modifier.padding(pad_m)) {
            Text(stringResource(R.string.settings_diagnostics_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_diagnostics_info), style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.clickable { onDiagnosticsEnabled(!viewState.diagnosticsEnabled) }) {
                Checkbox(checked = viewState.diagnosticsEnabled, onCheckedChange = onDiagnosticsEnabled)
                Text(stringResource(R.string.settings_diagnostics_enable), modifier = Modifier.padding(pad_m))
            }
            Row(modifier = Modifier.clickable(enabled = viewState.diagnosticsEnabled) {
                onGestureTraceEnabled(!viewState.gestureTraceEnabled)
            }) {
                Checkbox(
                    checked = viewState.gestureTraceEnabled,
                    enabled = viewState.diagnosticsEnabled,
                    onCheckedChange = onGestureTraceEnabled
                )
                Column {
                    Text(stringResource(R.string.settings_diagnostics_trace))
                    Text(stringResource(R.string.settings_diagnostics_trace_info),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Row {
                TextButton(onClick = onExportDiagnostics) { Text(stringResource(R.string.settings_diagnostics_export)) }
                TextButton(onClick = onClearDiagnostics) { Text(stringResource(R.string.settings_diagnostics_clear)) }
                TextButton(onClick = onMarkIssue, enabled = viewState.diagnosticsEnabled) {
                    Text(stringResource(R.string.settings_diagnostics_mark))
                }
            }
        }
    }
    }
    settingsSectionHeader(R.string.settings_section_cursor, cursorOpen) {
        cursorOpen = !cursorOpen
        DiagnosticRecorder.record("settings", "cursorExpanded=$cursorOpen")
        sectionPreferences.edit().putBoolean("cursor", cursorOpen).apply()
    }
    if (cursorOpen) {
    cursorIconComponent(
        cursorType = viewState.cursorType,
        modeStatus = ModeStatus.Mouse,
        onCursorTypeChange = onCursorTypeChange,
        onCustomCursorFileSelected = onCustomCursorFileSelected
    )

    horizontalDivider()

    settingsSlider(
        text = R.string.settings_cursor_alpha,
        value = viewState.cursorAlpha,
        valueDisplay = { (it * 100).toInt().toString() },
        prefKey = PrefKeys.MOUSE_CURSOR_ALPHA,
        onPrefChanging = onPrefChanging,
        onPrefChanged = onPrefChanged
    )

    horizontalDivider()

    settingsSlider(
        text = R.string.settings_cursor_size_slider_label,
        value = viewState.cursorSize,
        prefKey = PrefKeys.MOUSE_CURSOR_SIZE,
        onPrefChanging = onPrefChanging,
        onPrefChanged = onPrefChanged
    )

    horizontalDivider()

    settingsSlider(
        text = R.string.settings_cursor_hide_delay,
        textOnLastValue = R.string.settings_cursor_hide_delay_indefinite,
        value = viewState.cursorHideDelay,
        prefKey = PrefKeys.MOUSE_CURSOR_HIDE_DELAY,
        onPrefChanging = onPrefChanging,
        onPrefChanged = onPrefChanged
    )

    horizontalDivider()

    sPenSleepCheckBox(
        sPenSleepEnabled = viewState.sPenSleepEnabled,
        onSPenSleepEnabledChange = onSPenSleepEnabledChange
    )
    }
    }
}

private fun LazyListScope.shortcutDropdown(
    @StringRes label: Int,
    selected: Int,
    choices: List<Pair<Int, Int>>,
    onSelected: (Int) -> Unit
) = item {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(pad_m)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                val current = choices.firstOrNull { it.first == selected } ?: choices.first()
                Text(stringResource(current.second))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { (action, title) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(title)) },
                        onClick = {
                            expanded = false
                            onSelected(action)
                        }
                    )
                }
            }
        }
    }
}

private fun LazyListScope.settingsSectionHeader(
    title: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) = item {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(pad_m)
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(if (expanded) "▼" else "▶", style = MaterialTheme.typography.titleMedium)
    }
}

private fun LazyListScope.sPenSleepCheckBox(
    sPenSleepEnabled: Boolean,
    onSPenSleepEnabledChange: (Boolean) -> Unit
) = item {
    Row(
        modifier = Modifier
            .padding(pad_m)
            .clip(RoundedCornerShape(radius_m))
            .clickable {
                onSPenSleepEnabledChange(!sPenSleepEnabled)
            }
    ) {
        Checkbox(
            checked = sPenSleepEnabled,
            onCheckedChange = onSPenSleepEnabledChange
        )
        Column {
            Text(
                text = stringResource(R.string.settings_s_pen_sleep_info)
            )
            Crossfade(sPenSleepEnabled) {
                if (it) {
                    Text(
                        text = stringResource(R.string.settings_s_pen_sleep_enabled_info),
                        fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_s_pen_sleep_disabled_warning),
                        fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SettingsMouseTabPreview() {
    PenMouseSDevicePreview {
        SettingsMouseTabContent(
            viewState = SettingsMouseState(),
            contentPadding = it
        )
    }
}
