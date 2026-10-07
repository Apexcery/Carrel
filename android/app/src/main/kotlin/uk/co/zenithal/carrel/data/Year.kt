package uk.co.zenithal.carrel.data

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

// Ported from web/src/shelves.ts, web/src/components/ReadingGoal.tsx, and web/src/components/YearInBooks.tsx; keep
// them in step.

/** The most a reading goal can be; the API allows the same. */
const val MAX_GOAL_BOOKS = 1000

/** A read finished in a year: the book, and the date it was finished. */
data class FinishedRead(val item: LibraryItem, val finishedOn: String)

/** Reads finished in a year, one for each read, so a book read twice that year counts twice. */
fun readsFinishedIn(items: List<LibraryItem>, year: Int): List<FinishedRead> =
    items.flatMap { item ->
        item.entry.reads.mapNotNull { read -> read.finishedOn?.takeIf { it.startsWith("$year-") }?.let { FinishedRead(item, it) } }
    }

/** Against where the reader would be by now, to the nearest book, reading at an even rate to reach the goal. */
fun goalPace(finished: Int, books: Int, now: LocalDateTime): String {
    if (finished >= books) return "Goal reached"
    val start = LocalDateTime.of(now.year, 1, 1, 0, 0)
    val share = Duration.between(start, now).toMillis().toDouble() / Duration.between(start, start.plusYears(1)).toMillis()
    val difference = finished - (books * share).roundToInt()
    val count = "${abs(difference)} ${if (abs(difference) == 1) "book" else "books"}"
    return when {
        difference > 0 -> "$count ahead of schedule"
        difference < 0 -> "$count behind schedule"
        else -> "On track"
    }
}

/**
 * A reader's year so far: books finished (rereads count again), pages read (from each book's edition, so books without
 * a page count add none), the average rating of the books finished (null until one has a rating), and the titles
 * finished in each month, January first.
 */
data class YearInBooks(val finished: Int, val pages: Int, val averageRating: Double?, val months: List<List<String>>)

fun yearInBooks(items: List<LibraryItem>, year: Int): YearInBooks {
    val reads = readsFinishedIn(items, year)
    val ratings = reads.map { it.item }.distinct().mapNotNull { it.entry.rating }
    return YearInBooks(
        finished = reads.size,
        pages = reads.sumOf { it.item.book.pageCount ?: 0 },
        averageRating = ratings.takeIf { it.isNotEmpty() }?.average(),
        months = (1..12).map { month -> reads.filter { it.finishedOn.substring(5, 7).toInt() == month }.map { it.item.book.title } },
    )
}
