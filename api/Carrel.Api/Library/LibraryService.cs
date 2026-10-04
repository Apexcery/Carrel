using Carrel.Api.Books;
using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Library;

/// <summary>Thrown for requests that are well-formed but not acceptable; the message is shown to the user.</summary>
public class LibraryValidationException(string field, string message) : Exception(message)
{
    public string Field { get; } = field;
}

/// <summary>A user's library. Every query is scoped to the signed-in user's id.</summary>
public class LibraryService(CarrelDbContext db)
{
    public async Task<LibraryItemDto[]> ListAsync(Guid userId, CancellationToken ct)
    {
        var entries = await db.LibraryEntries
            .Where(e => e.UserId == userId)
            .Include(e => e.Reads)
            .Include(e => e.Edition)
            .Include(e => e.Book).ThenInclude(b => b.Authors).ThenInclude(ba => ba.Author)
            .Include(e => e.Book).ThenInclude(b => b.Series).ThenInclude(bs => bs.Series)
            .Include(e => e.Book).ThenInclude(b => b.Editions)
            .AsSplitQuery()
            .OrderByDescending(e => e.UpdatedAt)
            .ToListAsync(ct);
        return entries.Select(e => new LibraryItemDto(ToDto(e), ToBook(e.Book))).ToArray();
    }

    public async Task<LibraryEntryDto?> GetAsync(Guid userId, long bookId, CancellationToken ct) =>
        await LoadAsync(userId, bookId, ct) is { } entry ? ToDto(entry) : null;

    /// <summary>Creates or updates the entry; null if the book doesn't exist.</summary>
    public async Task<LibraryEntryDto?> SaveAsync(Guid userId, long bookId, SaveEntryRequest request, CancellationToken ct)
    {
        var book = await db.Books.Include(b => b.Editions).FirstOrDefaultAsync(b => b.Id == bookId, ct);
        if (book is null)
        {
            return null;
        }
        Validate(request, book);

        var entry = await LoadAsync(userId, bookId, ct);
        var now = DateTimeOffset.UtcNow;
        if (entry is null)
        {
            entry = new LibraryEntry { UserId = userId, BookId = bookId, Book = book, AddedAt = now };
            db.LibraryEntries.Add(entry);
        }

        if (request.Reads is not null)
        {
            SyncReads(entry, request.Reads);
        }
        var today = request.Today ?? DateOnly.FromDateTime(now.UtcDateTime);
        ApplyStatusChange(entry, request.Status, today);

        entry.Status = request.Status;
        entry.EditionId = request.EditionId;
        entry.Edition = request.EditionId is { } editionId ? book.Editions.First(e => e.Id == editionId) : null;
        entry.Rating = request.Rating;
        entry.ProgressUnit = request.ProgressUnit;
        entry.ProgressValue = request.ProgressValue;
        if (request.Status == ReadingStatus.Read)
        {
            entry.ProgressPercent = 100;
        }
        else
        {
            var total = ProgressTotal(entry);
            entry.ProgressPercent = (entry.ProgressUnit, entry.ProgressValue, total) switch
            {
                (ProgressUnit.Percent, { } value, _) => value,
                (_, { } value, > 0) => Math.Min(100, Math.Round(value / total.Value * 100, 2)),
                _ => null,
            };
        }
        entry.UpdatedAt = now;

        await db.SaveChangesAsync(ct);
        return ToDto(entry);
    }

    public async Task<bool> DeleteAsync(Guid userId, long bookId, CancellationToken ct) =>
        await db.LibraryEntries.Where(e => e.UserId == userId && e.BookId == bookId).ExecuteDeleteAsync(ct) > 0;

    /// <summary>
    /// Keeps read dates in step with status: starting to read opens a read dated today; finishing closes the open
    /// read today (or records a read finished today if there was none). Other changes leave reads alone.
    /// </summary>
    private static void ApplyStatusChange(LibraryEntry entry, ReadingStatus newStatus, DateOnly today)
    {
        if (entry.Id != 0 && entry.Status == newStatus)
        {
            return;
        }
        var openRead = entry.Reads.Where(r => r.FinishedOn is null).OrderByDescending(r => r.Id).FirstOrDefault();
        switch (newStatus)
        {
            case ReadingStatus.Reading when openRead is null:
                entry.Reads.Add(new Read { StartedOn = today });
                break;
            case ReadingStatus.Read when openRead is not null:
                openRead.FinishedOn = openRead.StartedOn > today ? openRead.StartedOn : today;
                break;
            case ReadingStatus.Read:
                entry.Reads.Add(new Read { FinishedOn = today });
                break;
        }
    }

