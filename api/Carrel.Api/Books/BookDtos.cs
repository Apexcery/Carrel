using Carrel.Api.Data;

namespace Carrel.Api.Books;

public record BookSearchResponse(int Found, BookSearchResult[] Results);

/// <summary>
/// A search hit straight from Hardcover, or Open Library when Hardcover has nothing; not stored until someone
/// opens the book. Exactly one of HardcoverId and OpenLibraryWorkId is set.
/// </summary>
public record BookSearchResult(
    int? HardcoverId,
    string? OpenLibraryWorkId,
    string Title,
    string? Subtitle,
    string[] Authors,
    int? ReleaseYear,
    string? CoverUrl,
    decimal? HardcoverRating,
    int? HardcoverRatingsCount,
    int? SeriesHardcoverId,
    string? SeriesName,
    decimal? SeriesPosition);

/// <summary>A series as listed by Hardcover, cleaned up; its books aren't stored until someone opens one.</summary>
public record SeriesDetail(
    int HardcoverId,
    string Name,
    string? Author,
    bool? IsCompleted,
    SeriesBook[] Books,
    SeriesBook[] OtherBooks);

public record SeriesBook(
    int HardcoverId,
    decimal? Position,
    string Title,
    string[] Authors,
    int? ReleaseYear,
    string? CoverUrl,
    decimal? HardcoverRating,
    int? HardcoverRatingsCount);

public record BookDetail(
    long Id,
    long? HardcoverId,
    string Title,
    string? Subtitle,
    string? Description,
    DescriptionSource? DescriptionSource,
    // Where the description came from, for sources that ask to be linked to (Google Books).
    string? DescriptionUrl,
    string? CoverUrl,
    int? FirstPublishedYear,
    decimal? HardcoverRating,
    int? HardcoverRatingsCount,
    ContributorDto[] Authors,
    SeriesEntryDto[] Series,
    string[] Genres,
    EditionDto[] Editions);

/// <summary>A suggested book from Hardcover; not stored until someone opens it.</summary>
public record BookSuggestion(
    int HardcoverId,
    string Title,
    string[] Authors,
    int? ReleaseYear,
    string? CoverUrl,
    string? SeriesName,
    decimal? SeriesPosition);

/// <summary>Suggestions for a book's page: books like it, and more by its first author (named in Author).</summary>
public record RelatedBooks(BookSuggestion[] Similar, string? Author, BookSuggestion[] ByAuthor);

/// <summary>The Discover shelves on the home page, the same for every reader.</summary>
public record DiscoverShelves(
    BookSuggestion[] Popular,
    BookSuggestion[] NewReleases,
    BookSuggestion[] ComingSoon,
    BookSuggestion[] TopRated);

/// <summary>Popular books in the reader's top genre; Genre is null until their books have one.</summary>
public record GenrePicks(string? Genre, BookSuggestion[] Books);

public record GenreLink(string Name, string Slug);

/// <summary>
/// The genres listed for browsing, in two groups, and the rest of Hardcover's well-used genres (most used first) for
/// searching; Other is empty while Hardcover can't be reached.
/// </summary>
public record GenreIndex(GenreLink[] Fiction, GenreLink[] Nonfiction, GenreLink[] Other);

/// <summary>A genre's page: its most read books, its best rated, and its popular new releases.</summary>
public record GenreShelves(string Name, BookSuggestion[] Popular, BookSuggestion[] TopRated, BookSuggestion[] NewReleases);

public record ContributorDto(long Id, string Name, string Role);

public record SeriesEntryDto(long Id, long? HardcoverId, string Name, decimal? Position);

public record EditionDto(
    long Id,
    string? Isbn13,
    string? Isbn10,
    EditionFormat? Format,
    int? PageCount,
    int? AudioSeconds,
    string? Publisher,
    DateOnly? ReleaseDate,
    string? Language,
    string? CoverUrl);
