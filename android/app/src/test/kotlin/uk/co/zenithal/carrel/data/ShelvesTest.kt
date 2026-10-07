package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelvesTest {
    private fun item(
        title: String,
        author: String? = null,
        status: ReadingStatus = ReadingStatus.Read,
        rating: Double? = null,
        addedAt: String = "2026-01-01T00:00:00+00:00",
        finished: List<String?> = emptyList(),
        series: String? = null,
    ) = LibraryItem(
        LibraryEntry(
            id = title.hashCode().toLong(), bookId = 1, status = status, rating = rating, editionId = null,
            progressUnit = null, progressValue = null, progressPercent = null, progressTotal = null,
            addedAt = addedAt, updatedAt = addedAt,
            reads = finished.mapIndexed { i, f -> Read(i.toLong(), null, f, false) },
        ),
        LibraryBook(
            id = 1, hardcoverId = null, title = title, authors = listOfNotNull(author), coverUrl = null,
            series = series?.let { SeriesEntry(1, null, it, null) }, firstPublishedYear = null,
            hardcoverRating = null, hardcoverRatingsCount = null, pageCount = null,
        ),
    )

    private fun titles(items: List<LibraryItem>) = items.map { it.book.title }

    @Test
    fun `text compares numbers by value and ignores case and accents`() {
        assertTrue(compareText("Book 2", "Book 10") < 0)
        assertTrue(compareText("book 2", "Book 2") == 0)
        assertTrue(compareText("Émile", "emile") == 0)
        assertTrue(compareText("Apple", "Banana") < 0)
        assertTrue(compareText("Book", "Book 1") < 0)
        assertTrue(compareText("Part 007", "Part 7") == 0)
    }

    @Test
    fun `books without a value go last in either direction, then ties go by title`() {
        val items = listOf(item("C", rating = 3.0), item("A"), item("B", rating = 3.0), item("D", rating = 5.0))
        assertEquals(listOf("D", "B", "C", "A"), titles(sortItems(items, SortKey.Rating, SortDirection.Descending)))
        assertEquals(listOf("B", "C", "D", "A"), titles(sortItems(items, SortKey.Rating, SortDirection.Ascending)))
    }

    @Test
    fun `authors sort by surname`() {
        val items = listOf(item("X", "Ursula K. Le Guin"), item("Y", "Iain M. Banks"), item("Z"))
        assertEquals(listOf("Y", "X", "Z"), titles(sortItems(items, SortKey.Author, SortDirection.Ascending)))
    }

    @Test
    fun `finished sorts by the latest read`() {
        val items = listOf(
            item("Old", finished = listOf("2020-05-01")),
            item("Reread", finished = listOf("2019-01-01", "2026-03-01")),
            item("Unknown", finished = listOf(null)),
        )
        assertEquals(listOf("Reread", "Old", "Unknown"), titles(sortItems(items, SortKey.Finished, SortDirection.Descending)))
    }

    @Test
    fun `filter matches every word across title, author, and series`() {
        val items = listOf(item("The Final Empire", "Brandon Sanderson", series = "Mistborn"), item("Dune", "Frank Herbert"))
        assertEquals(listOf("The Final Empire"), titles(filterItems(items, "mistborn sanderson")))
        assertEquals(listOf("Dune"), titles(filterItems(items, "  DUNE ")))
        assertEquals(2, filterItems(items, " ").size)
        assertEquals(0, filterItems(items, "dune sanderson").size)
    }

    @Test
    fun `each shelf has its own default order and options`() {
        assertEquals(SortKey.Finished, defaultSort(ReadingStatus.Read))
        assertEquals(SortKey.Updated, defaultSort(ReadingStatus.Paused))
        assertEquals(SortKey.Added, defaultSort(ReadingStatus.WantToRead))
        assertEquals(SortKey.Added, defaultSort(ReadingStatus.DidNotFinish))
        assertTrue(SortKey.Progress in sortOptions(ReadingStatus.Reading))
        assertTrue(SortKey.Progress !in sortOptions(ReadingStatus.Read))
        assertTrue(SortKey.Finished in sortOptions(ReadingStatus.DidNotFinish))
    }

    @Test
    fun `directions read naturally for each kind of sort`() {
        assertEquals(SortDirection.Ascending, naturalDirection(SortKey.Title))
        assertEquals(SortDirection.Descending, naturalDirection(SortKey.Added))
        assertEquals("Z–A", directionLabel(SortKey.Author, SortDirection.Descending))
        assertEquals("Lowest first", directionLabel(SortKey.Rating, SortDirection.Ascending))
        assertEquals("Newest first", directionLabel(SortKey.Updated, SortDirection.Descending))
    }

    @Test
    fun `a shelf holds only its own books, in its default order`() {
        val items = listOf(
            item("Earlier", status = ReadingStatus.WantToRead, addedAt = "2026-01-01T00:00:00+00:00"),
            item("Later", status = ReadingStatus.WantToRead, addedAt = "2026-02-01T00:00:00+00:00"),
            item("Done"),
        )
        assertEquals(listOf("Later", "Earlier"), titles(shelfItems(items, ReadingStatus.WantToRead)))
    }
}
