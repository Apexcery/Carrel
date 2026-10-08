package uk.co.zenithal.carrel.reader

import uk.co.zenithal.carrel.data.LibraryItem
import uk.co.zenithal.carrel.data.PhoneBook
import uk.co.zenithal.carrel.data.ReadingStatus
import java.time.OffsetDateTime

/**
 * The book the Home button carries on with: the last one opened, unless it's been removed or finished (read to the end,
 * or marked as read in Carrel since it was last read). Null opens the reading dashboard instead. `library` is the
 * signed-in reader's, null when signed out, when only the place in the file counts.
 */
fun bookToResume(lastOpened: Long?, books: List<PhoneBook>, library: List<LibraryItem>?): PhoneBook? {
    val book = books.firstOrNull { it.id == lastOpened } ?: return null
    if ((book.progression ?: 0.0) >= FINISHED_AT) return null
    val entry = book.bookId?.let { id -> library?.firstOrNull { it.book.id == id }?.entry }
    val lastRead = book.openedAt
    // A book already read and being read again was opened since, so it carries on.
    val markedRead = entry?.status == ReadingStatus.Read && lastRead != null &&
        OffsetDateTime.parse(entry.updatedAt).toInstant().toEpochMilli() > lastRead
    return book.takeUnless { markedRead }
}
