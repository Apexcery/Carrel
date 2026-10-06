using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Imports;

/// <summary>
/// Writes matched rows to the reader's library: one entry per book, so rows for the same book (two editions, or the
/// same book in both exports) are combined. Reads and dates come straight from the export, unlike saves from the book
/// page, which add a read finished today.
/// </summary>
public static class ImportWriter
{
    // When rows for the same book disagree, the furthest along wins: being read beats finished beats abandoned.
    private static readonly ReadingStatus[] Precedence =
        [ReadingStatus.Reading, ReadingStatus.Paused, ReadingStatus.Read, ReadingStatus.DidNotFinish, ReadingStatus.WantToRead];

    /// <summary>Writes each book's rows and marks them written; the caller saves, so the two are saved together.</summary>
    public static async Task WriteAsync(CarrelDbContext db, LibraryImport import, IReadOnlyCollection<IGrouping<long, ImportItem>> books,
        CancellationToken ct)
    {
        var bookIds = books.Select(b => b.Key).ToList();
        var entries = await db.LibraryEntries
            .Include(e => e.Reads)
            .Where(e => e.UserId == import.UserId && bookIds.Contains(e.BookId))
            .ToDictionaryAsync(e => e.BookId, ct);
        // Editions named by Carrel exports, by book and Hardcover edition id.
        var hardcoverEditionIds = books.SelectMany(b => b).Select(i => i.HardcoverEditionId).OfType<long>().Distinct().ToList();
        var editions = await db.Editions
            .Where(e => bookIds.Contains(e.BookId) && e.HardcoverEditionId != null && hardcoverEditionIds.Contains(e.HardcoverEditionId.Value))
            .ToDictionaryAsync(e => (e.BookId, e.HardcoverEditionId!.Value), e => e.Id, ct);
        // Entries this import already created (another row for the same book, written earlier) take more rows in.
        var created = await db.ImportItems
            .Where(i => i.ImportId == import.Id && i.CreatedEntry && i.BookId != null && bookIds.Contains(i.BookId.Value))
            .Select(i => i.BookId!.Value)
            .Distinct()
            .ToListAsync(ct);

        foreach (var book in books)
        {
            var rows = book.ToList();
            if (!entries.TryGetValue(book.Key, out var entry))
            {
                entry = new LibraryEntry { UserId = import.UserId, BookId = book.Key };
                db.LibraryEntries.Add(entry);
                Replace(entry, rows, editions);
                rows.ForEach(r => r.CreatedEntry = true);
            }
            else if (created.Contains(book.Key))
            {
                Merge(entry, rows, editions);
                rows.ForEach(r => r.CreatedEntry = true);
            }
            else if (import.OverwriteExisting)
            {
                Replace(entry, rows, editions);
            }
            // Otherwise the book was already in the library and the reader chose to leave those alone.
            rows.ForEach(r => r.Written = true);
        }
    }

    private static void Replace(LibraryEntry entry, List<ImportItem> rows, Dictionary<(long, long), long> editions)
    {
        entry.Status = Furthest(rows.Select(r => r.Status));
        entry.Rating = rows.Max(r => r.Rating);
        entry.Reads.Clear();
        foreach (var read in CombineReads(entry.Status, rows.SelectMany(r => r.Reads)))
        {
            entry.Reads.Add(read);
        }
        entry.AddedAt = Earliest(rows) ?? DateTimeOffset.UtcNow;
        RestoreProgress(entry, rows, editions);
        ApplyProgress(entry);
        entry.UpdatedAt = LastActivity(entry);
    }

    private static void Merge(LibraryEntry entry, List<ImportItem> rows, Dictionary<(long, long), long> editions)
    {
        entry.Status = Furthest(rows.Select(r => r.Status).Append(entry.Status));
        entry.Rating ??= rows.Max(r => r.Rating);
        var existing = entry.Reads.Select(r => new ImportedRead(r.StartedOn, r.FinishedOn, r.FinishedDateUnknown));
        var combined = CombineReads(entry.Status, existing.Concat(rows.SelectMany(r => r.Reads)));
        entry.Reads.Clear();
        entry.Reads.AddRange(combined);
        if (Earliest(rows) is { } added && added < entry.AddedAt)
        {
            entry.AddedAt = added;
        }
        if (entry.EditionId is null && entry.ProgressUnit is null)
        {
            RestoreProgress(entry, rows, editions);
        }
        ApplyProgress(entry);
        entry.UpdatedAt = LastActivity(entry);
    }

