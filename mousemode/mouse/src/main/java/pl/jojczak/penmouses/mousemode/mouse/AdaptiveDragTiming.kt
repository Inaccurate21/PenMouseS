package pl.jojczak.penmouses.mousemode.mouse

/**
 * Experimental adaptive gesture segment duration. Longer strokes can amortize Android
 * callback overhead when a 5ms stroke is consistently acknowledged after ~20ms.
 * The pointer still uses only the newest target (no backlog is replayed).
 */
internal class AdaptiveDragTiming(private val enabled: Boolean) {
    private var emaMs = 0f
    private var samples = 0

    fun observe(requestedMs: Long, actualMs: Long) {
        if (!enabled || requestedMs <= 1L || actualMs !in 1L..500L) return
        emaMs = if (samples == 0) actualMs.toFloat() else 0.3f * actualMs + 0.7f * emaMs
        if (samples < 1000) samples++
    }

    fun choose(baseMs: Long): Long {
        val base = baseMs.coerceIn(1L, 32L)
        if (!enabled || samples < 5 || emaMs <= base * 1.6f) return base
        // Experimental: at most 24 ms. Never silently make user-requested >24 ms shorter.
        return maxOf(base, (emaMs * 0.7f).toLong().coerceAtMost(24L))
    }
}
