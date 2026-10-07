package uk.co.zenithal.carrel.data

import java.math.BigDecimal
import kotlin.math.roundToInt

// Ported from web/src/components/LibraryPanel.tsx; keep the two in step.

/** "62% · Page 210 of 340", or as much of it as is known. */
fun progressSummary(entry: LibraryEntry): String {
    val unit = entry.progressUnit
    val value = entry.progressValue
    if (unit == null || value == null) return "No progress recorded yet."
    val total = entry.progressTotal?.takeIf { it > 0 }
    val done = when (unit) {
        ProgressUnit.Page -> "Page ${plainNumber(value)}${total?.let { " of $it" } ?: ""}"
        ProgressUnit.Seconds -> formatDuration(value.toInt()) + (total?.let { " of ${formatDuration(it)}" } ?: "")
        ProgressUnit.Percent -> null
    }
    val share = entry.progressPercent?.let { "${it.roundToInt()}%" }
    return listOfNotNull(share, done).joinToString(" · ").ifEmpty { plainNumber(value) }
}

/** A saved progress value as the reader typed it: a page or percent, or hours:minutes. */
fun formatProgressValue(unit: ProgressUnit, value: Double): String {
    if (unit != ProgressUnit.Seconds) return plainNumber(value)
    val minutes = (value / 60).roundToInt()
    return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
}

/** What the reader typed as a progress value, or null if it isn't one: hours:minutes (or hours) for time. */
fun parseProgressValue(unit: ProgressUnit, text: String): Double? {
    val trimmed = text.trim()
    if (unit == ProgressUnit.Seconds) {
        val match = Regex("^(\\d+)(?::(\\d{1,2}))?$").find(trimmed) ?: return null
        val hours = match.groupValues[1].toDouble()
        val minutes = match.groupValues[2].ifEmpty { "0" }.toDouble()
        return (hours * 60 + minutes) * 60
    }
    val value = trimmed.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < 0 || (unit == ProgressUnit.Percent && value > 100)) return null
    return value
}

/** 210.0 as "210", 12.5 as "12.5", as JavaScript prints numbers. */
fun plainNumber(value: Double): String = BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
