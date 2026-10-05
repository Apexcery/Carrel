using System.Linq.Expressions;
using Carrel.Api.Books;
using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Imports;

/// <summary>
/// Runs one batch of an import: about a minute of matching rows to books, then, once every row is matched or given up
/// on, writing them to the library. Each batch queues the next, so an import of any size finishes in pieces that fit
/// within a request. All progress is saved as it's made, so a batch that's cut off loses nothing.
/// </summary>
public class ImportProcessor(
    CarrelDbContext db,
    HardcoverClient hardcover,
    IServiceScopeFactory scopes,
    IImportQueue queue,
    ILogger<ImportProcessor> logger)
{
    /// <summary>How long a batch spends matching, then writing. Together well within Cloud Run's 300-second limit.</summary>
    private static readonly TimeSpan MatchingTime = TimeSpan.FromSeconds(50);
    private static readonly TimeSpan WritingTime = TimeSpan.FromSeconds(120);

    /// <summary>
    /// How long other runs keep off while a batch runs: longer than Cloud Run lets a request run, so a retry of a
    /// request Cloud Run cut off can't start while the first is still writing.
    /// </summary>
    private static readonly TimeSpan Lease = TimeSpan.FromMinutes(6);

    /// <summary>How long to wait before trying again while Hardcover's daily allowance is kept for readers.</summary>
    private static readonly TimeSpan AllowanceWait = TimeSpan.FromMinutes(30);

    private const int MaxFailures = 8;
    private const int PendingPerBatch = 500;

    /// <summary>Runs a batch. False if another batch is still running, so this one should be tried again later.</summary>
    public async Task<bool> ProcessAsync(long importId, CancellationToken ct)
    {
        // Claim the import, so a retried or duplicate batch doesn't run alongside this one.
        var now = DateTimeOffset.UtcNow;
        var claimed = await db.LibraryImports
            .Where(i => i.Id == importId && (i.State == ImportState.Matching || i.State == ImportState.Waiting)
                        && (i.LeaseUntil == null || i.LeaseUntil < now))
            .ExecuteUpdateAsync(s => s.SetProperty(i => i.LeaseUntil, now + Lease), ct);
        if (claimed == 0)
        {
            // Finished, failed, or gone: nothing to do. Otherwise a batch holds the lease; it may have crashed, so
            // this one tries again once the lease runs out. Both queue the same next batch, so they don't double up.
            return !await db.LibraryImports.AnyAsync(i => i.Id == importId
                && (i.State == ImportState.Matching || i.State == ImportState.Waiting), ct);
        }
        var import = await db.LibraryImports.SingleAsync(i => i.Id == importId, ct);

        try
        {
            if (HardcoverClient.DailyAllowanceReserved)
            {
                import.State = ImportState.Waiting;
            }
            else
            {
                import.State = ImportState.Matching;
                await MatchAsync(import, ct);
                if (!await db.ImportItems.Where(i => i.ImportId == importId).AnyAsync(Unresolved, ct)
                    && await WriteAsync(import, ct))
                {
                    import.State = ImportState.Done;
                    import.FinishedAt = DateTimeOffset.UtcNow;
                }
            }
            import.Failures = 0;
        }
        catch (Exception e) when (e is not OperationCanceledException || !ct.IsCancellationRequested)
        {
            logger.LogError(e, "Import {ImportId} batch failed.", importId);
            db.ChangeTracker.Clear();
            import = await db.LibraryImports.SingleAsync(i => i.Id == importId, CancellationToken.None);
            import.Failures++;
            if (import.Failures >= MaxFailures)
            {
                import.State = ImportState.Failed;
                import.FinishedAt = DateTimeOffset.UtcNow;
            }
        }

        // Saved, releasing the lease, before the next batch is queued; otherwise it can start while this one still holds it.
        var next = import.State is ImportState.Matching or ImportState.Waiting;
        import.LeaseUntil = null;
        import.UpdatedAt = DateTimeOffset.UtcNow;
        if (next)
        {
            import.BatchNumber++;
        }
        await db.SaveChangesAsync(CancellationToken.None);
        if (next)
        {
            // If queueing fails, the import looks stalled and the import page queues it again.
            await queue.EnqueueAsync(importId, import.BatchNumber,
                import.State == ImportState.Waiting ? AllowanceWait : import.Failures > 0 ? TimeSpan.FromSeconds(30 * import.Failures) : TimeSpan.Zero,
                CancellationToken.None);
        }
        return true;
    }

    /// <summary>Rows still being matched: not yet looked up, waiting for a search, or matched to a book not stored yet.</summary>
    private static readonly Expression<Func<ImportItem, bool>> Unresolved = item =>
        item.Match == ImportMatch.Pending || item.Match == ImportMatch.NeedsSearch
        || ((item.Match == ImportMatch.Exact || item.Match == ImportMatch.ByTitle) && item.BookId == null);

    private async Task MatchAsync(LibraryImport import, CancellationToken ct)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(ct);
        timeout.CancelAfter(MatchingTime);
        try
        {
            await MatchByIdsAsync(import, timeout.Token);
            await MatchBySearchAsync(import, timeout.Token);
            await StoreBooksAsync(import, timeout.Token);
        }
        catch (OperationCanceledException) when (timeout.IsCancellationRequested && !ct.IsCancellationRequested)
        {
            // Out of time for this batch; the next one carries on.
        }
        catch (BookSourceUnavailableException) when (HardcoverClient.DailyAllowanceReserved)
        {
            import.State = ImportState.Waiting;
        }
        // Progress made before running out of time or allowance is kept.
        await db.SaveChangesAsync(CancellationToken.None);
    }

    /// <summary>
    /// Looks rows up by their ids: ISBNs of books Carrel already has first (free), then Hardcover by Goodreads id,
    /// ISBN, and ASIN, a hundred at a time. Rows none of these find are searched for by title next.
    /// </summary>
    private async Task MatchByIdsAsync(LibraryImport import, CancellationToken ct)
    {
        var pending = await db.ImportItems
            .Where(i => i.ImportId == import.Id && i.Match == ImportMatch.Pending)
            .OrderBy(i => i.Row)
            .Take(PendingPerBatch)
            .ToListAsync(ct);
        if (pending.Count == 0)
        {
            return;
        }

        var isbns = pending.SelectMany(i => new[] { i.Isbn13, i.Isbn10 }).OfType<string>().Distinct().ToList();
        var stored = await db.Editions
            .Where(e => (e.Isbn13 != null && isbns.Contains(e.Isbn13)) || (e.Isbn10 != null && isbns.Contains(e.Isbn10)))
            .Select(e => new { e.Isbn13, e.Isbn10, e.BookId })
            .ToListAsync(ct);
        foreach (var item in pending)
        {
            if (stored.FirstOrDefault(e => (item.Isbn13 != null && e.Isbn13 == item.Isbn13) || (item.Isbn10 != null && e.Isbn10 == item.Isbn10)) is { } edition)
            {
                item.Match = ImportMatch.Exact;
                item.BookId = edition.BookId;
            }
        }

        await LookUpAsync(pending, i => i.GoodreadsId, hardcover.FindBookIdsByGoodreadsIdsAsync, ct);
        await LookUpAsync(pending, i => i.Isbn13, hardcover.FindBookIdsByIsbnsAsync, ct);
        await LookUpAsync(pending, i => i.Isbn10, hardcover.FindBookIdsByIsbnsAsync, ct);
        await LookUpAsync(pending, i => i.Asin, hardcover.FindBookIdsByAsinsAsync, ct);

        foreach (var item in pending.Where(i => i.Match == ImportMatch.Pending))
        {
            item.Match = ImportMatch.NeedsSearch;
        }
        await db.SaveChangesAsync(ct);
    }

    private async Task LookUpAsync(
        List<ImportItem> items,
        Func<ImportItem, string?> key,
        Func<IReadOnlyCollection<string>, CancellationToken, Task<Dictionary<string, int>>> lookUp,
        CancellationToken ct)
    {
        var keys = items.Where(i => i.Match == ImportMatch.Pending).Select(key).OfType<string>().Distinct().ToList();
        foreach (var chunk in keys.Chunk(HardcoverClient.MaxLookupBatch))
        {
            var found = await lookUp(chunk, ct);
            foreach (var item in items.Where(i => i.Match == ImportMatch.Pending && key(i) is { } k && found.ContainsKey(k)))
            {
                item.Match = ImportMatch.Exact;
                item.HardcoverBookId = found[key(item)!];
            }
            // Saved as it goes, so a batch that runs out of time doesn't repeat lookups.
            await db.SaveChangesAsync(ct);
        }
    }

    /// <summary>Searches Hardcover for each remaining row by title and author, one request each.</summary>
    private async Task MatchBySearchAsync(LibraryImport import, CancellationToken ct)
    {
        while (await db.ImportItems
                   .Where(i => i.ImportId == import.Id && i.Match == ImportMatch.NeedsSearch)
                   .OrderBy(i => i.Row)
                   .FirstOrDefaultAsync(ct) is { } item)
        {
            var results = await hardcover.SearchBooksInBackgroundAsync(TitleMatcher.SearchQuery(item.Title, item.Authors), 5, ct);
            if (TitleMatcher.FindMatch(item.Title, item.Authors, results.Hits.Select(h => h.Document)) is { } hit)
            {
                item.Match = ImportMatch.ByTitle;
                item.HardcoverBookId = int.Parse(hit.Id);
            }
            else
            {
                item.Match = ImportMatch.NotFound;
            }
            await db.SaveChangesAsync(ct);
        }
    }

    /// <summary>
    /// Stores the matched Hardcover books Carrel doesn't have yet, fetched fifty to a request, and points the rows at
    /// them. A book Hardcover no longer has leaves its rows unmatched.
    /// </summary>
    private async Task StoreBooksAsync(LibraryImport import, CancellationToken ct)
    {
        var items = await db.ImportItems
            .Where(i => i.ImportId == import.Id && i.HardcoverBookId != null && i.BookId == null)
            .ToListAsync(ct);
        var ids = items.Select(i => i.HardcoverBookId!.Value).Distinct().ToList();
        var storedIds = ids.Select(id => (long)id).ToList();
        var known = await db.Books
            .Where(b => b.HardcoverId != null && storedIds.Contains(b.HardcoverId.Value))
            .ToDictionaryAsync(b => (int)b.HardcoverId!.Value, b => b.Id, ct);
        Point(items, known);
        await db.SaveChangesAsync(ct);

        foreach (var chunk in ids.Where(id => !known.ContainsKey(id)).Chunk(HardcoverClient.MaxBookBatch))
        {
            var sources = await hardcover.GetBooksAsync(chunk, ct);
            // BookService saves each book and clears its own tracking, so it gets a context of its own.
            using var scope = scopes.CreateScope();
            var stored = await scope.ServiceProvider.GetRequiredService<BookService>().StoreHardcoverBooksAsync(sources.Values, ct);
            var byRequestedId = sources.ToDictionary(s => s.Key, s => stored[s.Value.Id]);
            Point(items, byRequestedId);
            foreach (var item in items.Where(i => i.BookId == null && chunk.Contains(i.HardcoverBookId!.Value) && !sources.ContainsKey(i.HardcoverBookId.Value)))
            {
                item.Match = ImportMatch.NotFound;
                item.HardcoverBookId = null;
            }
            await db.SaveChangesAsync(ct);
        }
    }

    private static void Point(List<ImportItem> items, Dictionary<int, long> bookIds)
    {
        foreach (var item in items.Where(i => i.BookId == null && bookIds.ContainsKey(i.HardcoverBookId!.Value)))
        {
            item.BookId = bookIds[item.HardcoverBookId!.Value];
        }
    }

    /// <summary>
    /// Writes matched rows not written yet, a hundred books at a time, until they're all written (true) or the batch
    /// runs out of time (false; the next batch carries on, since each hundred is saved with its rows marked written).
    /// </summary>
    private async Task<bool> WriteAsync(LibraryImport import, CancellationToken ct)
    {
        var deadline = DateTimeOffset.UtcNow + WritingTime;
        var items = await db.ImportItems
            .Where(i => i.ImportId == import.Id && !i.Written && i.BookId != null)
            .ToListAsync(ct);
        foreach (var chunk in items.GroupBy(i => i.BookId!.Value).Chunk(100))
        {
            if (DateTimeOffset.UtcNow > deadline)
            {
                return false;
            }
            await ImportWriter.WriteAsync(db, import, chunk, ct);
            await db.SaveChangesAsync(ct);
        }
        return true;
    }
}
