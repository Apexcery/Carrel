using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;

namespace Carrel.Api.Books;

/// <summary>
/// Book suggestions from Hardcover: books like a given one, more by its author, what's popular this month, and the
/// next book in each series a reader is working through. Lists are cached in memory and shared by every reader, so
/// they hold more books than the website shows; it leaves out books the reader already has.
/// </summary>
public class RecommendationService(
    CarrelDbContext db,
    HardcoverClient hardcover,
    SeriesService seriesService,
    CoverSuppression suppression,
    IMemoryCache cache)
{
    private static readonly TimeSpan RelatedCacheDuration = TimeSpan.FromDays(7);
    private static readonly TimeSpan DiscoverCacheDuration = TimeSpan.FromDays(1);

    private const int ListSize = 24;
    private const int ShelfSize = 12;
    private const int FetchSize = 40;
    private const int TrendingDays = 30;
    private const int NewReleaseDays = 90;
    private const int ComingSoonDays = 180;

    // Top rated: books with at least this many ratings, best first.
    private const int TopRatedMinRatings = 1000;

    // Similar books share the book's two most specific genres and its two strongest moods, loosening a tag at a time
    // when that finds too few. Tags on fewer books than MinTagBooks across Hardcover are too rare to match anything,
    // tags fewer than MinTagVotes readers gave the book are noise (unless that's all it has), and books with fewer
    // than MinReaders readers are too obscure to suggest.
    private const int GenresConsidered = 5;
    private const int MinTagBooks = 100;
    private const int MinTagVotes = 2;
    private const int MinReaders = 50;

    // More by the author leaves out books with less than this share of the readers of the author's most-read book.
    private const double MinAuthorReadersShare = 0.01;

    // Popular in a genre: books with the genre in their top GenreTopTags, from the GenreFetchSize most read with it at all.
    private const int GenreTopTags = 3;
    private const int GenreFetchSize = 80;

    // Umbrella genres that say little about what a reader likes, left out when choosing their top genre.
    private static readonly HashSet<string> BroadGenres = new(StringComparer.OrdinalIgnoreCase)
    {
        "Fiction", "Nonfiction", "Non-Fiction", "Literature & Fiction", "Science Fiction & Fantasy", "General",
    };

    // Series checked for "next in your series", most recently active first. Each is one Hardcover request until cached.
    private const int MaxSeries = 6;

    /// <summary>Books like this one and more by its first author; null if the book isn't stored.</summary>
    public async Task<RelatedBooks?> GetRelatedAsync(long bookId, CancellationToken ct)
    {
        var book = await db.Books.Where(b => b.Id == bookId).Select(b => new { b.HardcoverId }).FirstOrDefaultAsync(ct);
        if (book is null)
        {
            return null;
        }
        if (book.HardcoverId is not { } hardcoverId)
        {
            return new RelatedBooks([], null, []);
        }

        var key = $"related:{hardcoverId}";
        if (!cache.TryGetValue(key, out RelatedBooks? related) || related is null)
        {
            related = await FetchRelatedAsync((int)hardcoverId, ct);
            cache.Set(key, related, new MemoryCacheEntryOptions { Size = 1, AbsoluteExpirationRelativeToNow = RelatedCacheDuration });
        }
        return related with
        {
            Similar = await suppression.ApplyAsync(related.Similar, ct),
            ByAuthor = await suppression.ApplyAsync(related.ByAuthor, ct),
        };
    }

    /// <summary>
    /// The Discover shelves, the same for everyone: most read on Hardcover over the last month, popular books out in
    /// the last few months, the most anticipated coming in the next few, and the best rated. One book per series each.
    /// </summary>
    public async Task<DiscoverShelves> GetDiscoverAsync(CancellationToken ct)
    {
        if (!cache.TryGetValue("discover", out DiscoverShelves? shelves) || shelves is null)
        {
            var today = DateOnly.FromDateTime(DateTime.UtcNow);
            var trendingIds = (await hardcover.GetTrendingIdsAsync(today.AddDays(-TrendingDays), today, FetchSize, ct)).Distinct().ToArray();
            // The first three are ordered by readers (for books not out yet, readers who want to read them), so they
            // share a request; top rated is ordered by rating.
            var byReaders = await hardcover.ListBooksAsync(
            [
                CatalogueFilter(new() { ["id"] = new { _in = trendingIds } }),
                CatalogueFilter(new() { ["release_date"] = new { _gte = today.AddDays(-NewReleaseDays), _lte = today } }),
                CatalogueFilter(new() { ["release_date"] = new { _gt = today, _lte = today.AddDays(ComingSoonDays) } }),
            ], FetchSize, ct);
            var topRated = (await hardcover.ListBooksAsync(
                [CatalogueFilter(new() { ["ratings_count"] = new { _gte = TopRatedMinRatings } })],
                FetchSize, ct, "{rating: desc}"))[0];

            var rank = trendingIds.Select((id, index) => (id, index)).ToDictionary(x => x.id, x => x.index);
            shelves = new DiscoverShelves(
                Shelf(byReaders[0].OrderBy(b => rank[b.Id])),
                Shelf(byReaders[1]),
                Shelf(byReaders[2]),
                // A series' best-rated book, even a later one: a loved book 2 is a reason to start book 1.
                Shelf(topRated, earliestInSeries: false));
            cache.Set("discover", shelves, new MemoryCacheEntryOptions { Size = 1, AbsoluteExpirationRelativeToNow = DiscoverCacheDuration });
        }
        return new DiscoverShelves(
            await suppression.ApplyAsync(shelves.Popular, ct),
            await suppression.ApplyAsync(shelves.NewReleases, ct),
            await suppression.ApplyAsync(shelves.ComingSoon, ct),
            await suppression.ApplyAsync(shelves.TopRated, ct));
    }

    private static BookSuggestion[] Shelf(IEnumerable<HardcoverListedBook> books, bool earliestInSeries = true) =>
        OnePerSeries(books, earliestInSeries).Take(ShelfSize).Select(ToSuggestion).ToArray();

    /// <summary>
    /// Popular books in the genre that comes up most among the books the reader has read or is reading. No genre (and
    /// no books) if none of their books has one yet; no books if Hardcover can't be reached.
    /// </summary>
    public async Task<GenrePicks> GetGenrePicksAsync(Guid userId, CancellationToken ct)
    {
        var names = await db.LibraryEntries
            .Where(e => e.UserId == userId && (e.Status == ReadingStatus.Read || e.Status == ReadingStatus.Reading))
            .SelectMany(e => e.Book.Genres.Select(bg => bg.Genre.Name))
            .ToListAsync(ct);
        var genre = names
            .Where(name => !BroadGenres.Contains(name))
            .GroupBy(name => name, StringComparer.OrdinalIgnoreCase)
            .OrderByDescending(g => g.Count())
            .ThenBy(g => g.Key, StringComparer.OrdinalIgnoreCase)
            .Select(g => g.Key)
            .FirstOrDefault();
        if (genre is null)
        {
            return new GenrePicks(null, []);
        }

        // Shared by every reader with the same top genre.
        var key = $"genre:{genre.ToLowerInvariant()}";
        if (!cache.TryGetValue(key, out BookSuggestion[]? books) || books is null)
        {
            try
            {
                // Books whose own genre list has it (any reader's tag would match very popular books in any genre). A dictionary
                // keeps the "Genre" key's capital, which the request's camelCase naming would lower.
                var list = (await hardcover.ListBooksAsync(
                [
                    CatalogueFilter(new()
                    {
                        ["cached_tags"] = new { _contains = new Dictionary<string, object> { ["Genre"] = new[] { new { tag = genre } } } },
                    }),
                ], GenreFetchSize, ct))[0];
                // And only where it's one of the book's main genres, not its eighth.
                var inGenre = list.Where(b => (b.Genres ?? []).Take(GenreTopTags)
                    .Any(t => string.Equals(t.Tag, genre, StringComparison.OrdinalIgnoreCase)));
                books = OnePerSeries(inGenre).Take(ListSize).Select(ToSuggestion).ToArray();
            }
            catch (BookSourceUnavailableException)
            {
                return new GenrePicks(genre, []); // The rest of the page still loads; try again next time.
            }
            cache.Set(key, books, new MemoryCacheEntryOptions { Size = 1, AbsoluteExpirationRelativeToNow = DiscoverCacheDuration });
        }
        return new GenrePicks(genre, await suppression.ApplyAsync(books, ct));
    }

    /// <summary>
    /// For each series the reader has read or is reading, the next numbered book, unless it's already in their library.
    /// Only a book's main series counts, not umbrella series such as The Cosmere.
    /// </summary>
    public async Task<BookSuggestion[]> GetNextInSeriesAsync(Guid userId, CancellationToken ct)
    {
        var entries = await db.LibraryEntries
            .Where(e => e.UserId == userId)
            .Select(e => new
            {
                e.Status,
                e.UpdatedAt,
                e.Book.HardcoverId,
                Series = e.Book.Series.Select(bs => new { bs.Series.HardcoverId, bs.Position, bs.IsFeatured }).ToList(),
            })
            .ToListAsync(ct);
        var owned = entries.Where(e => e.HardcoverId is not null).Select(e => e.HardcoverId!.Value).ToHashSet();

        var inProgress = entries
            .Where(e => e.Status is ReadingStatus.Read or ReadingStatus.Reading)
            .Select(e => (e.UpdatedAt, Series: e.Series
                .OrderByDescending(s => s.IsFeatured)
                .ThenBy(s => s.Position ?? decimal.MaxValue)
                .FirstOrDefault()))
            .Where(e => e.Series is { HardcoverId: not null, Position: not null })
            .GroupBy(e => e.Series!.HardcoverId!.Value)
            .Select(g => (SeriesId: g.Key, Reached: g.Max(e => e.Series!.Position!.Value), LastActive: g.Max(e => e.UpdatedAt)))
            .OrderByDescending(s => s.LastActive)
            .Take(MaxSeries);

        var suggestions = new List<BookSuggestion>();
        foreach (var (seriesId, reached, _) in inProgress)
        {
            SeriesDetail? series;
            try
            {
                series = await seriesService.GetByHardcoverIdAsync((int)seriesId, ct);
            }
            catch (BookSourceUnavailableException)
            {
                continue; // Show the series that did load.
            }
            // If the next book is already on a shelf, the reader knows about it; don't skip ahead to the one after.
            var next = series?.Books.Where(b => b.Position > reached).MinBy(b => b.Position);
            if (next is not null && !owned.Contains(next.HardcoverId))
            {
                suggestions.Add(new BookSuggestion(
                    next.HardcoverId, next.Title, next.Authors, next.ReleaseYear, next.CoverUrl, series!.Name, next.Position));
            }
        }
        return suggestions.ToArray();
    }

    private async Task<RelatedBooks> FetchRelatedAsync(int hardcoverId, CancellationToken ct)
    {
        var book = await hardcover.GetBookTagsAsync(hardcoverId, ct);
        if (book is null)
        {
            return new RelatedBooks([], null, []);
        }
        var author = book.Contributions.Where(IsAuthor).Select(c => c.Author).FirstOrDefault();
        var authorIds = book.Contributions.Where(IsAuthor).Select(c => c.Author.Id).ToHashSet();
        var seriesId = MainSeries(book.BookSeries)?.Series!.Id;

        var tierFilters = SimilarTagTiers(book)
            .Select(tagIds => (object)CatalogueFilter(new()
            {
                ["_and"] = tagIds.Select(id => new { taggings = new { tag_id = new { _eq = id } } }).ToArray(),
                ["users_count"] = new { _gte = MinReaders },
                ["id"] = new { _neq = hardcoverId },
            }))
            .ToList();
        var firstFilters = tierFilters.Take(1).ToList();
        if (author is not null)
        {
            firstFilters.Add(CatalogueFilter(new()
            {
                ["contributions"] = new { author_id = new { _eq = author.Id } },
                ["id"] = new { _neq = hardcoverId },
            }));
        }
        if (firstFilters.Count == 0)
        {
            return new RelatedBooks([], null, []);
        }

        bool OtherSeries(HardcoverListedBook b) => seriesId is null || MainSeries(b.BookSeries)?.Series!.Id != seriesId;

        // Books by the same author or in the same series have their own sections. Suggest only standalones and the
        // start of a series, not book 4 of something the reader hasn't begun.
        List<HardcoverListedBook> Similar(IEnumerable<HardcoverListedBook> candidates) =>
            OnePerSeries(candidates
                    .DistinctBy(b => b.Id)
                    .Where(b => !b.Contributions.Any(c => IsAuthor(c) && authorIds.Contains(c.Author.Id)))
                    .Where(OtherSeries)
                    .Where(b => MainSeries(b.BookSeries)?.Position is not > 1))
                .Take(ListSize)
                .ToList();

        // The strictest tags and the author's books in one request; looser tags only when needed to fill the list,
        // since Hardcover counts every list against its limits.
        var first = await hardcover.ListBooksAsync(firstFilters, FetchSize, ct);
        var candidates = tierFilters.Count > 0 ? first[0].ToList() : [];
        foreach (var filter in tierFilters.Skip(1))
        {
            if (Similar(candidates).Count >= ListSize)
            {
                break;
            }
            candidates.AddRange((await hardcover.ListBooksAsync([filter], FetchSize, ct))[0]);
        }

        // Hardcover lists an author's split volumes, translations, and duplicate records too; they have few readers.
        var authorBooks = author is null ? [] : first[^1];
        var mostReaders = authorBooks.Select(b => b.UsersCount).DefaultIfEmpty(0).Max();
        var byAuthor = authorBooks
            .Where(b => b.Contributions.Any(c => IsAuthor(c) && c.Author.Id == author!.Id))
            .Where(b => b.UsersCount >= mostReaders * MinAuthorReadersShare)
            .Where(OtherSeries);

        return new RelatedBooks(
            Similar(candidates).Select(ToSuggestion).ToArray(),
            author?.Name,
            OnePerSeries(byAuthor).Take(ListSize).Select(ToSuggestion).ToArray());
    }

    /// <summary>Sets of tag ids to match, strictest first. Empty if the book has no usable genres.</summary>
    private static List<int[]> SimilarTagTiers(HardcoverBookTags book)
    {
        var tags = book.Taggings
            .Select(t => t.Tag)
            .Where(t => t.Count >= MinTagBooks)
            .DistinctBy(t => (t.TagCategory.Slug, t.Tag))
            .ToDictionary(t => (t.TagCategory.Slug, t.Tag));

        // cached_tags lists each category's tags with the most votes first. Less-read books often have only one vote
        // per tag, so use those when nothing has more.
        List<HardcoverTagDetail> Strongest(string category, string slug, int count)
        {
            var known = (book.CachedTags?.GetValueOrDefault(category) ?? [])
                .Select(t => (t.Count, Tag: tags.GetValueOrDefault((slug, t.Tag))))
                .Where(t => t.Tag is not null)
                .ToList();
            var voted = known.Any(t => t.Count >= MinTagVotes) ? known.Where(t => t.Count >= MinTagVotes) : known;
            return voted.Select(t => t.Tag!).DistinctBy(t => t.Id).Take(count).ToList();
        }

        var genres = Strongest("Genre", "genre", GenresConsidered);
        if (genres.Count == 0)
        {
            return [];
        }
        var moods = Strongest("Mood", "mood", 2).Select(t => t.Id).ToArray();
        // "Epic Fantasy" says more than "Fantasy", so prefer the genres on the fewest books.
        var specific = genres.OrderBy(g => g.Count).Take(2).Select(g => g.Id).ToArray();

        int[][] tiers = [[.. specific, .. moods], [.. specific, .. moods.Take(1)], [specific[0], .. moods.Take(1)], [genres[0].Id]];
        return tiers.DistinctBy(t => string.Join(",", t.Order())).ToList();
    }

    /// <summary>A Hardcover books filter that leaves out merged and removed books and box sets.</summary>
    private static Dictionary<string, object> CatalogueFilter(Dictionary<string, object> conditions)
    {
        conditions["canonical_id"] = new { _is_null = true };
        conditions["book_status_id"] = new { _eq = 1 };
        conditions["compilation"] = new { _eq = false };
        return conditions;
    }

    /// <summary>
    /// One book per series, so a long series can't fill a list: the earliest in the series of those given (or, if not
    /// <paramref name="earliestInSeries"/>, the first in the list), in the place of the series' first book in the list.
    /// </summary>
    private static IEnumerable<HardcoverListedBook> OnePerSeries(IEnumerable<HardcoverListedBook> books, bool earliestInSeries = true)
    {
        var list = books.ToList();
        var earliest = list
            .Where(b => MainSeries(b.BookSeries) is not null)
            .GroupBy(b => MainSeries(b.BookSeries)!.Series!.Id)
            .ToDictionary(g => g.Key, g => g.MinBy(b => MainSeries(b.BookSeries)!.Position ?? decimal.MaxValue)!);
        var seen = new HashSet<int>();
        foreach (var book in list)
        {
            if (MainSeries(book.BookSeries)?.Series!.Id is not { } seriesId)
            {
                yield return book;
            }
            else if (seen.Add(seriesId))
            {
                yield return earliestInSeries ? earliest[seriesId] : book;
            }
        }
    }

    /// <summary>The series to show first for a book, as BookService stores it: Hardcover's featured series.</summary>
    private static HardcoverBookSeries? MainSeries(HardcoverBookSeries[] series) =>
        series
            .Where(bs => bs.Series is not null)
            .OrderByDescending(bs => bs.Featured)
            .ThenBy(bs => bs.Position ?? decimal.MaxValue)
            .FirstOrDefault();

    private static bool IsAuthor(HardcoverContribution c) =>
        string.IsNullOrEmpty(c.Contribution) || c.Contribution.Equals("author", StringComparison.OrdinalIgnoreCase);

    private static BookSuggestion ToSuggestion(HardcoverListedBook book)
    {
        var series = MainSeries(book.BookSeries);
        return new BookSuggestion(
            book.Id,
            book.Title,
            book.Contributions.Where(IsAuthor).Select(c => c.Author.Name).Distinct().ToArray(),
            book.ReleaseYear,
            book.CachedImage?.Url,
            series?.Series!.Name,
            series?.Position);
    }
}
