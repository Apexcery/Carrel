package uk.co.zenithal.carrel.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.zenithal.carrel.data.Grouping
import uk.co.zenithal.carrel.data.LibraryBook
import uk.co.zenithal.carrel.data.LibraryEntry
import uk.co.zenithal.carrel.data.LibraryItem
import uk.co.zenithal.carrel.data.PhoneBook
import uk.co.zenithal.carrel.data.ReadingStatus
import uk.co.zenithal.carrel.data.grouped
import java.time.Instant

class ResumeTest {
    private val openedAt = Instant.parse("2026-10-08T12:00:00Z").toEpochMilli()

    private fun book(id: Long, progression: Double? = 0.4, bookId: Long? = 9, title: String = "Book $id", authors: String = "", series: String? = null, position: Double? = null) =
        PhoneBook(id, "$id.epub", null, "f$id", title, authors, series, position, "", bookId, null, null, progression, 0, openedAt)

    private fun library(status: ReadingStatus, updatedAt: String) = listOf(
        LibraryItem(
            LibraryEntry(1, 9, status, null, null, null, null, null, null, "2026-01-01T00:00:00+00:00", updatedAt, emptyList()),
            LibraryBook(9, null, "Book", emptyList(), null, null, null, null, null, null),
        ),
    )

    @Test
    fun carriesOnWithTheLastBookOpened() {
        val last = book(2)
        assertEquals(last, bookToResume(2, listOf(book(1), last), library(ReadingStatus.Reading, "2026-10-08T11:00:00+00:00")))
    }

    @Test
    fun opensTheDashboardWhenThereIsNoneOrItWasRemoved() {
        assertNull(bookToResume(null, listOf(book(1)), null))
        assertNull(bookToResume(3, listOf(book(1), book(2)), null))
    }

    @Test
    fun opensTheDashboardOnceTheBookIsFinished() {
        assertNull(bookToResume(1, listOf(book(1, progression = 0.99)), null))
        // Marked as read in Carrel (on the website, say) after it was last read.
        assertNull(bookToResume(1, listOf(book(1)), library(ReadingStatus.Read, "2026-10-08T13:00:00+00:00")))
    }

    @Test
    fun carriesOnRereadingABookAlreadyRead() {
        val book = book(1)
        assertEquals(book, bookToResume(1, listOf(book), library(ReadingStatus.Read, "2026-09-01T10:00:00+00:00")))
    }

    @Test
    fun signedOutOnlyThePlaceInTheFileCounts() {
        val book = book(1)
        assertEquals(book, bookToResume(1, listOf(book), null))
    }

    @Test
    fun groupsByAuthorAndSeries() {
        val books = listOf(
            book(1, title = "Mistborn", authors = "Brandon Sanderson", series = "Mistborn", position = 1.0),
            book(2, title = "The Well of Ascension", authors = "Brandon Sanderson", series = "Mistborn", position = 2.0),
            book(3, title = "Emma", authors = "Jane Austen"),
            book(4, title = "anonymous"),
        )
        assertEquals(
            listOf("Brandon Sanderson" to listOf(1L, 2L), "Jane Austen" to listOf(3L), "Unknown author" to listOf(4L)),
            grouped(books, Grouping.Author).map { (heading, group) -> heading to group.map { it.id } },
        )
        assertEquals(
            listOf("Mistborn" to listOf(1L, 2L), "Not in a series" to listOf(4L, 3L)),
            grouped(books.reversed(), Grouping.Series).map { (heading, group) -> heading to group.map { it.id } },
        )
        assertEquals(
            listOf("A" to listOf(4L), "E" to listOf(3L), "M" to listOf(1L), "T" to listOf(2L)),
            grouped(books, Grouping.Title).map { (heading, group) -> heading to group.map { it.id } },
        )
    }
}