    /// <summary>
    /// The reads to keep: each dated read once (two rows can record the same read), every read finished on an unknown
    /// date, and, for a book being read, one open read.
    /// </summary>
    private static List<Read> CombineReads(ReadingStatus status, IEnumerable<ImportedRead> reads)
    {
        var all = reads.ToList();
        var kept = all.Where(r => r.FinishedOn is not null).Distinct()
            .Concat(all.Where(r => r.FinishedDateUnknown))
            .ToList();
        if (status is ReadingStatus.Reading or ReadingStatus.Paused
            && all.Where(r => r.FinishedOn is null && !r.FinishedDateUnknown).MaxBy(r => r.StartedOn) is { } open)
        {
            kept.Add(open);
        }
        return kept.Select(r => new Read { StartedOn = r.StartedOn, FinishedOn = r.FinishedOn, FinishedDateUnknown = r.FinishedDateUnknown })
            .ToList();
    }

    private static ReadingStatus Furthest(IEnumerable<ReadingStatus> statuses) => statuses.MinBy(s => Array.IndexOf(Precedence, s));

    private static DateTimeOffset? Earliest(List<ImportItem> rows) =>
        rows.Select(r => r.AddedAt ?? (r.AddedOn is { } date ? AtMidnight(date) : null)).Min();

    /// <summary>
    /// The edition and progress from a Carrel export. Pages and seconds only mean something in their edition, so if
    /// that edition isn't found (Hardcover dropped it, or Carrel doesn't have it), the progress is kept as a percentage.
    /// </summary>
    private static void RestoreProgress(LibraryEntry entry, List<ImportItem> rows, Dictionary<(long, long), long> editions)
    {
        if (rows.FirstOrDefault(r => r.HardcoverEditionId is not null || r.ProgressUnit is not null) is not { } row)
        {
            return;
        }
        long? editionId = row.HardcoverEditionId is { } id && editions.TryGetValue((entry.BookId, id), out var found) ? found : null;
        entry.EditionId = editionId;
        if (row.ProgressUnit == ProgressUnit.Percent || (row.ProgressUnit is not null && editionId is not null))
        {
            entry.ProgressUnit = row.ProgressUnit;
            entry.ProgressValue = row.ProgressValue;
            entry.ProgressPercent = row.ProgressPercent;
        }
        else if (row.ProgressPercent is { } percent)
        {
            entry.ProgressUnit = ProgressUnit.Percent;
            entry.ProgressValue = percent;
            entry.ProgressPercent = percent;
        }
    }

    /// <summary>Finished books are complete; books not being read have no progress.</summary>
    private static void ApplyProgress(LibraryEntry entry)
    {
        if (entry.Status == ReadingStatus.Read)
        {
            entry.ProgressPercent = 100;
        }
        else if (entry.Status is not (ReadingStatus.Reading or ReadingStatus.Paused))
        {
            entry.ProgressUnit = null;
            entry.ProgressValue = null;
            entry.ProgressPercent = null;
        }
    }

    /// <summary>
    /// When the reader last did something with the book, by the export's dates, rather than when it was imported. The
    /// library is ordered by this, so imported books fall into place among the rest.
    /// </summary>
    private static DateTimeOffset LastActivity(LibraryEntry entry) =>
        entry.Reads.SelectMany(r => new[] { r.FinishedOn, r.StartedOn }).OfType<DateOnly>().Select(AtMidnight)
            .Append(entry.AddedAt)
            .Max();

    private static DateTimeOffset AtMidnight(DateOnly date) => new(date.ToDateTime(TimeOnly.MinValue), TimeSpan.Zero);
}
