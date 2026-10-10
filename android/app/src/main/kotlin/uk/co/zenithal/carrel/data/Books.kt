package uk.co.zenithal.carrel.data

import kotlinx.serialization.Serializable

// Shapes returned by the Carrel API (see api/Carrel.Api/Books/BookDtos.cs and web/src/types.ts).

@Serializable
data class BookSearchResponse(val found: Int, val results: List<BookSearchResult>)

/** A search hit from Hardcover, or Open Library when Hardcover has nothing. Exactly one of the two ids is set. */
@Serializable
data class BookSearchResult(
    val hardcoverId: Int?,
    val openLibraryWorkId: String?,
    val title: String,
    val subtitle: String?,
    val authors: List<String>,
    val releaseYear: Int?,
    val coverUrl: String?,
    val hardcoverRating: Double?,
    val hardcoverRatingsCount: Int?,
    val seriesHardcoverId: Int?,
    val seriesName: String?,
    val seriesPosition: Double?,
) {
    /** Where the book opens: its Hardcover or Open Library id, which stores it on the server. */
    val bookPath get() = if (hardcoverId != null) "/books/hardcover/$hardcoverId" else "/books/openlibrary/$openLibraryWorkId"
}

@Serializable
data class BookDetail(
    val id: Long,
    val hardcoverId: Int?,
    val title: String,
    val subtitle: String?,
    val description: String?,
    /** hardcover, open_library, or google_books. */
    val descriptionSource: String?,
    /** The description's source page, for sources that ask to be linked to (Google Books). */
    val descriptionUrl: String? = null,
    val coverUrl: String?,
    val firstPublishedYear: Int?,
    val hardcoverRating: Double?,
    val hardcoverRatingsCount: Int?,
    val authors: List<Contributor>,
    val series: List<SeriesEntry>,
    val genres: List<String>,
    val editions: List<Edition>,
)

@Serializable
data class Contributor(val id: Long, val name: String, val role: String)

@Serializable
data class SeriesEntry(val id: Long, val hardcoverId: Int?, val name: String, val position: Double?)

@Serializable
data class Edition(
    val id: Long,
    val isbn13: String?,
    val isbn10: String?,
    /** print, ebook, or audio. */
    val format: String?,
    val pageCount: Int?,
    val audioSeconds: Int?,
    val publisher: String?,
    val releaseDate: String?,
    val language: String?,
    val coverUrl: String?,
)

@Serializable
data class SeriesDetail(
    val hardcoverId: Int,
    val name: String,
    val author: String?,
    val isCompleted: Boolean?,
    val books: List<SeriesBook>,
    val otherBooks: List<SeriesBook>,
)

@Serializable
data class SeriesBook(
    val hardcoverId: Int,
    val position: Double?,
    val title: String,
    val authors: List<String>,
    val releaseYear: Int?,
    val coverUrl: String?,
    val hardcoverRating: Double?,
    val hardcoverRatingsCount: Int?,
)

/** A suggested book from Hardcover; opening it stores it. */
@Serializable
data class BookSuggestion(
    val hardcoverId: Int,
    val title: String,
    val authors: List<String>,
    val releaseYear: Int?,
    val coverUrl: String?,
    val seriesName: String?,
    val seriesPosition: Double?,
)

/** Suggestions for a book's page: books like it, and more by its first author (named in author). */
@Serializable
data class RelatedBooks(val similar: List<BookSuggestion>, val author: String?, val byAuthor: List<BookSuggestion>)

@Serializable
data class GenreLink(val name: String, val slug: String)

/** The genres listed for browsing, in two groups, and Hardcover's other well-used genres for searching. */
@Serializable
data class GenreIndex(val fiction: List<GenreLink>, val nonfiction: List<GenreLink>, val other: List<GenreLink>)

/** A genre's page: its most read books, its best rated, and its popular new releases. */
@Serializable
data class GenreShelves(
    val name: String,
    val popular: List<BookSuggestion>,
    val topRated: List<BookSuggestion>,
    val newReleases: List<BookSuggestion>,
)

/** Popular books in the reader's top genre; genre is null until their books have one. */
@Serializable
data class GenrePicks(val genre: String?, val books: List<BookSuggestion>)

/** The Discover tab's shelves, the same for everyone. */
@Serializable
data class DiscoverShelves(
    val popular: List<BookSuggestion>,
    val newReleases: List<BookSuggestion>,
    val comingSoon: List<BookSuggestion>,
    val topRated: List<BookSuggestion>,
)

/** Only the part of a library entry the browsing screens use: which books the reader has, to leave out suggestions. */
@Serializable
data class OwnedBook(val book: OwnedBookIds)

@Serializable
data class OwnedBookIds(val hardcoverId: Long?)

const val LIBRARY_PATH = "/library"
const val GENRES_PATH = "/genres"
