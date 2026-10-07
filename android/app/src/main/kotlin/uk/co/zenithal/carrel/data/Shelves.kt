package uk.co.zenithal.carrel.data

import java.text.Collator
import java.util.Locale

// Ported from web/src/shelves.ts; keep the two in step.

enum class SortKind { Text, Number, Date }

enum class SortDirection { Ascending, Descending }

enum class SortKey(
    val label: String,
    val kind: SortKind,
    /** Only offered on these shelves; null for all of them. */
    val shelves: Set<ReadingStatus>?,
    val value: (LibraryItem) -> Comparable<*>?,
) {
    Added("Date added", SortKind.Date, null, { it.entry.addedAt }),
    // Any save stamps it, but for a book being read that's nearly always a progress update or starting it.
    Updated("Last updated", SortKind.Date, setOf(ReadingStatus.Reading, ReadingStatus.Paused), { it.entry.updatedAt }),
    Finished("Date finished", SortKind.Date, setOf(ReadingStatus.Read, ReadingStatus.DidNotFinish), ::lastFinished),
    Progress("Progress", SortKind.Number, setOf(ReadingStatus.Reading, ReadingStatus.Paused), { it.entry.progressPercent }),
    Title("Title", SortKind.Text, null, { it.book.title }),
    Author("Author", SortKind.Text, null, { surname(it.book.authors.firstOrNull()) }),
    Rating("Your rating", SortKind.Number, null, { it.entry.rating }),
    Hardcover("Hardcover rating", SortKind.Number, null, { it.book.hardcoverRating }),
    Popularity("Popularity", SortKind.Number, null, { it.book.hardcoverRatingsCount }),
    Published("Year published", SortKind.Number, null, { it.book.firstPublishedYear }),
}

fun sortOptions(status: ReadingStatus): List<SortKey> = SortKey.entries.filter { it.shelves == null || status in it.shelves }

/**
 * Read by when it was finished; Reading and Paused by when the book was last updated, so the latest progress comes
 * first; the rest by when the book was added.
 */
fun defaultSort(status: ReadingStatus): SortKey = when (status) {
    ReadingStatus.Read -> SortKey.Finished
    ReadingStatus.Reading, ReadingStatus.Paused -> SortKey.Updated
    else -> SortKey.Added
}

/** Names sort A–Z; dates, ratings, and counts sort newest or highest first. */
fun naturalDirection(key: SortKey): SortDirection =
    if (key.kind == SortKind.Text) SortDirection.Ascending else SortDirection.Descending

fun directionLabel(key: SortKey, direction: SortDirection): String {
    val ascending = direction == SortDirection.Ascending
    return when (key.kind) {
        SortKind.Text -> if (ascending) "A–Z" else "Z–A"
        SortKind.Number -> if (ascending) "Lowest first" else "Highest first"
        SortKind.Date -> if (ascending) "Oldest first" else "Newest first"
    }
}

/** Sorts by the given key; books without a value go last in either direction, then ties go by title. */
fun sortItems(items: List<LibraryItem>, key: SortKey, direction: SortDirection): List<LibraryItem> {
    val sign = if (direction == SortDirection.Ascending) 1 else -1
    return items.sortedWith { a, b ->
        val x = key.value(a)?.takeIf { it != "" }
        val y = key.value(b)?.takeIf { it != "" }
        val byValue = when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            x is Number && y is Number -> sign * x.toDouble().compareTo(y.toDouble())
            else -> sign * compareText(x.toString(), y.toString())
        }
        if (byValue != 0) byValue else compareText(a.book.title, b.book.title)
    }
}

/** Keeps books whose title, author, or series contains every word of the query. */
fun filterItems(items: List<LibraryItem>, query: String): List<LibraryItem> {
    val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return items
    return items.filter { item ->
        val text = (listOf(item.book.title) + item.book.authors + (item.book.series?.name ?: "")).joinToString(" ").lowercase()
        terms.all { it in text }
    }
}

/** A shelf's books in its default order. */
fun shelfItems(library: List<LibraryItem>, status: ReadingStatus): List<LibraryItem> {
    val key = defaultSort(status)
    return sortItems(library.filter { it.entry.status == status }, key, naturalDirection(key))
}

/** The latest date the book was finished, if any. */
fun lastFinished(item: LibraryItem): String? = item.entry.reads.mapNotNull { it.finishedOn }.maxOrNull()

private fun surname(name: String?): String? = name?.trim()?.split(Regex("\\s+"))?.last()

private val COLLATOR = Collator.getInstance(Locale.UK).apply { strength = Collator.PRIMARY }
private val CHUNKS = Regex("\\d+|\\D+")

/**
 * Compares text as the website's Intl.Collator does (ignoring case and accents) with its numeric option, which Java's
 * Collator lacks: runs of digits compare as numbers, so "Book 2" comes before "Book 10".
 */
fun compareText(a: String, b: String): Int {
    val x = CHUNKS.findAll(a).map { it.value }.toList()
    val y = CHUNKS.findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(x.size, y.size)) {
        val p = x[i]
        val q = y[i]
        val result = if (p[0].isDigit() && q[0].isDigit()) compareNumbers(p, q) else COLLATOR.compare(p, q)
        if (result != 0) return result
    }
    return x.size.compareTo(y.size)
}

/** Digit strings by value, however long. */
private fun compareNumbers(p: String, q: String): Int {
    val a = p.trimStart('0')
    val b = q.trimStart('0')
    return if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
}
