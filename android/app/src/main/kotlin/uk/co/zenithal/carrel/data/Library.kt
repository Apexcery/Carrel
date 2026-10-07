package uk.co.zenithal.carrel.data

import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
 * Saves changes to the reader's library, then puts the result into the saved library list, so every screen showing
 * it (the book's panel, the Library tab, and shelves) changes straight away.
 */
class LibraryChanges(private val api: ApiClient, private val store: Store) {

    suspend fun save(book: BookDetail, request: SaveEntryRequest): LibraryEntry {
        val entry = api.send(HttpMethod.Put, "/library/books/${book.id}", request, SaveEntryRequest.serializer(), LibraryEntry.serializer())
        store.update(LIBRARY_PATH, LibrarySerializer) { items ->
            if (items.any { it.book.id == book.id }) {
                items.map { if (it.book.id == book.id) it.copy(entry = entry) else it }
            } else {
                items + LibraryItem(entry, book.toLibraryBook(entry.editionId))
            }
        }
        return entry
    }

    suspend fun remove(bookId: Long) {
        api.delete("/library/books/$bookId")
        store.update(LIBRARY_PATH, LibrarySerializer) { items -> items.filter { it.book.id != bookId } }
    }
}
