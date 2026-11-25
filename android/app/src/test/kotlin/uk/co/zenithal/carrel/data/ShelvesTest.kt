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
        bookId: Long = 1,
    ) = LibraryItem(
        LibraryEntry(
            id = title.hashCode().toLong(), bookId = bookId, status = status, rating = rating, editionId = null,
            progressUnit = null, progressValue = null, progressPercent = null, progressTotal = null,
            addedAt = addedAt, updatedAt = addedAt,
            reads = finished.mapIndexed { i, f -> Read(i.toLong(), null, f, false) },
        ),
        LibraryBook(
            id = bookId, hardcoverId = null, title = title, authors = listOfNotNull(author), coverUrl = null,
            series = series?.let { SeriesEntry(1, null, it, null) }, firstPublishedYear = null,
            hardcoverRating = null, hardcoverRatingsCount = null, pageCount = null,
        ),
    )

    private fun titles(items: List<ShelfItem>) = items.map { it.book.title }

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

    @Test
    fun `a custom shelf holds its books in the library or not, most recently shelved first, and offers only the general sorts`() {
        val library = listOf(
            item("Rated", status = ReadingStatus.Read, rating = 4.0, addedAt = "2020-01-01T00:00:00+00:00", bookId = 1),
            item("Reading", status = ReadingStatus.Reading, addedAt = "2026-02-01T00:00:00+00:00", bookId = 2),
            item("Elsewhere", bookId = 3),
        )
        val shelf = CustomShelf(
            1, "Favourites", isPublic = false,
            books = listOf(
                ShelvedBook(item("Not in the library", bookId = 4).book, "2026-03-01T00:00:00+00:00"),
                ShelvedBook(library[0].book, "2026-02-15T00:00:00+00:00"),
                ShelvedBook(library[1].book, "2026-01-01T00:00:00+00:00"),
            ),
        )
        val items = customShelfItems(library, shelf)
        // By when they were shelved, not added to the library.
        assertEquals(listOf("Not in the library", "Rated", "Reading"), titles(items))
        assertEquals(null, items[0].entry)
        assertEquals(4.0, items[1].entry?.rating)
        // A book not in the library has no rating, so it goes with the unrated ones.
        assertEquals(listOf("Rated", "Not in the library", "Reading"), titles(sortItems(items, SortKey.Rating, SortDirection.Descending)))
        assertEquals(SortKey.Added, defaultSort(null))
        assertTrue(SortKey.Progress !in sortOptions(null))
        assertTrue(SortKey.Finished !in sortOptions(null))
        assertTrue(SortKey.Rating in sortOptions(null))
    }

    @Test
    fun `a profile from an API before custom shelves reads as having none`() {
        val json = """{"username":"reader","isPublic":true,"avatarUrl":null,"goals":[],"library":[]}"""
        assertTrue(CarrelJson.decodeFromString(Reader.serializer(), json).shelves.isEmpty())
    }

    @Test
    fun `putting a book on a custom shelf puts it first, once, and taking it off removes it`() {
        val book = item("Dune", bookId = 7).book
        val empty = CustomShelf(1, "Favourites", isPublic = false, books = emptyList())
        val on = empty.withBook(book, on = true, now = "2026-03-01T00:00:00Z")
        assertTrue(7L in on)
        assertEquals(on, on.withBook(book, on = true, now = "2026-04-01T00:00:00Z"))
        assertTrue(7L !in on.withBook(book, on = false, now = "2026-04-01T00:00:00Z"))
    }
}
