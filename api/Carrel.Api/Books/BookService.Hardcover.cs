using System.Globalization;
using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Books;

// Importing from Hardcover, the primary source.
public partial class BookService
{
    private const int MaxGenres = 5;

    /// <summary>Null when Hardcover is unavailable or finds nothing, so Open Library is tried instead.</summary>
    private async Task<BookSearchResponse?> SearchHardcoverAsync(string query, int page, CancellationToken ct)
    {
        try
        {
            var results = await hardcover.SearchBooksAsync(query, page, SearchPageSize, ct);
            return results.Found == 0
                ? null
                : new BookSearchResponse(results.Found, results.Hits.Select(h => ToSearchResult(h.Document)).ToArray());
        }
        catch (BookSourceUnavailableException)
        {
            return null;
        }
    }

    private async Task<int?> TryFindHardcoverIdByIsbnAsync(IReadOnlyCollection<string> isbns, CancellationToken ct)
    {
        if (isbns.Count == 0)
        {
            return null;
        }
        try
        {
            return await hardcover.FindBookIdByIsbnAsync(isbns, ct);
        }
        catch (BookSourceUnavailableException)
        {
            return null;
        }
    }

    /// <summary>
    /// Imports or refreshes a Hardcover book. A book stored from Open Library with a matching ISBN (or
    /// <paramref name="mergeInto"/>) becomes the Hardcover book instead of a duplicate. Call inside the import lock.
    /// </summary>
    private async Task<Book?> ImportFromHardcoverAsync(int hardcoverId, Book? mergeInto, CancellationToken ct)
    {
        // Another request may have imported it while this one waited for the lock.
        var book = await LoadAsync(b => b.HardcoverId == hardcoverId, ct);
        if (book is not null && IsFresh(book))
        {
            return book;
        }

        var source = await hardcover.GetBookAsync(hardcoverId, ct);
        if (source is null)
        {
            return null;
        }
        if (source.Id != hardcoverId)
        {
            // Hardcover merged it into a canonical book, which may already be stored.
            book = await LoadAsync(b => b.HardcoverId == source.Id, ct);
        }

        var editions = source.Editions.Select(e => ToEditionData(e)).ToList();
        var isbns = editions.SelectMany(e => new[] { e.Isbn13, e.Isbn10 }).OfType<string>().ToList();
        if (book is not null && mergeInto is not null && book != mergeInto)
        {
            logger.LogWarning("Stored book {BookId} duplicates Hardcover book {HardcoverId}; it needs merging by hand.",
                mergeInto.Id, source.Id);
        }
        book ??= mergeInto ?? await FindBookByIsbnAsync(isbns, openLibraryOnly: true, ct);
        if (book is null)
        {
            book = new Book { Title = "" };
            db.Books.Add(book);
        }

        ApplyHardcover(book, source);
        await SyncAuthorsAsync(book, source.Contributions
            .Select(c => new AuthorData(c.Author.Id, null, c.Author.Name, c.Contribution))
            .ToList(), ct);
        await SyncSeriesAsync(book, source, ct);
        await SyncGenresAsync(book, source, ct);
        await SyncEditionsAsync(book, editions, ct);
        await db.SaveChangesAsync(ct);
        return book;
    }

    private static void ApplyHardcover(Book book, HardcoverBook source)
    {
        book.Title = string.IsNullOrWhiteSpace(source.Title) ? "Untitled" : source.Title;
        book.Subtitle = source.Subtitle;
        if (!string.IsNullOrWhiteSpace(source.Description))
        {
            book.Description = source.Description;
            book.DescriptionSource = DescriptionSource.Hardcover;
        }
        book.CoverUrl = source.CachedImage?.Url ?? book.CoverUrl;
        book.FirstPublishedYear = source.ReleaseYear ?? book.FirstPublishedYear;
        book.HardcoverId = source.Id;
        book.HardcoverRating = source.Rating is { } rating ? Math.Round(rating, 2) : null;
        book.HardcoverRatingsCount = source.RatingsCount;
        book.FetchedAt = DateTimeOffset.UtcNow;
    }

