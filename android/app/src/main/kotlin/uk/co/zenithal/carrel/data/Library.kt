package uk.co.zenithal.carrel.data

import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate

// Shapes returned by the library endpoints (see api/Carrel.Api/Library/LibraryDtos.cs and web/src/types.ts).

/** A shelf. `slug` is its address on the website, e.g. /@reader/shelves/want-to-read. */
@Serializable
enum class ReadingStatus(val label: String, val slug: String) {
    @SerialName("want_to_read") WantToRead("Want to read", "want-to-read"),
    @SerialName("reading") Reading("Reading", "reading"),
    @SerialName("paused") Paused("Paused", "paused"),
    @SerialName("read") Read("Read", "read"),
    @SerialName("did_not_finish") DidNotFinish("Did not finish", "did-not-finish");

    /** Started but not finished: these show and edit progress. */
    val inProgress get() = this == Reading || this == Paused

    companion object {
        fun fromSlug(slug: String) = entries.firstOrNull { it.slug == slug }
    }
}

@Serializable
enum class ProgressUnit {
    @SerialName("page") Page,
    @SerialName("percent") Percent,
    @SerialName("seconds") Seconds,
}

@Serializable
data class LibraryEntry(
    val id: Long,
    val bookId: Long,
    val status: ReadingStatus,
    val rating: Double?,
    val editionId: Long?,
    val progressUnit: ProgressUnit?,
    val progressValue: Double?,
    val progressPercent: Double?,
    /** Pages or seconds in the edition used for progress, when known. */
    val progressTotal: Int?,
    val addedAt: String,
    val updatedAt: String,
    /** Newest first. */
    val reads: List<Read>,
)

@Serializable
data class Read(
    val id: Long,
    val startedOn: String?,
    val finishedOn: String?,
    /** Finished, but when isn't known; never set alongside finishedOn. */
    val finishedDateUnknown: Boolean,
)

@Serializable
data class SaveEntryRequest(
    val status: ReadingStatus,
    val editionId: Long?,
    val rating: Double?,
    val progressUnit: ProgressUnit?,
    val progressValue: Double?,
    /** The reader's own date, for automatic read dates (the server's may be a different day). */
    val today: String,
    /**
     * When given, replaces the reading history: reads with an id are updated, without one added, missing ones deleted.
     * Left out (null), the history stays as it is; an empty list deletes it.
     */
    val reads: List<ReadInput>? = null,
    /**
     * When a change saved on the phone while offline was made. The API turns it away if the entry has changed or been
     * removed since (the newer change wins).
     */
    val changedAt: String? = null,
)

@Serializable
data class ReadInput(val id: Long?, val startedOn: String?, val finishedOn: String?, val finishedDateUnknown: Boolean)

/** A library entry with enough of its book to show on a shelf. */
@Serializable
data class LibraryItem(val entry: LibraryEntry, val book: LibraryBook)

@Serializable
data class LibraryBook(
    val id: Long,
    val hardcoverId: Long?,
    val title: String,
    val authors: List<String>,
    val coverUrl: String?,
    val series: SeriesEntry?,
    val firstPublishedYear: Int?,
    val hardcoverRating: Double?,
    val hardcoverRatingsCount: Int?,
    /** From the reader's edition, else the first edition with one. */
    val pageCount: Int?,
)

val LibrarySerializer = ListSerializer(LibraryItem.serializer())

/** The entry as a request that keeps everything as it is, to change a part of: PUT replaces the whole entry. */
fun LibraryEntry?.toRequest() = SaveEntryRequest(
    status = this?.status ?: ReadingStatus.WantToRead,
    editionId = this?.editionId,
    rating = this?.rating,
    progressUnit = this?.progressUnit,
    progressValue = this?.progressValue,
    today = LocalDate.now().toString(),
)

/** A book as the library list has it, for adding a newly shelved book to the saved list. */
fun BookDetail.toLibraryBook(editionId: Long?) = LibraryBook(
    id = id,
    hardcoverId = hardcoverId?.toLong(),
    title = title,
    authors = authors.filter { it.role == "author" }.map { it.name },
    coverUrl = coverUrl,
    series = series.firstOrNull(),
    firstPublishedYear = firstPublishedYear,
    hardcoverRating = hardcoverRating,
    hardcoverRatingsCount = hardcoverRatingsCount,
    pageCount = editions.firstOrNull { it.id == editionId }?.pageCount ?: editions.firstNotNullOfOrNull { it.pageCount },
)

/**
 * The book's page as far as the saved library knows it, to show without a signal: no description, genres, or
 * editions. Its authors' ids are made up, since nothing uses them.
 */
