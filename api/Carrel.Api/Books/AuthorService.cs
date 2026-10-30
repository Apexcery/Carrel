using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.Extensions.Caching.Memory;

namespace Carrel.Api.Books;

/// <summary>
/// Author pages and author search from Hardcover, cleaned up and cached in memory. Hardcover lists an author's split
/// volumes, translations, and duplicate records among their books, so only books with a meaningful readership are
/// kept, and only the most read at each numbered position in a series.
/// </summary>
public partial class AuthorService(HardcoverClient hardcover, IMemoryCache cache, CoverSuppression suppression)
{
    private static readonly TimeSpan CacheDuration = TimeSpan.FromHours(12);
    private static readonly TimeSpan SearchCacheDuration = TimeSpan.FromHours(1);

    // The author's most read books, from which their page is made.
    private const int BooksFetched = 100;

    // In-between and unnumbered books in a series need at least this share of the readers of its most read book: it
    // keeps novellas such as Edgedancer but not split volumes ("The Way of Kings, Part 1"), which the series page lists.
    private const double MinInBetweenReadersShare = 0.1;

    // Search asks for more than it shows, since many hits only match one of the author's titles.
    private const int SearchFetched = 10;
    private const int SearchShown = 5;
    private const int KnownForShown = 2;

    public async Task<AuthorDetail?> GetByHardcoverIdAsync(int hardcoverId, CancellationToken ct)
    {
        var key = $"author:{hardcoverId}";
        if (!cache.TryGetValue(key, out AuthorDetail? author))
        {
            var source = await hardcover.GetAuthorAsync(hardcoverId, ct);
            author = source is null ? null : await ToDetailAsync(source, ct);
            cache.Set(key, author, new MemoryCacheEntryOptions { Size = 1, AbsoluteExpirationRelativeToNow = CacheDuration });
        }
        return author is null ? null : await suppression.ApplyAsync(author, ct);
    }

    /// <summary>Up to SearchShown authors whose names match every word searched for, in Hardcover's order.</summary>
    public async Task<AuthorSearchResult[]> SearchAsync(string query, CancellationToken ct)
    {
        query = query.Trim();
        var key = $"author-search:{query.ToLowerInvariant()}";
        if (!cache.TryGetValue(key, out AuthorSearchResult[]? results) || results is null)
        {
            var words = Words(query);
            var hits = (await hardcover.SearchAuthorsAsync(query, SearchFetched, ct)).Hits.Select(h => h.Document);
            results = hits
                .Select(d => d with { Name = string.Join(' ', d.Name.Split(' ', StringSplitOptions.RemoveEmptyEntries)) })
                .Where(d => words.All(w => Words(d.Name).Any(n => n.StartsWith(w, StringComparison.Ordinal))))
                // Hardcover has joint records for some books' co-authors ("Ursula K. Le Guin, Tao Tzu").
                .Where(d => !JointName().IsMatch(d.Name))
                // Some authors have duplicate records with a book or two; the first is the main one.
                .DistinctBy(d => string.Join(' ', Words(d.Name)))
                .Take(SearchShown)
                .Select(d => new AuthorSearchResult(
                    int.Parse(d.Id, CultureInfo.InvariantCulture), d.Name, d.Image?.Url, (d.Books ?? []).Take(KnownForShown).ToArray()))
                .ToArray();
            cache.Set(key, results, new MemoryCacheEntryOptions { Size = 1, AbsoluteExpirationRelativeToNow = SearchCacheDuration });
        }
        return await suppression.ApplyAsync(results, ct);
    }

    private async Task<AuthorDetail> ToDetailAsync(HardcoverAuthorDetail source, CancellationToken ct)
    {
        var listed = (await hardcover.ListBooksAsync(
            [RecommendationService.CatalogueFilter(new() { ["contributions"] = new { author_id = new { _eq = source.Id } } })],
            BooksFetched, ct))[0];
        // Books they wrote, not ones they translated, narrated, or wrote a foreword for. Most read first.
        var written = listed
            .Where(b => b.Contributions.Any(c => RecommendationService.IsAuthor(c) && c.Author.Id == source.Id))
            .ToList();
        var mostReaders = written.Select(b => b.UsersCount).DefaultIfEmpty(0).Max();
        var books = written.Where(b => b.UsersCount >= mostReaders * RecommendationService.MinAuthorReadersShare).ToList();

        // Grouping keeps the order each series first appears in, so its most read book decides its place.
        var series = books
            .Select(b => (Book: b, Series: RecommendationService.MainSeries(b.BookSeries)))
            .Where(x => x.Series is not null)
            .GroupBy(x => x.Series!.Series!.Id)
            .Select(g => new AuthorSeries(
                g.Key,
                g.First().Series!.Series!.Name,
                g.Where(x => x.Series!.Position is { } p && p == decimal.Truncate(p)
                        || x.Book.UsersCount >= g.First().Book.UsersCount * MinInBetweenReadersShare)
                    .OrderBy(x => x.Series!.Position ?? decimal.MaxValue)
                    .ThenByDescending(x => x.Book.UsersCount)
                    .DistinctBy(x => x.Series!.Position ?? -x.Book.Id) // Unnumbered books are all kept.
                    .Select(x => RecommendationService.ToSuggestion(x.Book))
                    .ToArray()))
            .ToArray();
        var standalones = books
            .Where(b => RecommendationService.MainSeries(b.BookSeries) is null)
            .Select(RecommendationService.ToSuggestion)
            .ToArray();

        return new AuthorDetail(
            source.Id,
            source.Name,
            source.CachedImage?.Url,
            source.BornYear,
            source.DeathYear,
            string.IsNullOrWhiteSpace(source.Location) ? null : source.Location.Trim(),
            string.IsNullOrWhiteSpace(source.Bio) ? null : source.Bio.Replace("\r\n", "\n").Trim(),
            series,
            standalones);
    }

    /// <summary>Lower-case words without accents, so "Brontë" matches "bronte".</summary>
    private static string[] Words(string text)
    {
        var plain = new string(text.Normalize(NormalizationForm.FormD)
            .Where(c => CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark)
            .Select(c => char.IsLetterOrDigit(c) ? char.ToLowerInvariant(c) : ' ')
            .ToArray());
        return plain.Split(' ', StringSplitOptions.RemoveEmptyEntries);
    }

    /// <summary>Several people's names in one: joined by &amp; or ;, or listed with commas (but not "King, Jr.").</summary>
    [GeneratedRegex(@"[&;]|,(?!\s*(Jr|Sr|II|III|IV)\.?\s*$)", RegexOptions.IgnoreCase)]
    private static partial Regex JointName();
}
