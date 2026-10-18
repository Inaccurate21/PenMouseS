package pl.jojczak.penmouses.core.common.utils

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Opt-in, bounded in-memory telemetry. Never records screen content, touch targets,
 * notification contents, device identifiers, or application/window text.
 */
object DiagnosticRecorder {
    private const val MAX_EVENTS = 600
    private val entries = ArrayDeque<String>(MAX_EVENTS)
    @Volatile private var enabled = false
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized fun setEnabled(value: Boolean) {
        enabled = value
        if (value) record("diagnostics", "enabled")
    }

    fun isEnabled(): Boolean = enabled

    @Synchronized fun record(tag: String, message: String) {
        if (!enabled) return
        if (entries.size >= MAX_EVENTS) entries.removeFirst()
        entries.addLast("${format.format(Date())} [${tag.take(28)}] ${message.take(360).replace("\n", " ")}")
    }

    @Synchronized fun clear() { entries.clear() }

    @Synchronized fun snapshot(context: Context): String {
        val prefs = PreferencesManager(context)
        val currentSettings = listOf(
            "diagnostic_enabled" to prefs.get(PrefKeys.MOUSE_DIAGNOSTICS_ENABLED),
            "cursor_sensitivity" to prefs.get(PrefKeys.MOUSE_SENSITIVITY),
            "cursor_acceleration" to prefs.get(PrefKeys.MOUSE_ACCELERATION),
            "axis_rotation_degrees" to prefs.get(PrefKeys.MOUSE_AXIS_ROTATION),
            "drag_duration_ms" to prefs.get(PrefKeys.MOUSE_DRAG_SEGMENT_MS),
            "realtime_drag" to prefs.get(PrefKeys.MOUSE_REALTIME_DRAG),
            "overlay_enabled" to prefs.get(PrefKeys.MOUSE_BATTERY_OVERLAY),
            "overlay_x" to prefs.get(PrefKeys.MOUSE_BATTERY_OVERLAY_X),
            "overlay_y" to prefs.get(PrefKeys.MOUSE_BATTERY_OVERLAY_Y),
            "observed_spen_battery" to prefs.get(PrefKeys.S_PEN_OBSERVED_LEVEL),
            "observed_at_ms" to prefs.get(PrefKeys.S_PEN_OBSERVED_AT),
        )
        return buildString {
            appendLine("PenMouse S Live diagnostic report — opt-in")
            appendLine("Created: ${format.format(Date())}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            appendLine("App: ${context.packageName}")
            appendLine("Scope: motion sensor deltas, click timing, battery UI parser metadata; no screen text")
            appendLine("Settings:")
            currentSettings.forEach { (key,value) -> appendLine("  $key=$value") }
            appendLine("Events (${entries.size} newest, max $MAX_EVENTS; in-memory only):")
            entries.forEach(::appendLine)
        }
    }
}