    private static void SyncReads(LibraryEntry entry, ReadInput[] reads)
    {
        if (reads.Any(r => r.Id is { } id && entry.Reads.All(existing => existing.Id != id)))
        {
            throw new LibraryValidationException("reads", "One of those reads doesn't belong to this book.");
        }
        entry.Reads.RemoveAll(existing => reads.All(r => r.Id != existing.Id));
        foreach (var input in reads)
        {
            var read = entry.Reads.FirstOrDefault(r => input.Id is not null && r.Id == input.Id);
            if (read is null)
            {
                read = new Read();
                entry.Reads.Add(read);
            }
            read.StartedOn = input.StartedOn;
            read.FinishedOn = input.FinishedOn;
        }
    }

    private static int? ProgressTotal(LibraryEntry entry)
    {
        // The chosen edition, else the first edition with a length in the right unit.
        IEnumerable<Edition> editions = entry.Edition is { } chosen ? [chosen] : entry.Book.Editions.OrderBy(e => e.Id);
        return entry.ProgressUnit switch
        {
            ProgressUnit.Page => editions.Select(e => e.PageCount).FirstOrDefault(p => p > 0),
            ProgressUnit.Seconds => editions.Select(e => e.AudioSeconds).FirstOrDefault(s => s > 0),
            _ => null,
        };
    }

    private static void Validate(SaveEntryRequest request, Book book)
    {
        if (!Enum.IsDefined(request.Status))
        {
            throw new LibraryValidationException("status", "Unknown status.");
        }
        if (request.Rating is { } rating && (rating < 0.5m || rating > 5 || rating * 2 != decimal.Truncate(rating * 2)))
        {
            throw new LibraryValidationException("rating", "Rating must be 0.5 to 5 in half stars.");
        }
        if (request.EditionId is { } editionId && book.Editions.All(e => e.Id != editionId))
        {
            throw new LibraryValidationException("editionId", "That edition isn't one of this book's.");
        }
        if ((request.ProgressUnit is null) != (request.ProgressValue is null))
        {
            throw new LibraryValidationException("progressValue", "Progress needs both a unit and a value.");
        }
        if (request.ProgressValue < 0 || (request.ProgressUnit == ProgressUnit.Percent && request.ProgressValue > 100)
                                      || request.ProgressValue > 1_000_000)
        {
            throw new LibraryValidationException("progressValue", "Progress is out of range.");
        }
        if (request.Reads?.Any(r => r.StartedOn > r.FinishedOn) == true)
        {
            throw new LibraryValidationException("reads", "A read can't finish before it starts.");
        }
    }

    private Task<LibraryEntry?> LoadAsync(Guid userId, long bookId, CancellationToken ct) =>
        db.LibraryEntries
            .Include(e => e.Reads)
            .Include(e => e.Edition)
            .Include(e => e.Book).ThenInclude(b => b.Editions)
            .AsSplitQuery()
            .FirstOrDefaultAsync(e => e.UserId == userId && e.BookId == bookId, ct);

    private static LibraryEntryDto ToDto(LibraryEntry entry) => new(
        entry.Id,
        entry.BookId,
        entry.Status,
        entry.Rating,
        entry.EditionId,
        entry.ProgressUnit,
        entry.ProgressValue,
        entry.ProgressPercent,
        ProgressTotal(entry),
        entry.AddedAt,
        entry.UpdatedAt,
        entry.Reads
            .OrderByDescending(r => r.FinishedOn ?? DateOnly.MaxValue)
            .ThenByDescending(r => r.StartedOn)
            .Select(r => new ReadDto(r.Id, r.StartedOn, r.FinishedOn))
            .ToArray());

    private static LibraryBookDto ToBook(Book book)
    {
        var series = book.Series
            .OrderByDescending(bs => bs.IsFeatured)
            .ThenBy(bs => bs.Position ?? decimal.MaxValue)
            .Select(bs => new SeriesEntryDto(bs.Series.Id, bs.Series.HardcoverId, bs.Series.Name, bs.Position))
            .FirstOrDefault();
        return new LibraryBookDto(
            book.Id,
            book.Title,
            book.Authors.Where(ba => ba.Role == "author").OrderBy(ba => ba.Position).Select(ba => ba.Author.Name).ToArray(),
            book.CoverSuppressed ? null : book.CoverUrl,
            series,
            book.FirstPublishedYear,
            book.HardcoverRating,
            book.HardcoverRatingsCount);
    }
}
