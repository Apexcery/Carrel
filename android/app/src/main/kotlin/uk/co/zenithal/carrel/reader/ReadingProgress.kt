package uk.co.zenithal.carrel.reader

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.zenithal.carrel.AppContainer
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.BookDetail
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.ProgressUnit
import uk.co.zenithal.carrel.data.ReadingStatus
import uk.co.zenithal.carrel.data.toBookDetail
import uk.co.zenithal.carrel.data.toRequest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What reading a linked book does to the reader's library. Opening it moves it to Reading (adding it if it isn't in the
 * library yet, which needs a signal). The percentage read saves every few percent and on leaving, replacing any page
 * count. It's never marked as read without asking: closing it at the end asks first. Without a signal, changes wait
 * in the outbox like any other.
 */
class ReadingProgress(private val container: AppContainer, private val bookId: Long) {
    private val saving = Mutex()
    /** Only books being read save progress; a finished or abandoned one being looked at again doesn't. */
    private var tracking = false
    /** Where the book opened: nothing saves until the reader moves from it, so opening a new copy can't reset progress. */
    private var openedAt: Int? = null
    private var percent: Int? = null
    private var savedPercent: Int? = null
    private var progression: Double? = null

    suspend fun start() = saving.withLock {
        val item = savedItem()
        try {
            when {
                item == null -> {
                    val book = container.api.get("/books/$bookId", BookDetail.serializer())
                    container.library.save(book, null.toRequest().copy(status = ReadingStatus.Reading))
                }
                item.entry.status == ReadingStatus.WantToRead || item.entry.status == ReadingStatus.Paused ->
                    container.library.save(item.book.toBookDetail(), item.entry.toRequest().copy(status = ReadingStatus.Reading))
            }
        } catch (_: ApiException) {
            // Most likely no signal, for a book not in the library yet: it's read without being tracked.
        }
        val entry = savedItem()?.entry
        tracking = entry?.status == ReadingStatus.Reading
        savedPercent = entry?.takeIf { it.progressUnit == ProgressUnit.Percent }?.progressValue?.roundToInt()
    }

    /**
     * How far through the library says the book is, for a copy never opened before to start at (e.g. after reading it in
     * another app); null to start at the beginning.
     */
    suspend fun startingPercent(): Double? =
        savedItem()?.entry?.takeIf { it.status.inProgress }?.progressPercent?.takeIf { it > 0 && it < 100 }

    /** Where the reader has got to (Readium's totalProgression, 0 to 1); true when it's time to save. */
    fun moved(totalProgression: Double?): Boolean {
        if (totalProgression == null) return false
        progression = totalProgression
        val now = readPercent(totalProgression)
        if (openedAt == null) openedAt = now
        if (percent == null && now == openedAt) return false
        percent = now
        val last = savedPercent
        return tracking && (last == null || abs(now - last) >= SAVE_EVERY)
    }

    /** Saves the percentage, if it's changed since the last save. */
    suspend fun save() = saving.withLock {
        val now = percent ?: return@withLock
        if (!tracking || now == savedPercent) return@withLock
        val item = savedItem()?.takeIf { it.entry.status == ReadingStatus.Reading } ?: return@withLock
        try {
            container.library.save(item.book.toBookDetail(), item.entry.toRequest().copy(progressUnit = ProgressUnit.Percent, progressValue = now.toDouble()))
            savedPercent = now
        } catch (_: ApiException) {
            // Tried again at the next save.
        }
    }

    /** Whether closing should ask to mark the book as read: it's at the end, in the library, and not read yet. */
    suspend fun atEnd(): Boolean {
        val entry = savedItem()?.entry ?: return false
        return (progression ?: 0.0) >= FINISHED_AT && entry.status != ReadingStatus.Read
    }

    suspend fun markRead() = saving.withLock {
        val item = savedItem() ?: return@withLock
        container.library.save(item.book.toBookDetail(), item.entry.toRequest().copy(status = ReadingStatus.Read))
        tracking = false
    }

    private suspend fun savedItem() =
        container.store.saved(LIBRARY_PATH, LibrarySerializer).first()?.firstOrNull { it.book.id == bookId }

    private companion object {
        /** Percent read between saves while reading. */
        const val SAVE_EVERY = 5
    }
}

/** How far through (Readium's totalProgression) counts as the end, since the last page rarely reaches 1. */
const val FINISHED_AT = 0.98

/** A whole percentage read, from Readium's totalProgression (0 to 1). */
fun readPercent(totalProgression: Double) = (totalProgression * 100).roundToInt().coerceIn(0, 100)
