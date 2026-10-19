package pl.jojczak.penmouses.mousemode.mouse

import java.util.ArrayDeque

/**
 * Diagnostic-only rolling window. It emits consecutive sensor samples around
 * suspicious motion without changing the movement pipeline or persisting every
 * ordinary SDK callback. Sequence numbers expose any omitted quiet intervals.
 */
internal class MotionTraceWindow(
    private val beforeCount: Int,
    private val afterCount: Int,
    private val log: (String) -> Unit,
) {
    init {
        require(beforeCount >= 0 && afterCount >= 0)
    }

    private val preceding = ArrayDeque<String>()
    private var followingRemaining = 0
    private var sequence = 0L

    fun reset() {
        preceding.clear()
        followingRemaining = 0
        sequence = 0L
    }

    fun add(detail: String, trigger: Boolean) {
        val frame = "seq=${++sequence} $detail"
        if (trigger) {
            if (followingRemaining == 0) {
                while (preceding.isNotEmpty()) log(preceding.removeFirst())
            }
            preceding.clear()
            log(frame)
            followingRemaining = afterCount
        } else if (followingRemaining > 0) {
            log(frame)
            followingRemaining--
        } else if (beforeCount > 0) {
            preceding.addLast(frame)
            while (preceding.size > beforeCount) preceding.removeFirst()
        }
    }
}
