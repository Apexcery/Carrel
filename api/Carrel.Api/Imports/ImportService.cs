using Carrel.Api.Books;
using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Imports;

/// <summary>A reader's imports: starting one, following its progress, and sorting out rows it couldn't match for sure.</summary>
public class ImportService(CarrelDbContext db, BookService books, IImportQueue queue, ILogger<ImportService> logger)
{
    /// <summary>An import that hasn't moved for this long is queued again, in case its chain of batches broke.</summary>
    private static readonly TimeSpan StalledAfter = TimeSpan.FromMinutes(5);

    public async Task<ImportStatusDto> CreateAsync(Guid userId, string csv, bool overwriteExisting, CancellationToken ct)
    {
        if (await db.LibraryImports.AnyAsync(i => i.UserId == userId
                && (i.State == ImportState.Matching || i.State == ImportState.Waiting), ct))
        {
            throw new ImportFileException("An import is already running. Wait for it to finish before starting another.");
        }
        var (source, rows) = ImportParser.Parse(csv.TrimStart('﻿'));

        var import = new LibraryImport
        {
            UserId = userId,
            Source = source,
            OverwriteExisting = overwriteExisting,
            State = ImportState.Matching,
            CreatedAt = DateTimeOffset.UtcNow,
            UpdatedAt = DateTimeOffset.UtcNow,
            BatchNumber = 1,
        };
        import.Items.AddRange(rows.Select((row, i) => new ImportItem
        {
            Row = i + 1,
            Title = row.Title,
            Authors = row.Authors,
            Isbn13 = row.Isbn13,
            Isbn10 = row.Isbn10,
            Asin = row.Asin,
            GoodreadsId = row.GoodreadsId,
            Status = row.Status,
            Rating = row.Rating,
            AddedOn = row.AddedOn,
            Reads = row.Reads,
            // Carrel's own exports name the Hardcover book, so there's nothing to look up.
            Match = row.HardcoverBookId is null ? ImportMatch.Pending : ImportMatch.Exact,
            HardcoverBookId = row.HardcoverBookId,
            HardcoverEditionId = row.HardcoverEditionId,
            AddedAt = row.AddedAt,
            ProgressUnit = row.ProgressUnit,
            ProgressValue = row.ProgressValue,
            ProgressPercent = row.ProgressPercent,
        }));
        db.LibraryImports.Add(import);
        await db.SaveChangesAsync(ct);
        try
        {
            await queue.EnqueueAsync(import.Id, import.BatchNumber, TimeSpan.Zero, ct);
        }
        catch (Exception e)
        {
            // Marked failed rather than left running with nothing queued, which would block new imports for good;
            // the reader can try again from the import page.
            logger.LogError(e, "Couldn't queue import {ImportId}.", import.Id);
            import.State = ImportState.Failed;
            import.FinishedAt = DateTimeOffset.UtcNow;
            await db.SaveChangesAsync(CancellationToken.None);
        }
        return await StatusAsync(import, ct);
    }

    /// <summary>The reader's latest import, restarting it if it has stalled; null if they've never imported.</summary>
    public async Task<ImportStatusDto?> LatestAsync(Guid userId, CancellationToken ct)
    {
        var import = await db.LibraryImports.Where(i => i.UserId == userId).OrderByDescending(i => i.CreatedAt).FirstOrDefaultAsync(ct);
        if (import is null)
        {
            return null;
        }
        var now = DateTimeOffset.UtcNow;
        var stalledAfter = import.State == ImportState.Waiting ? TimeSpan.FromHours(1) : StalledAfter;
        if (import.State is ImportState.Matching or ImportState.Waiting
            && (import.LeaseUntil is null || import.LeaseUntil < now) && import.UpdatedAt < now - stalledAfter)
        {
            import.BatchNumber++;
            import.UpdatedAt = now;
            // Saved first, so the batch sees the new batch number and queues the one after it.
            await db.SaveChangesAsync(ct);
            try
            {
                await queue.EnqueueAsync(import.Id, import.BatchNumber, TimeSpan.Zero, ct);
            }
            catch (Exception e)
            {
                // The status still shows; the next look at the page tries again.
                logger.LogError(e, "Couldn't queue stalled import {ImportId}.", import.Id);
            }
        }
        return await StatusAsync(import, ct);
    }

    /// <summary>Starts a failed import again from where it stopped.</summary>
    public async Task<ImportStatusDto?> ResumeAsync(Guid userId, long importId, CancellationToken ct)
    {
        var import = await db.LibraryImports.FirstOrDefaultAsync(i => i.Id == importId && i.UserId == userId, ct);
        if (import is null)
        {
            return null;
        }
        if (import.State == ImportState.Failed)
        {
            import.State = ImportState.Matching;
            import.Failures = 0;
            import.FinishedAt = null;
            import.BatchNumber++;
            import.UpdatedAt = DateTimeOffset.UtcNow;
            await db.SaveChangesAsync(ct);
            await queue.EnqueueAsync(import.Id, import.BatchNumber, TimeSpan.Zero, ct);
        }
        return await StatusAsync(import, ct);
    }

