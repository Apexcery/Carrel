package uk.co.zenithal.carrel.reader

import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.time.Instant
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

    /** Where the reader is in this book as Carrel has it, saved from any device; null if it isn't, or there's no signal. */
    suspend fun savedPosition(): ReadingPosition? = try {
        container.api.getOrNull("/library/books/$bookId/position", ReadingPosition.serializer())
    } catch (_: ApiException) {
        null
    }

    /**
     * Saves where the reader is to Carrel, for another device to carry on from; `at` is when they were there. It's
     * turned away if another device was read more recently, and not sent without a signal (the next open sends it).
     */
    suspend fun savePosition(locator: String, progression: Double, at: Instant) {
        try {
            container.api.send(
                HttpMethod.Put,
                "/library/books/$bookId/position",
                SavePositionRequest(locator, progression.coerceIn(0.0, 1.0), at.toString()),
                SavePositionRequest.serializer(),
                ReadingPosition.serializer(),
            )
        } catch (_: ApiException) {
        }
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

/** Where the reader is in a book, as Carrel saves it (GET /library/books/{id}/position): a Readium Locator (JSON). */
@Serializable
data class ReadingPosition(val locator: String, val progression: Double, val updatedAt: String)

@Serializable
data class SavePositionRequest(val locator: String, val progression: Double, val changedAt: String)

/**
 * Whether a place saved in Carrel is worth going to from where this copy is (`localAt`, null if it's never been read
 * here): it's more recent, by more than a moment, and somewhere else in the book.
 */
fun isNewerElsewhere(remote: ReadingPosition, remoteAt: Instant, localAt: Instant?, localProgression: Double?): Boolean {
    if (localAt != null && !remoteAt.isAfter(localAt.plusSeconds(1))) return false
    return localProgression == null || abs(remote.progression - localProgression) >= SAME_PLACE
}

/** Progressions closer than this (about a page in a long book) are the same place. */
const val SAME_PLACE = 0.002

/** A whole percentage read, from Readium's totalProgression (0 to 1). */
fun readPercent(totalProgression: Double) = (totalProgression * 100).roundToInt().coerceIn(0, 100)
