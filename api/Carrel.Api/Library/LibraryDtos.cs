using Carrel.Api.Books;
using Carrel.Api.Data;

namespace Carrel.Api.Library;

public record LibraryEntryDto(
    long Id,
    long BookId,
    ReadingStatus Status,
    decimal? Rating,
    long? EditionId,
    ProgressUnit? ProgressUnit,
    decimal? ProgressValue,
    decimal? ProgressPercent,
    int? ProgressTotal, // Pages or seconds in the edition used for progress, when known.
    DateTimeOffset AddedAt,
    DateTimeOffset UpdatedAt,
    ReadDto[] Reads);

public record ReadDto(long Id, DateOnly? StartedOn, DateOnly? FinishedOn, bool FinishedDateUnknown);

/// <summary>A library entry with enough of its book to show on a shelf.</summary>
public record LibraryItemDto(LibraryEntryDto Entry, LibraryBookDto Book);

public record LibraryBookDto(
    long Id,
    long? HardcoverId,
    string Title,
    string[] Authors,
    string? CoverUrl,
    SeriesEntryDto? Series,
    int? FirstPublishedYear,
    decimal? HardcoverRating,
    int? HardcoverRatingsCount,
    int? PageCount);

/// <summary>
/// The entry's desired state. <paramref name="Today"/> is the reader's local date, used for automatic read dates
/// (servers run on UTC, which may be a different day). <paramref name="Reads"/>, when given, replaces the reading
/// history: reads with an id are updated, reads without one are added, and reads left out are deleted.
/// <paramref name="ChangedAt"/> is when the change was made, for one the app saved while offline and sends later: it's
/// turned away if the entry has changed or been removed since (the newer change wins), and becomes the entry's last
/// update.
/// </summary>
public record SaveEntryRequest(
    ReadingStatus Status,
    long? EditionId,
    decimal? Rating,
    ProgressUnit? ProgressUnit,
    decimal? ProgressValue,
    DateOnly? Today,
    ReadInput[]? Reads = null,
    DateTimeOffset? ChangedAt = null);

/// <summary>Where the reader is in a book they're reading in the app: a Readium Locator (JSON) and how far through, 0 to 1.</summary>
public record ReadingPositionDto(string Locator, decimal Progression, DateTimeOffset UpdatedAt);

/// <summary>
/// Saves where the reader is. <paramref name="ChangedAt"/> is when they were there, by the device's clock; a save older
/// than the position already saved is turned away (another device was read more recently).
/// </summary>
public record SavePositionRequest(string Locator, decimal Progression, DateTimeOffset ChangedAt);

/// <param name="FinishedDateUnknown">Finished on a date that isn't known; ignored when <paramref name="FinishedOn"/> is set.</param>
public record ReadInput(long? Id, DateOnly? StartedOn, DateOnly? FinishedOn, bool FinishedDateUnknown = false);
