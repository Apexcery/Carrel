using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Books;

/// <summary>
/// Hides covers taken down after a copyright notice (Book.CoverSuppressed) in search results and series listings.
/// Those come live from the book sources and are cached, so they're checked on every response rather than when stored.
/// </summary>
public class CoverSuppression(CarrelDbContext db)
{
    public async Task<BookSearchResponse> ApplyAsync(BookSearchResponse response, CancellationToken ct)
    {
        var hardcoverIds = response.Results.Where(r => r.HardcoverId is not null).Select(r => (long)r.HardcoverId!.Value).ToArray();
        var openLibraryIds = response.Results.Where(r => r.OpenLibraryWorkId is not null).Select(r => r.OpenLibraryWorkId!).ToArray();
        if (hardcoverIds.Length == 0 && openLibraryIds.Length == 0)
        {
            return response;
        }

        var hidden = await db.Books
            .Where(b => b.CoverSuppressed
                && ((b.HardcoverId != null && hardcoverIds.Contains(b.HardcoverId.Value))
                    || (b.OpenLibraryWorkKey != null && openLibraryIds.Contains(b.OpenLibraryWorkKey))))
            .Select(b => new { b.HardcoverId, b.OpenLibraryWorkKey })
            .ToListAsync(ct);
        if (hidden.Count == 0)
        {
            return response;
        }

        bool IsHidden(BookSearchResult r) => hidden.Any(h =>
            (r.HardcoverId is not null && h.HardcoverId == r.HardcoverId) ||
            (r.OpenLibraryWorkId is not null && h.OpenLibraryWorkKey == r.OpenLibraryWorkId));
        return response with { Results = response.Results.Select(r => IsHidden(r) ? r with { CoverUrl = null } : r).ToArray() };
    }

    public async Task<SeriesDetail> ApplyAsync(SeriesDetail series, CancellationToken ct)
    {
        var ids = series.Books.Concat(series.OtherBooks).Select(b => (long)b.HardcoverId).ToArray();
        var hidden = await db.Books
            .Where(b => b.CoverSuppressed && b.HardcoverId != null && ids.Contains(b.HardcoverId.Value))
            .Select(b => b.HardcoverId!.Value)
            .ToListAsync(ct);
        if (hidden.Count == 0)
        {
            return series;
        }

        SeriesBook[] Hide(SeriesBook[] books) =>
            books.Select(b => hidden.Contains(b.HardcoverId) ? b with { CoverUrl = null } : b).ToArray();
        return series with { Books = Hide(series.Books), OtherBooks = Hide(series.OtherBooks) };
    }
}
