package pl.jojczak.penmouses.mousemode.mouse

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import pl.jojczak.penmouses.core.common.utils.PrefKeys
import pl.jojczak.penmouses.core.common.utils.PreferencesManager

/**
 * Lightweight accessibility overlay without SYSTEM_ALERT_WINDOW.
 * A missing battery value is deliberately reported as unavailable, not as phone battery.
 * Samsung's public S Pen Remote SDK does not provide battery level. We attempt only
 * harmless, read-only Samsung-specific settings, which can be missing on One UI.
 */
internal class SPenBatteryOverlay(
    private val context: Context,
    private val preferences: PreferencesManager,
) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTask = Runnable { if (attached) refresh() }
    private val label = TextView(context)
    private var attached = false
    private var unlocked = false
    private var dragging = false
    private var anchorX = 0f
    private var anchorY = 0f
    private var anchorScreenX = 0
    private var anchorScreenY = 0
    private val density = context.resources.displayMetrics.density

    private val layout = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    init {
        label.textSize = 13f
        label.setPadding(dp(10), dp(7), dp(10), dp(7))
        label.setOnTouchListener { _, event ->
            if (!unlocked) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    anchorX = event.rawX
                    anchorY = event.rawY
                    anchorScreenX = layout.x
                    anchorScreenY = layout.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val metrics = context.resources.displayMetrics
                    layout.x = (anchorScreenX + event.rawX - anchorX).toInt().coerceIn(0, (metrics.widthPixels - label.width).coerceAtLeast(0))
                    layout.y = (anchorScreenY + event.rawY - anchorY).toInt().coerceIn(0, (metrics.heightPixels - label.height).coerceAtLeast(0))
                    applyLayout()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    val metrics = context.resources.displayMetrics
                    preferences.put(PrefKeys.MOUSE_BATTERY_OVERLAY_X, (layout.x.toFloat() / metrics.widthPixels.coerceAtLeast(1)).coerceIn(0f, 1f))
                    preferences.put(PrefKeys.MOUSE_BATTERY_OVERLAY_Y, (layout.y.toFloat() / metrics.heightPixels.coerceAtLeast(1)).coerceIn(0f, 1f))
                    true
                }
                else -> false
            }
        }
    }

    fun refresh() {
        handler.removeCallbacks(refreshTask)
        if (!preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY)) {
            hide()
            return
        }

        val observed = preferences.get(PrefKeys.S_PEN_OBSERVED_LEVEL)
        val observedAt = preferences.get(PrefKeys.S_PEN_OBSERVED_AT)
        val age = System.currentTimeMillis() - observedAt
        val observedValid = observed in 0..100 && observedAt > 0L && age in 0..MAX_OBSERVED_AGE_MS
        val batteryLevel = if (observedValid) observed else readBatteryLevel()
        val clock = if (observedValid) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(observedAt)) else null
        val compact = preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_COMPACT)
        val showTime = preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_SHOW_TIME)
        label.text = when {
            compact -> if (batteryLevel != null) "${batteryLevel}%" else "—"
            observedValid && showTime -> "S Pen  ${batteryLevel}% · ${clock}"
            batteryLevel != null -> "S Pen  ${batteryLevel}%"
            else -> "S Pen  —"
        }
        val scale = preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_SCALE).coerceIn(0.6f, 2f)
        label.textSize = 13f * scale
        label.setPadding(dp((if (compact) 8 else 10) * scale), dp(7 * scale),
            dp((if (compact) 8 else 10) * scale), dp(7 * scale))
        label.contentDescription = when {
            observedValid -> "S Pen ${batteryLevel}%, ostatni odczyt o godzinie ${clock}"
            batteryLevel != null -> "S Pen ${batteryLevel}%"
            else -> "S Pen: brak dostępnego odczytu"
        }

        val colors = intArrayOf(0xE51E1E1E.toInt(), 0xF5EEEEEE.toInt(), 0xED2457B8.toInt(), 0xED147D52.toInt())
        val choice = preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_COLOR).toInt().coerceIn(0, 3)
        val background = GradientDrawable().apply {
            setColor(colors[choice])
            cornerRadius = dp(14 * scale).toFloat()
        }
        label.background = background
        label.setTextColor(if (choice == 1) Color.BLACK else Color.WHITE)
        label.alpha = preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_ALPHA).coerceIn(0.2f, 1f)

        val shouldUnlock = preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_UNLOCKED)
        layout.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (shouldUnlock) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        unlocked = shouldUnlock

        if (!attached) {
            val metrics = context.resources.displayMetrics
            layout.x = (preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_X) * metrics.widthPixels).toInt()
            layout.y = (preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_Y) * metrics.heightPixels).toInt()
            try {
                manager.addView(label, layout)
                attached = true
            } catch (_: RuntimeException) {
                attached = false
            }
        } else {
            // Sync slider changes in either lock mode; only resist updates during finger drag.
            if (!dragging) {
                val metrics = context.resources.displayMetrics
                layout.x = (preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_X) * metrics.widthPixels).toInt()
                layout.y = (preferences.get(PrefKeys.MOUSE_BATTERY_OVERLAY_Y) * metrics.heightPixels).toInt()
            }
            applyLayout()
        }
        if (attached) handler.postDelayed(refreshTask, 30_000L)
    }

    private fun applyLayout() {
        if (attached && label.isAttachedToWindow) {
            try { manager.updateViewLayout(label, layout) } catch (_: IllegalArgumentException) { }
        }
    }

    fun hide() {
        handler.removeCallbacks(refreshTask)
        if (!attached) return
        try { manager.removeView(label) } catch (_: IllegalArgumentException) { }
        attached = false
    }

    private fun dp(value: Int) = (density * value).toInt()
    private fun dp(value: Float) = (density * value).toInt()

    private companion object { const val MAX_OBSERVED_AGE_MS = 6L * 60L * 60L * 1000L }

    /**
     * Diagnostic candidates; verified S Pen-only names, never the phone's battery level.
     * New Samsung firmware might not expose them to third-party apps.
     */
    private fun readBatteryLevel(): Int? {
        val keys = arrayOf("spen_battery_level", "spen_battery_percentage", "spen_remote_battery_level")
        for (key in keys) {
            val level = try { Settings.System.getInt(context.contentResolver, key, -1) } catch (_: Exception) { -1 }
            if (level in 0..100) return level
        }

        // Search visible system settings for Samsung's firmware-dependent key naming.
        // Never read/display the phone battery. Unknown and inaccessible remain unknown.
        return try {
            context.contentResolver.query(Settings.System.CONTENT_URI, arrayOf("name", "value"), null, null, null)?.use { cursor ->
                var result: Int? = null
                val keyColumn = cursor.getColumnIndex("name")
                val valueColumn = cursor.getColumnIndex("value")
                if (keyColumn < 0 || valueColumn < 0) return@use null
                while (cursor.moveToNext()) {
                    val key = cursor.getString(keyColumn)?.lowercase() ?: continue
                    if (!(key.contains("spen") || key.contains("s_pen"))) continue
                    if (!key.contains("battery")) continue
                    val level = cursor.getString(valueColumn)?.toIntOrNull()
                    if (level != null && level in 0..100) { result = level; break }
                }
                result
            }
        } catch (_: Exception) { null }
    }
}
