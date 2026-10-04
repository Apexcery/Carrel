using Microsoft.Extensions.Caching.Memory;

namespace Carrel.Api.Books;

/// <summary>
/// Series listings from Hardcover, cleaned up and cached in memory. Hardcover's series data includes split volumes
/// ("Part 2 of 3"), translations and duplicates at the same position, so only the most-read book at each numbered
/// position is kept, and in-between or unnumbered works only if they have a meaningful readership.
/// </summary>
public class SeriesService(HardcoverClient hardcover, IMemoryCache cache, CoverSuppression suppression)
{
    private static readonly TimeSpan CacheDuration = TimeSpan.FromHours(12);

    // In-between and unnumbered works need at least this share of the most-read book's readers.
    private const double MinimumOtherReadersShare = 0.01;

    public async Task<SeriesDetail?> GetByHardcoverIdAsync(int hardcoverId, CancellationToken ct)
    {
        var key = $"series:{hardcoverId}";
        if (!cache.TryGetValue(key, out SeriesDetail? series))
        {
            var source = await hardcover.GetSeriesAsync(hardcoverId, ct);
            series = source is null ? null : ToDetail(source);
            cache.Set(key, series, new MemoryCacheEntryOptions { Size = 1, AbsoluteExpirationRelativeToNow = CacheDuration });
        }
        return series is null ? null : await suppression.ApplyAsync(series, ct);
    }

    private static SeriesDetail ToDetail(HardcoverSeriesDetail source)
    {
        // Entries arrive ordered by position, then most-read first.
        var entries = source.BookSeries.DistinctBy(e => e.Book.Id).ToList();
        var mostReaders = entries.Select(e => e.Book.UsersCount).DefaultIfEmpty(0).Max();

        var main = entries
            .Where(e => e.Position is { } p && p == decimal.Truncate(p))
            .DistinctBy(e => e.Position)
            .ToList();
        var other = entries
            .Where(e => e.Position is not { } p || p != decimal.Truncate(p))
            .Where(e => e.Book.UsersCount >= mostReaders * MinimumOtherReadersShare)
            .DistinctBy(e => e.Position ?? -e.Book.Id) // Unnumbered works are all kept.
            .ToList();

        return new SeriesDetail(
            source.Id,
            source.Name,
            source.Author?.Name,
            source.IsCompleted,
            main.Select(ToBook).ToArray(),
            other.Select(ToBook).ToArray());
    }

    private static SeriesBook ToBook(HardcoverSeriesEntry entry) => new(
        entry.Book.Id,
        entry.Position,
        entry.Book.Title,
        entry.Book.Contributions
            .Where(c => string.IsNullOrEmpty(c.Contribution) || c.Contribution.Equals("author", StringComparison.OrdinalIgnoreCase))
            .Select(c => c.Author.Name)
            .ToArray(),
        entry.Book.ReleaseYear,
        entry.Book.CachedImage?.Url,
        entry.Book.Rating is { } rating ? Math.Round(rating, 2) : null,
        entry.Book.RatingsCount);
}