    /// <summary>Rows for the reader to look at: title matches not checked yet, and rows nothing matched.</summary>
    public async Task<ImportReviewItemDto[]?> ReviewAsync(Guid userId, long importId, CancellationToken ct)
    {
        if (!await db.LibraryImports.AnyAsync(i => i.Id == importId && i.UserId == userId, ct))
        {
            return null;
        }
        var items = await db.ImportItems
            .Where(i => i.ImportId == importId
                        && ((i.Match == ImportMatch.ByTitle && !i.Confirmed && i.BookId != null) || i.Match == ImportMatch.NotFound))
            .Include(i => i.Book).ThenInclude(b => b!.Authors).ThenInclude(ba => ba.Author)
            .Include(i => i.Book).ThenInclude(b => b!.Series).ThenInclude(bs => bs.Series)
            .OrderBy(i => i.Row)
            .AsSplitQuery()
            .ToListAsync(ct);
        return items.Select(i => new ImportReviewItemDto(
            i.Id,
            i.Row,
            i.Title,
            i.Authors,
            i.Match,
            i.Book is not { } book ? null : ToBookDto(book))).ToArray();
    }

    private static ImportBookDto ToBookDto(Book book)
    {
        var series = book.Series.OrderByDescending(bs => bs.IsFeatured).ThenBy(bs => bs.Position ?? decimal.MaxValue).FirstOrDefault();
        return new ImportBookDto(
            book.Id,
            book.Title,
            book.Authors.Where(a => a.Role == "author").OrderBy(a => a.Position).Select(a => a.Author.Name).ToArray(),
            book.CoverSuppressed ? null : book.CoverUrl,
            book.FirstPublishedYear,
            series?.Series.Name,
            series?.Position);
    }

    /// <summary>Keeps a title match as it is.</summary>
    public async Task<bool> ConfirmAsync(Guid userId, long importId, long itemId, CancellationToken ct) =>
        await Items(userId, importId, itemId).Where(i => i.Match == ImportMatch.ByTitle)
            .ExecuteUpdateAsync(s => s.SetProperty(i => i.Confirmed, true), ct) > 0;

    /// <summary>Gives up on an unmatched row; the book stays out of the library.</summary>
    public async Task<bool> DismissAsync(Guid userId, long importId, long itemId, CancellationToken ct) =>
        await Items(userId, importId, itemId).Where(i => i.Match == ImportMatch.NotFound)
            .ExecuteUpdateAsync(s => s.SetProperty(i => i.Match, ImportMatch.Dismissed), ct) > 0;

    /// <summary>
    /// Matches a row to the book the reader picked and writes it to the library. If the row had been matched by title
    /// to a different book and that entry came from this import alone, the wrong entry is removed.
    /// </summary>
    public async Task<bool> ChooseAsync(Guid userId, long importId, long itemId, ChooseBookRequest request, CancellationToken ct)
    {
        var import = await db.LibraryImports.FirstOrDefaultAsync(i => i.Id == importId && i.UserId == userId, ct);
        var item = await Items(userId, importId, itemId).FirstOrDefaultAsync(ct);
        if (import is not { State: ImportState.Done } || item is not { Match: ImportMatch.ByTitle or ImportMatch.NotFound })
        {
            return false;
        }
        var book = request switch
        {
            { HardcoverId: { } hardcoverId } => await books.GetByHardcoverIdAsync(hardcoverId, ct),
            { OpenLibraryId: { } openLibraryId } => await books.GetByOpenLibraryIdAsync(openLibraryId, ct),
            _ => null,
        };
        if (book is null)
        {
            return false;
        }

        if (item is { CreatedEntry: true, BookId: { } wrongBookId } && wrongBookId != book.Id
            && !await db.ImportItems.AnyAsync(i => i.ImportId == importId && i.Id != itemId && i.BookId == wrongBookId && i.CreatedEntry, ct))
        {
            await db.LibraryEntries.Where(e => e.UserId == userId && e.BookId == wrongBookId).ExecuteDeleteAsync(ct);
        }
        item.Match = ImportMatch.Chosen;
        item.BookId = book.Id;
        item.HardcoverBookId = null;
        item.Written = false;
        item.CreatedEntry = false;
        item.Confirmed = true;
        await ImportWriter.WriteAsync(db, import, [new[] { item }.GroupBy(i => i.BookId!.Value).Single()], ct);
        await db.SaveChangesAsync(ct);
        return true;
    }

    private IQueryable<ImportItem> Items(Guid userId, long importId, long itemId) =>
        db.ImportItems.Where(i => i.Id == itemId && i.ImportId == importId && i.Import.UserId == userId);

    private async Task<ImportStatusDto> StatusAsync(LibraryImport import, CancellationToken ct)
    {
        var counts = await db.ImportItems
            .Where(i => i.ImportId == import.Id)
            .GroupBy(i => new { i.Match, Unstored = i.BookId == null, i.Confirmed })
            .Select(g => new { g.Key.Match, g.Key.Unstored, g.Key.Confirmed, Count = g.Count() })
            .ToListAsync(ct);
        int Count(Func<ImportMatch, bool, bool, bool> filter) =>
            counts.Where(c => filter(c.Match, c.Unstored, c.Confirmed)).Sum(c => c.Count);

        return new ImportStatusDto(
            import.Id,
            import.Source,
            import.State,
            import.OverwriteExisting,
            import.CreatedAt,
            import.FinishedAt,
            Total: Count((_, _, _) => true),
            Matched: Count((match, unstored, _) => match is ImportMatch.Exact or ImportMatch.ByTitle or ImportMatch.Chosen && !unstored),
            ToCheck: Count((match, unstored, confirmed) => match == ImportMatch.ByTitle && !unstored && !confirmed),
            NotFound: Count((match, _, _) => match == ImportMatch.NotFound),
            Remaining: Count((match, unstored, _) => match is ImportMatch.Pending or ImportMatch.NeedsSearch
                                                     || (match is ImportMatch.Exact or ImportMatch.ByTitle && unstored)));
    }
}