    private async Task SyncSeriesAsync(Book book, HardcoverBook source, CancellationToken ct)
    {
        var entries = source.BookSeries
            .Where(bs => bs.Series is not null)
            .Select(bs => (bs.Position, bs.Featured, Series: bs.Series!))
            .DistinctBy(bs => bs.Series.Id)
            .ToList();
        var ids = entries.Select(bs => (long)bs.Series.Id).ToList();
        var series = await db.Series
            .Where(s => s.HardcoverId != null && ids.Contains(s.HardcoverId.Value))
            .ToDictionaryAsync(s => s.HardcoverId!.Value, ct);

        var wanted = new Dictionary<Series, (decimal? Position, bool Featured)>();
        foreach (var entry in entries)
        {
            if (!series.TryGetValue(entry.Series.Id, out var item))
            {
                item = new Series { Name = entry.Series.Name, HardcoverId = entry.Series.Id };
                series[entry.Series.Id] = item;
            }
            item.Name = entry.Series.Name;
            wanted[item] = (entry.Position, entry.Featured);
        }

        book.Series.RemoveAll(bs => !wanted.ContainsKey(bs.Series));
        foreach (var (item, (position, featured)) in wanted)
        {
            var link = book.Series.FirstOrDefault(bs => bs.Series == item);
            if (link is null)
            {
                book.Series.Add(new BookSeries { Series = item, Position = position, IsFeatured = featured });
            }
            else
            {
                link.Position = position;
                link.IsFeatured = featured;
            }
        }
    }

    private async Task SyncGenresAsync(Book book, HardcoverBook source, CancellationToken ct)
    {
        var names = (source.CachedTags?.GetValueOrDefault("Genre") ?? [])
            .Select(t => t.Tag.Trim())
            .Where(n => n.Length > 0)
            .DistinctBy(n => n.ToLowerInvariant())
            .Take(MaxGenres)
            .ToList();
        var lowerNames = names.Select(n => n.ToLowerInvariant()).ToList();
        var genres = await db.Genres
            .Where(g => lowerNames.Contains(g.Name.ToLower()))
            .ToListAsync(ct);

        var wanted = names
            .Select(n => genres.FirstOrDefault(g => string.Equals(g.Name, n, StringComparison.OrdinalIgnoreCase)) ?? new Genre { Name = n })
            .ToList();

        book.Genres.RemoveAll(bg => !wanted.Contains(bg.Genre));
        foreach (var genre in wanted.Where(g => book.Genres.All(bg => bg.Genre != g)))
        {
            book.Genres.Add(new BookGenre { Genre = genre });
        }
    }

    private static EditionData ToEditionData(HardcoverEdition edition) => new(
        edition.Id,
        null,
        NormalizeIsbn(edition.Isbn13),
        NormalizeIsbn(edition.Isbn10),
        edition.ReadingFormatId switch
        {
            1 or 3 => EditionFormat.Print,
            2 => EditionFormat.Audio,
            4 => EditionFormat.Ebook,
            _ => null,
        },
        edition.Pages > 0 ? edition.Pages : null,
        edition.AudioSeconds > 0 ? edition.AudioSeconds : null,
        edition.Publisher?.Name,
        DateOnly.TryParseExact(edition.ReleaseDate, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var date)
            ? date
            : null,
        edition.Language?.Code2,
        edition.CachedImage?.Url);

    private static BookSearchResult ToSearchResult(HardcoverSearchDocument doc) => new(
        int.Parse(doc.Id, CultureInfo.InvariantCulture),
        null,
        doc.Title,
        doc.Subtitle,
        doc.AuthorNames ?? [],
        doc.ReleaseYear,
        doc.Image?.Url,
        doc.Rating is { } rating ? Math.Round(rating, 2) : null,
        doc.RatingsCount,
        doc.FeaturedSeries?.Series?.Id,
        doc.FeaturedSeries?.Series?.Name,
        doc.FeaturedSeries?.Series is null ? null : doc.FeaturedSeries.Position);
}
