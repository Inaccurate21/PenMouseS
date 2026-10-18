package pl.jojczak.penmouses.service

import android.view.accessibility.AccessibilityNodeInfo
import pl.jojczak.penmouses.core.common.utils.DiagnosticRecorder

/**
 * Reads the battery percentage already displayed on the official Samsung S Pen screen.
 * Does not request Samsung internal permissions, scan other apps, or inspect screenshots.
 * Only the percent is returned and the visible UI must be opened by the user.
 */
internal object SamsungPenBatteryCapture {
    private val percent = Regex("""^\s*(100|[1-9]?\d)\s*%\s*$""")

    fun extract(root: AccessibilityNodeInfo?): Int? {
        if (root == null) {
            DiagnosticRecorder.record("battery-parser", "rootNodeUnavailable")
            return null
        }
        val texts = ArrayList<String>(60)
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty() && texts.size < 240) {
            val node = stack.removeLast()
            node.text?.toString()?.takeIf(String::isNotBlank)?.let(texts::add)
            node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(texts::add)
            if (texts.size >= 240) break
            for (index in 0 until node.childCount.coerceAtMost(30)) {
                node.getChild(index)?.let(stack::addLast)
            }
        }
        return extractFromTexts(texts)
    }

    // Pure parser: never mistake unrelated charge levels on a generic Settings screen.
    fun extractFromTexts(texts: List<String>): Int? {
        val normalized = texts.map { it.trim() }
        val isPenSettings = normalized.any { it.equals("Rysik S Pen", ignoreCase = true) ||
            it.equals("S Pen", ignoreCase = true) }
        val hasPenContext = normalized.any { it.contains("Wyjęto", ignoreCase = true) ||
            it.contains("Włożono", ignoreCase = true) ||
            it.contains("Akcje gestem", ignoreCase = true) ||
            it.contains("Air actions", ignoreCase = true) ||
            it.contains("Removed", ignoreCase = true) ||
            it.contains("Inserted", ignoreCase = true) }
        if (!isPenSettings || !hasPenContext) {
            DiagnosticRecorder.record("battery-parser", "noPenPage title=$isPenSettings penContext=$hasPenContext nodeTexts=${normalized.size}")
            return null
        }
        val values = normalized.mapNotNull { percent.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.distinct()
        // If multiple percentages appear, never guess which one represents the stylus.
        DiagnosticRecorder.record("battery-parser", "penPage=true nodeTexts=${normalized.size} percentCandidates=$values")
        return values.singleOrNull()
    }
}
