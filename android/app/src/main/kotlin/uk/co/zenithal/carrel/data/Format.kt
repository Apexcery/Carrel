package uk.co.zenithal.carrel.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Ported from web/src/format.ts; keep the two in step.

/** Series position as written on a spine: 1, 1.5, 3.5. */
fun seriesPosition(position: Double?): String? =
    position?.let { BigDecimal(it).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() }

/** Hides a subtitle that only repeats the end of the title, e.g. "Mistborn: The Final Empire" / "The Final Empire". */
fun displaySubtitle(title: String, subtitle: String?): String? =
    subtitle?.takeIf { it.isNotEmpty() && !title.lowercase().endsWith(it.lowercase()) }

/** Names with the Oxford comma: "A", "A and B", "A, B, and C". */
fun listNames(names: List<String>): String = when {
    names.size <= 2 -> names.joinToString(" and ")
    else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
}

private val ROLE_PHRASES = mapOf(
    "translator" to "Translated by",
    "illustrator" to "Illustrated by",
    "narrator" to "Narrated by",
    "editor" to "Edited by",
    "foreword" to "Foreword by",
    "afterword" to "Afterword by",
    "cover artist" to "Cover by",
)

/** "Translated by X" style credits for everyone who isn't an author, grouped by role. */
fun otherCredits(contributors: List<Contributor>): List<String> =
    contributors.filter { it.role != "author" }
        .groupBy({ it.role }, { it.name })
        .map { (role, names) ->
            val phrase = ROLE_PHRASES[role] ?: (role.replaceFirstChar { it.uppercase() } + ":")
            "$phrase ${listNames(names)}"
        }

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

/** "2006-07-17" as "17 Jul 2006". Null for a missing or unreadable date. */
fun formatDate(isoDate: String?): String? =
    isoDate?.let { runCatching { LocalDate.parse(it).format(DATE) }.getOrNull() }

fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = Math.round((seconds % 3600) / 60.0).toInt()
    return if (hours > 0) "$hours h $minutes m" else "$minutes m"
}

/** A count with thousands separators, as the website's toLocaleString(). */
fun formatCount(count: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(count)
