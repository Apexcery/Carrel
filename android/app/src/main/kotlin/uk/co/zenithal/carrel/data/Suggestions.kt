package uk.co.zenithal.carrel.data

// Ported from web/src/pages/HomePage.tsx; keep the two in step.

/** The lowest rating that counts as liking a book. */
private const val FAVOURITE_RATING = 4.0
/** Ratings below this mean the reader didn't like the book, so it isn't used for suggestions. */
private const val DISLIKED_BELOW = 3.0
/** Authors tried in turn for "More by", while the reader has everything by the ones before. */
private const val FAVOURITE_AUTHORS = 3

enum class HomeTab(val label: String) { ForYou("For you"), Discover("Discover") }

/**
 * The Home tab to show. Signed out there's only Discover. A reader with nothing on their shelves has nothing to base
 * For you on, so they start on Discover too (once the library has loaded and shown that), unless they chose a tab.
 */
fun homeTab(signedIn: Boolean, library: List<LibraryItem>?, chosen: HomeTab?): HomeTab = when {
    !signedIn -> HomeTab.Discover
    chosen != null -> chosen
    library?.isEmpty() == true -> HomeTab.Discover
    else -> HomeTab.ForYou
}

/** The book "Because you…" suggestions are based on, and whether the reader liked it or only read it. */
data class SuggestionBasis(val item: LibraryItem, val liked: Boolean)

/**
 * The book to base "Because you…" suggestions on: the most recently finished book the reader rated 4 stars or more,
 * else (for readers who don't rate) the most recently finished book they didn't rate below 3.
 */
fun suggestionBasis(items: List<LibraryItem>): SuggestionBasis? {
    val read = inApiOrder(items).filter { it.entry.status == ReadingStatus.Read }.sortedByDescending(::finishedOrChanged)
    read.firstOrNull { (it.entry.rating ?: 0.0) >= FAVOURITE_RATING }?.let { return SuggestionBasis(it, liked = true) }
    return read.firstOrNull { it.entry.rating.let { r -> r == null || r >= DISLIKED_BELOW } }?.let { SuggestionBasis(it, liked = false) }
}

/**
 * Books by the reader's favourite authors, one each, best first, to find more by them: authors ranked by books read or
 * being read (ties go to the higher average rating), each with the book the reader finished or changed most recently.
 */
fun favouriteAuthorBooks(items: List<LibraryItem>): List<LibraryItem> {
    val byAuthor = inApiOrder(items)
        .filter { (it.entry.status == ReadingStatus.Read || it.entry.status == ReadingStatus.Reading) && it.book.authors.isNotEmpty() }
        .groupBy { it.book.authors.first() }
    return byAuthor.values
        .sortedWith(compareByDescending<List<LibraryItem>> { it.size }.thenByDescending(::averageRating))
        .take(FAVOURITE_AUTHORS)
        .map { books -> books.sortedByDescending(::finishedOrChanged).first() }
}

/**
 * The library in the API's order (last changed first), which ties fall back to. A list patched on the device after a
 * change keeps the book where it was, or adds it at the end.
 */
private fun inApiOrder(items: List<LibraryItem>) = items.sortedByDescending { it.entry.updatedAt }

/** When the book was last finished (reads come newest first), else when its entry last changed. */
private fun finishedOrChanged(item: LibraryItem) = item.entry.reads.firstOrNull()?.finishedOn ?: item.entry.updatedAt

private fun averageRating(books: List<LibraryItem>) = books.mapNotNull { it.entry.rating }.ifEmpty { listOf(0.0) }.average()

/**
 * The book after `position` in a series, as the API's "Next in your series" chooses it (RecommendationService), or null
 * when there isn't one or the reader already has it: if the next book is on a shelf, the reader knows about it, so
 * this doesn't skip ahead to the one after.
 */
fun nextInSeries(series: SeriesDetail, position: Double, owned: Set<Long>): SeriesBook? =
    series.books.filter { (it.position ?: return@filter false) > position }.minByOrNull { it.position!! }
        ?.takeIf { it.hardcoverId.toLong() !in owned }
