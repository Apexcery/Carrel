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
    string? CoverUrl,
    int? FirstPublishedYear,
    decimal? HardcoverRating,
    int? HardcoverRatingsCount,
    ContributorDto[] Authors,
    SeriesEntryDto[] Series,
    string[] Genres,
    EditionDto[] Editions);

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
