using Carrel.Api.Data;

namespace Carrel.Api.Imports;

/// <summary>
/// An import's progress. <paramref name="Remaining"/> rows are still being matched; <paramref name="ToCheck"/> were
/// matched by title and not checked yet; <paramref name="NotFound"/> matched nothing and haven't been dismissed.
/// </summary>
public record ImportStatusDto(
    long Id,
    ImportSource Source,
    ImportState State,
    bool OverwriteExisting,
    DateTimeOffset CreatedAt,
    DateTimeOffset? FinishedAt,
    int Total,
    int Matched,
    int ToCheck,
    int NotFound,
    int Remaining);

/// <summary>A row to look at, with the book it was matched to by title (none if nothing matched).</summary>
public record ImportReviewItemDto(long Id, int Row, string Title, string[] Authors, ImportMatch Match, ImportBookDto? Book);

/// <summary>A matched book; the series is its featured one, as on book pages.</summary>
public record ImportBookDto(
    long Id,
    string Title,
    string[] Authors,
    string? CoverUrl,
    int? FirstPublishedYear,
    string? SeriesName,
    decimal? SeriesPosition);

/// <summary>The book a reader picked from search results, by whichever id the result has.</summary>
public record ChooseBookRequest(int? HardcoverId, string? OpenLibraryId);
