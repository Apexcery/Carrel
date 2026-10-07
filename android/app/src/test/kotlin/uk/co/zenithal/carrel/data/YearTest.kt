package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class YearTest {
    private var nextId = 1L

    private fun item(title: String, finished: List<String?>, rating: Double? = null, pages: Int? = null): LibraryItem {
        val id = nextId++
        return LibraryItem(
            LibraryEntry(
                id = id, bookId = id, status = ReadingStatus.Read, rating = rating, editionId = null,
                progressUnit = null, progressValue = null, progressPercent = null, progressTotal = null,
                addedAt = "", updatedAt = "", reads = finished.mapIndexed { i, f -> Read(i.toLong(), null, f, f == null) },
            ),
            LibraryBook(
                id = id, hardcoverId = null, title = title, authors = emptyList(), coverUrl = null, series = null,
                firstPublishedYear = null, hardcoverRating = null, hardcoverRatingsCount = null, pageCount = pages,
            ),
        )
    }

    @Test
    fun `rereads in the year count again, other years and unknown dates don't`() {
        val items = listOf(
            item("Twice", listOf("2026-09-01", "2026-02-01")),
            item("Last year", listOf("2025-12-31")),
            item("Unknown", listOf(null)),
        )
        assertEquals(listOf("2026-09-01", "2026-02-01"), readsFinishedIn(items, 2026).map { it.finishedOn })
    }

    @Test
    fun `pace compares with an even rate through the year`() {
        val midYear = LocalDateTime.of(2026, 7, 2, 12, 0) // Half of 2026 has passed.
        assertEquals("On track", goalPace(25, 50, midYear))
        assertEquals("3 books ahead of schedule", goalPace(28, 50, midYear))
        assertEquals("1 book behind schedule", goalPace(24, 50, midYear))
        assertEquals("Goal reached", goalPace(50, 50, midYear))
        assertEquals("Goal reached", goalPace(60, 50, LocalDateTime.of(2026, 1, 1, 0, 0)))
    }

    @Test
    fun `the year adds up reads, pages, ratings, and months`() {
        val items = listOf(
            item("Twice", listOf("2026-09-01", "2026-02-10"), rating = 5.0, pages = 300),
            item("Unrated", listOf("2026-09-20"), pages = null),
            item("Rated", listOf("2026-02-28"), rating = 3.0, pages = 200),
            item("Old", listOf("2024-02-01"), rating = 1.0, pages = 1000),
        )
        val year = yearInBooks(items, 2026)
        assertEquals(4, year.finished)
        assertEquals(800, year.pages)
        // Each book's rating once, however many times it was read.
        assertEquals(4.0, year.averageRating!!, 0.0)
        assertEquals(listOf("Twice", "Rated"), year.months[1])
        assertEquals(listOf("Twice", "Unrated"), year.months[8])
        assertEquals(emptyList<String>(), year.months[0])
    }

    @Test
    fun `no rating until a finished book has one`() {
        assertNull(yearInBooks(listOf(item("Unrated", listOf("2026-01-05"))), 2026).averageRating)
        assertEquals(0, yearInBooks(emptyList(), 2026).finished)
    }
}
