package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private var nextId = 1L

    /** `finished` is newest first, as the API sends reads. */
    private fun item(
        title: String,
        author: String? = "Author",
        status: ReadingStatus = ReadingStatus.Read,
        rating: Double? = null,
        updatedAt: String = "2026-01-01T00:00:00+00:00",
        finished: List<String?> = emptyList(),
    ): LibraryItem {
        val id = nextId++
        return LibraryItem(
            LibraryEntry(
                id = id, bookId = id, status = status, rating = rating, editionId = null,
                progressUnit = null, progressValue = null, progressPercent = null, progressTotal = null,
                addedAt = updatedAt, updatedAt = updatedAt,
                reads = finished.mapIndexed { i, f -> Read(i.toLong(), null, f, false) },
            ),
            LibraryBook(
                id = id, hardcoverId = id, title = title, authors = listOfNotNull(author), coverUrl = null, series = null,
                firstPublishedYear = null, hardcoverRating = null, hardcoverRatingsCount = null, pageCount = null,
            ),
        )
    }

    @Test
    fun `the home tab follows sign-in, the reader's choice, and an empty library`() {
        val books = listOf(item("A"))
        assertEquals(HomeTab.Discover, homeTab(signedIn = false, library = books, chosen = HomeTab.ForYou))
        assertEquals(HomeTab.ForYou, homeTab(signedIn = true, library = books, chosen = null))
        assertEquals(HomeTab.ForYou, homeTab(signedIn = true, library = null, chosen = null))
        assertEquals(HomeTab.Discover, homeTab(signedIn = true, library = emptyList(), chosen = null))
        assertEquals(HomeTab.ForYou, homeTab(signedIn = true, library = emptyList(), chosen = HomeTab.ForYou))
    }

    @Test
    fun `suggestions start from the latest favourite`() {
        val items = listOf(
            item("Loved long ago", rating = 5.0, finished = listOf("2024-01-01")),
            item("Loved lately", rating = 4.0, finished = listOf("2026-03-01", "2020-01-01")),
            item("Liked latest", rating = 3.5, finished = listOf("2026-09-01")),
            item("Reading", status = ReadingStatus.Reading, rating = 5.0, updatedAt = "2026-10-01T00:00:00+00:00"),
        )
        val basis = suggestionBasis(items)!!
        assertEquals("Loved lately", basis.item.book.title)
        assertTrue(basis.liked)
    }

    @Test
    fun `without favourites, the latest book not disliked`() {
        val items = listOf(
            item("Disliked", rating = 2.0, finished = listOf("2026-09-01")),
            item("Unrated", finished = listOf("2026-05-01")),
            item("Fine", rating = 3.0, finished = listOf("2026-01-01")),
        )
        val basis = suggestionBasis(items)!!
        assertEquals("Unrated", basis.item.book.title)
        assertFalse(basis.liked)
        assertNull(suggestionBasis(listOf(item("Only disliked", rating = 1.0))))
    }

    @Test
    fun `a read without a finish date counts from when it last changed`() {
        val items = listOf(
            item("Finished", finished = listOf("2026-02-01")),
            item("Date unknown", updatedAt = "2026-06-01T00:00:00+00:00", finished = listOf(null)),
        )
        assertEquals("Date unknown", suggestionBasis(items)!!.item.book.title)
    }

    @Test
    fun `favourite authors rank by books read, then average rating`() {
        val items = listOf(
            item("A1", "Ann", rating = 3.0, finished = listOf("2026-01-01")),
            item("A2", "Ann", rating = 3.0, finished = listOf("2026-05-01")),
            item("B1", "Bea", rating = 5.0, finished = listOf("2026-02-01")),
            item("B2", "Bea", status = ReadingStatus.Reading, updatedAt = "2026-08-01T00:00:00+00:00"),
            item("C1", "Cal", rating = 5.0, finished = listOf("2026-03-01")),
            item("D1", "Dee", status = ReadingStatus.WantToRead),
            item("E1", "Eve", rating = 1.0, finished = listOf("2026-04-01")),
            item("No author", author = null),
        )
        // Bea's average counts only rated books (5.0), so she beats Ann's 3.0; each brings their latest book.
        assertEquals(listOf("B2", "A2", "C1"), favouriteAuthorBooks(items).map { it.book.title })
    }

    @Test
    fun `ties fall back to the API's order, however the list was patched`() {
        val older = item("Older", "Ann", finished = listOf("2026-01-01"), updatedAt = "2026-01-01T00:00:00+00:00")
        val newer = item("Newer", "Bea", finished = listOf("2026-01-01"), updatedAt = "2026-02-01T00:00:00+00:00")
        assertEquals(listOf("Newer", "Older"), favouriteAuthorBooks(listOf(older, newer)).map { it.book.title })
        assertEquals("Newer", suggestionBasis(listOf(older, newer))!!.item.book.title)
    }

    private fun seriesBook(id: Int, position: Double?) = SeriesBook(id, position, "Book $id", emptyList(), null, null, null, null)

    @Test
    fun nextInSeriesIsTheLowestPositionAfter() {
        val series = SeriesDetail(1, "Stormlight", null, null, listOf(seriesBook(1, 1.0), seriesBook(3, 3.0), seriesBook(25, 2.5), seriesBook(2, 2.0), seriesBook(9, null)), emptyList())
        assertEquals(2, nextInSeries(series, 1.0, emptySet())?.hardcoverId)
        assertEquals(25, nextInSeries(series, 2.0, emptySet())?.hardcoverId)
        assertNull(nextInSeries(series, 3.0, emptySet()))
    }

    @Test
    fun nextInSeriesDoesNotSkipAnOwnedBook() {
        val series = SeriesDetail(1, "Stormlight", null, null, listOf(seriesBook(1, 1.0), seriesBook(2, 2.0), seriesBook(3, 3.0)), emptyList())
        assertNull(nextInSeries(series, 1.0, setOf(2L)))
    }
}
