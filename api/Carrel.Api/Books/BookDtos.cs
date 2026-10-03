using Carrel.Api.Data;

namespace Carrel.Api.Books;

public record BookSearchResponse(int Found, BookSearchResult[] Results);

/// <summary>A search hit straight from Hardcover; not stored until someone opens the book.</summary>
public record BookSearchResult(
    int HardcoverId,
    string Title,
    string? Subtitle,
    string[] Authors,
    int? ReleaseYear,
    string? CoverUrl,
    decimal? HardcoverRating,
    int? HardcoverRatingsCount,
    string? SeriesName,
    decimal? SeriesPosition);

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

public record SeriesEntryDto(long Id, string Name, decimal? Position);

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
