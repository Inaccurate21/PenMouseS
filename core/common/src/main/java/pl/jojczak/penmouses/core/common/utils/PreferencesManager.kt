@file:Suppress("SpellCheckingInspection")

package pl.jojczak.penmouses.core.common.utils

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.edit
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.ModeStatus
import pl.jojczak.penmouses.core.ui.R

class PreferencesManager(
    context: Context
) {
    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun <T> put(key: PrefKey<T>, value: T) {
        Log.d(TAG, "Writing pref ${key.name} with value $value")
        prefs.edit {
            when (value) {
                is Boolean -> {
                    putBoolean(key.name, value)
                }

                is Float -> {
                    putFloat(key.name, value)
                }

                is Int -> {
                    putInt(key.name, value)
                }
                is Long -> {
                    putLong(key.name, value)
                }

                is CursorType -> {
                    putString(key.name, value.name)
                }
            }
            this.apply()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: PrefKey<T>): T {
        Log.d(TAG, "Reading pref ${key.name}")
        return when (key.default) {
            is Boolean -> {
                prefs.getBoolean(key.name, key.default) as T
            }

            is Float -> {
                prefs.getFloat(key.name, key.default) as T
            }

            is Int -> {
                prefs.getInt(key.name, key.default) as T
            }
            is Long -> {
                prefs.getLong(key.name, key.default) as T
            }

            is CursorType -> {
                CursorType.fromValue(
                    prefs.getString(key.name, key.default.name) ?: key.default.name
                ) as T
            }

            else -> {
                throw IllegalArgumentException("Unsupported type")
            }
        }
    }

    fun reset() {
        prefs.edit {
            put(PrefKeys.MOUSE_SENSITIVITY, PrefKeys.MOUSE_SENSITIVITY.default)
            put(PrefKeys.MOUSE_ACCELERATION, PrefKeys.MOUSE_ACCELERATION.default)
            put(PrefKeys.MOUSE_HOLD_ACTION, PrefKeys.MOUSE_HOLD_ACTION.default)
            put(PrefKeys.MOUSE_DOUBLE_PRESS_ACTION, PrefKeys.MOUSE_DOUBLE_PRESS_ACTION.default)
            put(PrefKeys.MOUSE_TRIPLE_PRESS_ACTION, PrefKeys.MOUSE_TRIPLE_PRESS_ACTION.default)
            put(PrefKeys.MOUSE_HOLD_THRESHOLD_MS, PrefKeys.MOUSE_HOLD_THRESHOLD_MS.default)
            put(PrefKeys.MOUSE_AXIS_ROTATION, PrefKeys.MOUSE_AXIS_ROTATION.default)
            put(PrefKeys.MOUSE_DIAGNOSTICS_ENABLED, PrefKeys.MOUSE_DIAGNOSTICS_ENABLED.default)
            put(PrefKeys.MOUSE_GESTURE_TRACE_ENABLED, PrefKeys.MOUSE_GESTURE_TRACE_ENABLED.default)
            put(PrefKeys.S_PEN_OBSERVED_LEVEL, PrefKeys.S_PEN_OBSERVED_LEVEL.default)
            put(PrefKeys.S_PEN_OBSERVED_AT, PrefKeys.S_PEN_OBSERVED_AT.default)
            put(PrefKeys.MOUSE_BATTERY_OVERLAY, PrefKeys.MOUSE_BATTERY_OVERLAY.default)
            put(PrefKeys.MOUSE_BATTERY_OVERLAY_UNLOCKED, PrefKeys.MOUSE_BATTERY_OVERLAY_UNLOCKED.default)
            put(PrefKeys.MOUSE_BATTERY_OVERLAY_ALPHA, PrefKeys.MOUSE_BATTERY_OVERLAY_ALPHA.default)
            put(PrefKeys.MOUSE_BATTERY_OVERLAY_SHOW_TIME, PrefKeys.MOUSE_BATTERY_OVERLAY_SHOW_TIME.default)
            put(PrefKeys.MOUSE_BATTERY_OVERLAY_COMPACT, PrefKeys.MOUSE_BATTERY_OVERLAY_COMPACT.default)
            put(PrefKeys.MOUSE_BATTERY_OVERLAY_SCALE, PrefKeys.MOUSE_BATTERY_OVERLAY_SCALE.default)
            put(PrefKeys.MOUSE_BATTERY_OVERLAY_X, PrefKeys.MOUSE_BATTERY_OVERLAY_X.default)
            put(PrefKeys.MOUSE_BATTERY_OVERLAY_Y, PrefKeys.MOUSE_BATTERY_OVERLAY_Y.default)
            put(PrefKeys.MOUSE_BATTERY_OVERLAY_COLOR, PrefKeys.MOUSE_BATTERY_OVERLAY_COLOR.default)
            put(PrefKeys.MOUSE_CURSOR_SIZE, PrefKeys.MOUSE_CURSOR_SIZE.default)
            put(PrefKeys.MOUSE_CURSOR_TYPE, PrefKeys.MOUSE_CURSOR_TYPE.default)
            put(PrefKeys.MOUSE_CURSOR_HIDE_DELAY, PrefKeys.MOUSE_CURSOR_HIDE_DELAY.default)
            put(PrefKeys.MOUSE_SLEEP_ENABLED, PrefKeys.MOUSE_SLEEP_ENABLED.default)
            put(PrefKeys.MOUSE_REALTIME_DRAG, PrefKeys.MOUSE_REALTIME_DRAG.default)
            put(PrefKeys.MOUSE_DRAG_SEGMENT_MS, PrefKeys.MOUSE_DRAG_SEGMENT_MS.default)
            put(PrefKeys.MOUSE_ADAPTIVE_DRAG, PrefKeys.MOUSE_ADAPTIVE_DRAG.default)
            put(PrefKeys.MOUSE_CAPTURE_LATE_MOTION, PrefKeys.MOUSE_CAPTURE_LATE_MOTION.default)
            put(PrefKeys.MOUSE_CURSOR_ALPHA, PrefKeys.MOUSE_CURSOR_ALPHA.default)
            put(PrefKeys.POINT_CURSOR_TYPE, PrefKeys.POINT_CURSOR_TYPE.default)
            put(PrefKeys.POINT_CURSOR_ALPHA, PrefKeys.POINT_CURSOR_ALPHA.default)
            put(PrefKeys.POINT_CURSOR_SIZE, PrefKeys.POINT_CURSOR_SIZE.default)
            put(PrefKeys.SCROLL_EXPERIMENTAL_MODE, PrefKeys.SCROLL_EXPERIMENTAL_MODE.default)
            this.apply()
        }
        Log.d(TAG, "Preferences reset")
    }

    companion object {
        private const val TAG = "PreferencesManager"
    }
}

object PrefKeys {
    val MOUSE_SENSITIVITY = PrefKey("spen_sensitivity", 50f, 1f, 1f..400f)
    val MOUSE_BATTERY_OVERLAY = PrefKey("mouse_battery_overlay", false)
    val MOUSE_BATTERY_OVERLAY_UNLOCKED = PrefKey("mouse_battery_overlay_unlocked", false)
    val MOUSE_BATTERY_OVERLAY_SHOW_TIME = PrefKey("mouse_battery_overlay_show_time", true)
    val MOUSE_BATTERY_OVERLAY_COMPACT = PrefKey("mouse_battery_overlay_compact", false)
    val MOUSE_BATTERY_OVERLAY_SCALE = PrefKey("mouse_battery_overlay_scale", 1f, 0.1f, 0.6f..2f)
    val MOUSE_BATTERY_OVERLAY_ALPHA = PrefKey("mouse_battery_overlay_alpha", 0.9f, 0.05f, 0.2f..1f)
    val MOUSE_BATTERY_OVERLAY_X = PrefKey("mouse_battery_overlay_x", 0.8f, 0.01f, 0f..1f)
    val MOUSE_BATTERY_OVERLAY_Y = PrefKey("mouse_battery_overlay_y", 0.08f, 0.01f, 0f..1f)
    val MOUSE_BATTERY_OVERLAY_COLOR = PrefKey("mouse_battery_overlay_color", 0f, 1f, 0f..3f)
    val MOUSE_DIAGNOSTICS_ENABLED = PrefKey("mouse_diagnostics_enabled", false)
    val MOUSE_GESTURE_TRACE_ENABLED = PrefKey("mouse_gesture_trace_enabled", false)
    val MOUSE_AXIS_ROTATION = PrefKey("mouse_axis_rotation", 0f, 90f, 0f..270f)
    val S_PEN_OBSERVED_LEVEL = PrefKey("spen_observed_battery_percent", -1)
    val S_PEN_OBSERVED_AT = PrefKey("spen_observed_battery_timestamp_ms", 0L)
    val MOUSE_HOLD_ACTION = PrefKey("mouse_hold_action", 0f, 1f, 0f..4f)
    val MOUSE_DOUBLE_PRESS_ACTION = PrefKey("mouse_double_press_action", 0f, 1f, 0f..5f)
    val MOUSE_TRIPLE_PRESS_ACTION = PrefKey("mouse_triple_press_action", 0f, 1f, 0f..5f)
    val MOUSE_TEXT_SELECTION_SENSITIVITY_SCALE = 0.30f
    val MOUSE_HOLD_THRESHOLD_MS = PrefKey("mouse_hold_threshold_ms", 450f, 50f, 250f..900f)
    val MOUSE_ACCELERATION = PrefKey("mouse_acceleration", 1.5f, 0.5f, 0f..3f)
    val MOUSE_CURSOR_SIZE = PrefKey("cursor_size", 60f, 1f, 20f..250f)
    val MOUSE_CURSOR_TYPE = PrefKey("cursor_type", CursorType.Light)
    val MOUSE_CURSOR_ALPHA = PrefKey("cursor_alpha", 1f, step = 0.05f, range = 0.05f..1f)
    val MOUSE_CURSOR_HIDE_DELAY = PrefKey("time_to_hide_cursor", 10f, 5f, 5f..305f)
    val MOUSE_SLEEP_ENABLED = PrefKey("spen_sleep_enabled", true)
    val MOUSE_REALTIME_DRAG = PrefKey("mouse_realtime_drag", false)
    val MOUSE_ADAPTIVE_DRAG = PrefKey("mouse_adaptive_drag", false)
    val MOUSE_CAPTURE_LATE_MOTION = PrefKey("mouse_capture_late_motion", false)
    val MOUSE_DRAG_SEGMENT_MS = PrefKey("mouse_drag_segment_ms", 8f, 1f, 1f..32f)
    val POINT_CURSOR_TYPE = PrefKey("point_cursor_type", CursorType.Light)
    val POINT_CURSOR_ALPHA = PrefKey("point_cursor_alpha", 1f, step = 0.05f, range = 0.05f..1f)
    val POINT_CURSOR_SIZE = PrefKey("point_cursor_size", 60f, 1f, 20f..250f)
    val SCROLL_EXPERIMENTAL_MODE = PrefKey("scroll_experimental_mode", false)
    val FIRST_RUN = PrefKey("first_run", true)
    val LAUNCH_COUNT = PrefKey("launch_count", 0)
    val ANALYTICS_CONSENT_SHOWED = PrefKey("analytics_consent_showed", false)
    val ANALYTICS_ENABLED = PrefKey("analytics_enabled", false)
}

data class PrefKey<T>(
    val name: String,
    val default: T,
    val step: Float = 1f,
    val range: ClosedFloatingPointRange<Float> = 0f..1f
)

enum class CursorType(
    private val fileName: String,
    @param:StringRes val label: Int
) {
    Light("light.png", R.string.cursor_type_light),
    Dark("dark.png", R.string.cursor_type_dark),
    Retro("retro.png", R.string.cursor_type_retro),
    SPen("spen.png", R.string.cursor_type_spen),
    Custom("custom.png", R.string.cursor_type_custom);

    fun getFileName(mode: ModeStatus? = null) = when (mode) {
        ModeStatus.Mouse -> "mouse_" + this.fileName
        ModeStatus.Point -> "point_" + this.fileName
        else -> this.fileName
    }

    companion object {
        fun fromValue(value: String) = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: Light
    }
}