fun LibraryBook.toBookDetail() = BookDetail(
    id = id,
    hardcoverId = hardcoverId?.toInt(),
    title = title,
    subtitle = null,
    description = null,
    descriptionSource = null,
    coverUrl = coverUrl,
    firstPublishedYear = firstPublishedYear,
    hardcoverRating = hardcoverRating,
    hardcoverRatingsCount = hardcoverRatingsCount,
    authors = authors.mapIndexed { index, name -> Contributor(-index - 1L, name, "author") },
    series = listOfNotNull(series),
    genres = emptyList(),
    editions = emptyList(),
)

/**
 * The entry as the API saves it (LibraryService.SaveAsync), for a change made on the phone while offline, so it shows
 * before it syncs. Reads added on the phone get made-up negative ids until then. `pageCount` is the book's, for
 * progress in pages.
 */
fun LibraryEntry.applied(request: SaveEntryRequest, changedAt: String, pageCount: Int?): LibraryEntry {
    var nextId = minOf(0L, reads.minOfOrNull { it.id } ?: 0L) - 1
    val newId = { nextId-- }
    var history = request.reads?.map { Read(it.id ?: newId(), it.startedOn, it.finishedOn, it.finishedDateUnknown && it.finishedOn == null) } ?: reads
    // Read dates follow the status, as LibraryService.ApplyStatusChange keeps them.
    if (request.status != status) {
        val open = history.firstOrNull { it.finishedOn == null && !it.finishedDateUnknown }
        history = when {
            request.status == ReadingStatus.Reading && open == null -> listOf(Read(newId(), request.today, null, false)) + history
            request.status == ReadingStatus.Read && open != null ->
                history.map { if (it == open) it.copy(finishedOn = maxOf(it.startedOn ?: request.today, request.today)) else it }
            request.status == ReadingStatus.Read -> listOf(Read(newId(), null, request.today, false)) + history
            else -> history
        }
    }
    val total = when {
        request.progressUnit == progressUnit -> progressTotal
        request.progressUnit == ProgressUnit.Page -> pageCount
        else -> null
    }
    val value = request.progressValue
    val percent = when {
        request.status == ReadingStatus.Read -> 100.0
        value == null -> null
        request.progressUnit == ProgressUnit.Percent -> value
        total != null && total > 0 -> minOf(100.0, Math.round(value / total * 10_000) / 100.0)
        else -> null
    }
    return copy(
        status = request.status,
        rating = request.rating,
        editionId = request.editionId,
        progressUnit = request.progressUnit,
        progressValue = value,
        progressPercent = percent,
        progressTotal = total,
        updatedAt = changedAt,
        reads = history,
    )
}

/**
 * Saves changes to the reader's library, then puts the result into the saved library list, so every screen showing
 * it (the book's panel, the Library tab, and shelves) changes straight away. Without a signal, a change to a book
 * already in the library is kept on the phone (see Outbox) and shows as if saved; so is any change to a book with a
 * change still waiting, so they reach the API in order.
 */
class LibraryChanges(private val api: ApiClient, private val store: Store, private val outbox: Outbox) {

    suspend fun save(book: BookDetail, request: SaveEntryRequest): LibraryEntry {
        if (!outbox.isWaiting(book.id)) {
            try {
                val entry = api.send(HttpMethod.Put, "/library/books/${book.id}", request, SaveEntryRequest.serializer(), LibraryEntry.serializer())
                store.update(LIBRARY_PATH, LibrarySerializer) { items ->
                    if (items.any { it.book.id == book.id }) {
                        items.map { if (it.book.id == book.id) it.copy(entry = entry) else it }
                    } else {
                        items + LibraryItem(entry, book.toLibraryBook(entry.editionId))
                    }
                }
                return entry
            } catch (e: ApiException) {
                if (e.status != 0) throw e
                // Adding a book still needs a signal.
                savedItem(book.id) ?: throw e
            }
        }
        val item = savedItem(book.id) ?: throw ApiException(0, ApiClient.CANT_CONNECT)
        val changedAt = Instant.now().toString()
        val entry = item.entry.applied(request, changedAt, item.book.pageCount)
        store.update(LIBRARY_PATH, LibrarySerializer) { items -> items.map { if (it.book.id == book.id) it.copy(entry = entry) else it } }
        // Reads added on the phone have no id yet, as far as the API knows.
        outbox.add(book.id, request.copy(changedAt = changedAt, reads = request.reads?.map { if ((it.id ?: 0) < 0) it.copy(id = null) else it }))
        return entry
    }

    suspend fun remove(bookId: Long) {
        if (!outbox.isWaiting(bookId)) {
            try {
                api.delete("/library/books/$bookId")
                store.update(LIBRARY_PATH, LibrarySerializer) { items -> items.filter { it.book.id != bookId } }
                return
            } catch (e: ApiException) {
                if (e.status != 0) throw e
            }
        }
        outbox.add(bookId, null)
        store.update(LIBRARY_PATH, LibrarySerializer) { items -> items.filter { it.book.id != bookId } }
    }

    private suspend fun savedItem(bookId: Long) = store.saved(LIBRARY_PATH, LibrarySerializer).first()?.firstOrNull { it.book.id == bookId }
}
