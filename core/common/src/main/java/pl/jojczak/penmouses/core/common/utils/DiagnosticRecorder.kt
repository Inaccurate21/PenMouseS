package pl.jojczak.penmouses.core.common.utils

import android.content.Context
import android.os.Build
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Opt-in event timeline. Persists diagnostic events in private app storage so that
 * exporting long after stopping mouse mode still includes the earlier session.
 * No screen text, touched content, package names of visited apps, IDs or passwords.
 *
 * Disk writes run on a dedicated daemon thread to avoid slowing down gesture input.
 */
object DiagnosticRecorder {
    private const val MAX_MEMORY_EVENTS = 3000
    private const val MAX_FILE_BYTES = 7L * 1024 * 1024
    private const val MAX_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    private const val CURRENT = "penmouse-diagnostics.log"
    private const val PREVIOUS = "penmouse-diagnostics.previous.log"
    private val events = ArrayDeque<String>(MAX_MEMORY_EVENTS)
    private val worker = Executors.newSingleThreadExecutor { action ->
        Thread(action, "penmouse-diagnostics-io").apply { isDaemon = true }
    }
    @Volatile private var context: Context? = null
    @Volatile private var enabled = false
    private var lastEventMs = 0L
    private val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized fun initialize(context: Context) {
        if (this.context != null) return
        this.context = context.applicationContext
        enabled = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getBoolean(PrefKeys.MOUSE_DIAGNOSTICS_ENABLED.name, false)
        if (enabled) record("process", "diagnostic process started appVersion=${appVersion(context)} (previous logs retained)")
    }

    @Synchronized fun setEnabled(value: Boolean) {
        if (value && !enabled) {
            enabled = true
            record("diagnostics", "enabled")
        } else if (!value && enabled) {
            record("diagnostics", "disabled (existing report retained)")
            enabled = false
        }
    }

    fun isEnabled(): Boolean = enabled

    @Suppress("DEPRECATION")
    private fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (versionCode=${info.longVersionCode})"
    }.getOrDefault("unavailable")

    @Synchronized fun record(tag: String, message: String) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        val line = "${clock.format(Date(now))} [${tag.take(28)}] ${message.take(360).replace("\n", " ")}"
        if (events.size == MAX_MEMORY_EVENTS) events.removeFirst()
        events.addLast(line)
        lastEventMs = now
        val directory = context?.filesDir ?: return
        worker.execute {
            runCatching {
                val file = File(directory, CURRENT)
                if (file.exists() && (file.length() > MAX_FILE_BYTES ||
                        System.currentTimeMillis() - file.lastModified() > MAX_RETENTION_MS)) {
                    val previous = File(directory, PREVIOUS)
                    previous.delete()
                    file.renameTo(previous)
                }
                file.appendText(line + "\n", Charsets.UTF_8)
            }
        }
    }

    @Synchronized fun clear() {
        events.clear()
        lastEventMs = 0L
        val dir = context?.filesDir ?: return
        worker.execute {
            File(dir, CURRENT).delete()
            File(dir, PREVIOUS).delete()
        }
    }

    @Synchronized fun snapshot(context: Context): String {
        initialize(context)
        // Wait for all previously enqueued disk writes before reading the timeline.
        val flushed = runCatching { worker.submit {}.get(4, TimeUnit.SECONDS) }.isSuccess
        val prefs = PreferencesManager(context)
        val now = System.currentTimeMillis()
        val settings = listOf(
            "diagnostics_enabled" to prefs.get(PrefKeys.MOUSE_DIAGNOSTICS_ENABLED),
            "gesture_path_trace_enabled" to prefs.get(PrefKeys.MOUSE_GESTURE_TRACE_ENABLED),
            "cursor_sensitivity" to prefs.get(PrefKeys.MOUSE_SENSITIVITY),
            "cursor_acceleration" to prefs.get(PrefKeys.MOUSE_ACCELERATION),
            "axis_rotation" to prefs.get(PrefKeys.MOUSE_AXIS_ROTATION),
            "drag_ms" to prefs.get(PrefKeys.MOUSE_DRAG_SEGMENT_MS),
            "adaptive_drag" to prefs.get(PrefKeys.MOUSE_ADAPTIVE_DRAG),
            "late_motion_compensation" to prefs.get(PrefKeys.MOUSE_CAPTURE_LATE_MOTION),
            "hold_action" to prefs.get(PrefKeys.MOUSE_HOLD_ACTION),
            "hold_threshold_ms" to prefs.get(PrefKeys.MOUSE_HOLD_THRESHOLD_MS),
            "overlay_enabled" to prefs.get(PrefKeys.MOUSE_BATTERY_OVERLAY),
            "observed_battery_percent" to prefs.get(PrefKeys.S_PEN_OBSERVED_LEVEL),
            "observed_battery_at" to prefs.get(PrefKeys.S_PEN_OBSERVED_AT)
        )
        val files = listOf(File(context.filesDir, PREVIOUS), File(context.filesDir, CURRENT))
        val persisted = if (flushed) files.filter { it.isFile }
            .flatMap { file -> runCatching { file.readLines(Charsets.UTF_8) }.getOrDefault(emptyList()) }
            else emptyList()
        val source = if (persisted.isNotEmpty()) persisted else events.toList()
        val lastLine = source.lastOrNull()
        val lastTime = lastLine?.take(23)?.let { t ->
            runCatching { clock.parse(t)?.time }.getOrNull()
        }
        return buildString {
            appendLine("PenMouse S Live diagnostic report — opt-in")
            appendLine("Created: ${clock.format(Date(now))}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} / Android SDK ${Build.VERSION.SDK_INT}")
            appendLine("Package: ${context.packageName}")
            appendLine("Installed APK: ${appVersion(context)}")
            appendLine("Mouse status AT EXPORT: ${AppToServiceEvent.serviceStatus.value}")
            appendLine("Last recorded event: ${lastLine?.take(23) ?: "none"}")
            if (lastTime != null) appendLine("Time since last recorded event: ${(now - lastTime).coerceAtLeast(0L) / 1000}s (not measured while app/service inactive)")
            appendLine("Persistent log: ${if (flushed) "flushed" else "flush timeout; recent in-memory fallback"}; oldest data rotates after 7 MB, 2 files, retention up to 7 days")
            appendLine("Scope: service/tiles/UI lifecycle, motion deltas, burst-adjacent detailed frames, click holds, gesture timing, battery parser status; no screen text")
            appendLine("Detailed trace records injected touch coordinates; Gallery UI responses are indirect evidence, not proof a photo changed.")
            appendLine("Settings:")
            settings.forEach { (key, value) -> appendLine("  $key=$value") }
            appendLine("Events (${source.size} entries):")
            source.forEach { appendLine(it) }
        }
    }
}
