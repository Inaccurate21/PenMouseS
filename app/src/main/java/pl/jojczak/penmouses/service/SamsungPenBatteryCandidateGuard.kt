package pl.jojczak.penmouses.service

/**
 * Two matching readings must arrive within a short, strictly bounded interval.
 * A single unrelated percentage in Samsung's accessibility tree is not trusted.
 * Measurements are observed only while the verified S Pen screen is visible.
 */
internal class SamsungPenBatteryCandidateGuard {
    private var lastValue: Int? = null
    private var lastAtMs = 0L

    fun observe(value: Int, atMs: Long): Boolean {
        if (value !in 0..100 || atMs <= 0L) {
            reset()
            return false
        }
        val matching = lastValue == value && atMs - lastAtMs in MIN_DELAY_MS..MAX_DELAY_MS
        lastValue = value
        lastAtMs = atMs
        return matching
    }

    fun reset() {
        lastValue = null
        lastAtMs = 0L
    }

    private companion object {
        const val MIN_DELAY_MS = 300L
        const val MAX_DELAY_MS = 30_000L
    }
}
