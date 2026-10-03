using System.Globalization;
using System.Linq.Expressions;
using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;

namespace Carrel.Api.Books;

/// <summary>Book search and details. Books are stored when someone opens them, then refreshed when stale.</summary>
public class BookService(CarrelDbContext db, HardcoverClient hardcover, IMemoryCache cache)
{
    private static readonly TimeSpan RefreshAfter = TimeSpan.FromDays(30);
    private static readonly TimeSpan SearchCacheDuration = TimeSpan.FromHours(1);
    private const int SearchPageSize = 20;
    private const int MaxGenres = 5;

    // Serialises imports so two requests for the same new book don't both insert it.
    // Sufficient while Cloud Run runs at most one instance.
    private static readonly SemaphoreSlim ImportLock = new(1, 1);

    public async Task<BookSearchResponse> SearchAsync(string query, int page, CancellationToken ct)
    {
        query = query.Trim();
        var key = $"hardcover-search:{page}:{query.ToLowerInvariant()}";
        if (cache.TryGetValue(key, out BookSearchResponse? cached))
        {
            return cached!;
        }

        var results = await hardcover.SearchBooksAsync(query, page, SearchPageSize, ct);
        var response = new BookSearchResponse(results.Found, results.Hits.Select(h => ToSearchResult(h.Document)).ToArray());
        cache.Set(key, response, new MemoryCacheEntryOptions { Size = 1, AbsoluteExpirationRelativeToNow = SearchCacheDuration });
        return response;
    }

    public async Task<BookDetail?> GetByHardcoverIdAsync(int hardcoverId, CancellationToken ct)
    {
        var book = await LoadAsync(b => b.HardcoverId == hardcoverId, ct);
        book = await RefreshIfStaleAsync(book, hardcoverId, ct);
        return book is null ? null : ToDetail(book);
    }

    public async Task<BookDetail?> GetByIdAsync(long id, CancellationToken ct)
    {
        var book = await LoadAsync(b => b.Id == id, ct);
        if (book?.HardcoverId is { } hardcoverId)
        {
            book = await RefreshIfStaleAsync(book, (int)hardcoverId, ct);
        }
        return book is null ? null : ToDetail(book);
    }

    private static bool IsFresh(Book book) => book.FetchedAt > DateTimeOffset.UtcNow - RefreshAfter;

    private async Task<Book?> RefreshIfStaleAsync(Book? stored, int hardcoverId, CancellationToken ct)
    {
        if (stored is not null && IsFresh(stored))
        {
            return stored;
        }
        try
        {
            return await ImportAsync(hardcoverId, ct) ?? stored;
        }
        catch (BookSourceUnavailableException) when (stored is not null)
        {
            // Stale data beats no data.
            return stored;
        }
    }

    private Task<Book?> LoadAsync(Expression<Func<Book, bool>> predicate, CancellationToken ct) =>
        db.Books
            .Include(b => b.Authors).ThenInclude(ba => ba.Author)
            .Include(b => b.Series).ThenInclude(bs => bs.Series)
            .Include(b => b.Genres).ThenInclude(bg => bg.Genre)
            .Include(b => b.Editions)
            .AsSplitQuery()
            .FirstOrDefaultAsync(predicate, ct);

    private async Task<Book?> ImportAsync(int hardcoverId, CancellationToken ct)
    {
        await ImportLock.WaitAsync(ct);
        try
        {
            // Another request may have imported it while this one waited.
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
            if (book is null)
            {
                book = new Book { Title = "" };
                db.Books.Add(book);
            }

            ApplyBook(book, source);
            await SyncAuthorsAsync(book, source, ct);
            await SyncSeriesAsync(book, source, ct);
            await SyncGenresAsync(book, source, ct);
            await SyncEditionsAsync(book, source, ct);
            await db.SaveChangesAsync(ct);
            return book;
        }
        finally
        {
            ImportLock.Release();
        }
    }

    private static void ApplyBook(Book book, HardcoverBook source)
    {
        book.Title = string.IsNullOrWhiteSpace(source.Title) ? "Untitled" : source.Title;
        book.Subtitle = source.Subtitle;
        if (!string.IsNullOrWhiteSpace(source.Description))
        {
            book.Description = source.Description;
            book.DescriptionSource = DescriptionSource.Hardcover;
        }
        book.CoverUrl = source.CachedImage?.Url;
        book.FirstPublishedYear = source.ReleaseYear;
        book.HardcoverId = source.Id;
        book.HardcoverRating = source.Rating is { } rating ? Math.Round(rating, 2) : null;
        book.HardcoverRatingsCount = source.RatingsCount;
        book.FetchedAt = DateTimeOffset.UtcNow;
    }

    private async Task SyncAuthorsAsync(Book book, HardcoverBook source, CancellationToken ct)
    {
        var ids = source.Contributions.Select(c => (long)c.Author.Id).Distinct().ToList();
        var authors = await db.Authors
            .Where(a => a.HardcoverId != null && ids.Contains(a.HardcoverId.Value))
            .ToDictionaryAsync(a => a.HardcoverId!.Value, ct);

        var wanted = new List<(Author Author, string Role)>();
        foreach (var contribution in source.Contributions)
        {
            if (!authors.TryGetValue(contribution.Author.Id, out var author))
            {
                author = new Author { Name = contribution.Author.Name, HardcoverId = contribution.Author.Id };
                authors[contribution.Author.Id] = author;
            }
            author.Name = contribution.Author.Name;
            var role = string.IsNullOrWhiteSpace(contribution.Contribution) ? "author" : contribution.Contribution.Trim().ToLowerInvariant();
            if (!wanted.Contains((author, role)))
            {
                wanted.Add((author, role));
            }
        }

        book.Authors.RemoveAll(ba => !wanted.Contains((ba.Author, ba.Role)));
        for (var position = 0; position < wanted.Count; position++)
        {
            var (author, role) = wanted[position];
            var link = book.Authors.FirstOrDefault(ba => ba.Author == author && ba.Role == role);
            if (link is null)
            {
                book.Authors.Add(new BookAuthor { Author = author, Role = role, Position = position });
            }
            else
            {
                link.Position = position;
            }
        }
    }

    private async Task SyncSeriesAsync(Book book, HardcoverBook source, CancellationToken ct)
    {
        var entries = source.BookSeries
            .Where(bs => bs.Series is not null)
            .Select(bs => (bs.Position, Series: bs.Series!))
            .DistinctBy(bs => bs.Series.Id)
            .ToList();
        var ids = entries.Select(bs => (long)bs.Series.Id).ToList();
        var series = await db.Series
            .Where(s => s.HardcoverId != null && ids.Contains(s.HardcoverId.Value))
            .ToDictionaryAsync(s => s.HardcoverId!.Value, ct);

        var wanted = new Dictionary<Series, decimal?>();
        foreach (var entry in entries)
        {
            if (!series.TryGetValue(entry.Series.Id, out var item))
            {
                item = new Series { Name = entry.Series.Name, HardcoverId = entry.Series.Id };
                series[entry.Series.Id] = item;
            }
            item.Name = entry.Series.Name;
            wanted[item] = entry.Position;
        }

        book.Series.RemoveAll(bs => !wanted.ContainsKey(bs.Series));
        foreach (var (item, position) in wanted)
        {
            var link = book.Series.FirstOrDefault(bs => bs.Series == item);
            if (link is null)
            {
                book.Series.Add(new BookSeries { Series = item, Position = position });
            }
            else
            {
                link.Position = position;
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

    private async Task SyncEditionsAsync(Book book, HardcoverBook source, CancellationToken ct)
    {
        var ids = source.Editions.Select(e => (long)e.Id).ToList();
        var existing = await db.Editions
            .Where(e => e.HardcoverEditionId != null && ids.Contains(e.HardcoverEditionId.Value))
            .ToDictionaryAsync(e => e.HardcoverEditionId!.Value, ct);

        // ISBNs are unique across editions; Hardcover occasionally has duplicates, so the first edition keeps it.
        var isbns = source.Editions.SelectMany(e => new[] { e.Isbn13, e.Isbn10 }).OfType<string>().ToList();
        var usedIsbns = (await db.Editions
                .Where(e => (e.Isbn13 != null && isbns.Contains(e.Isbn13)) || (e.Isbn10 != null && isbns.Contains(e.Isbn10)))
                .Where(e => e.HardcoverEditionId == null || !ids.Contains(e.HardcoverEditionId.Value))
                .Select(e => new { e.Isbn13, e.Isbn10 })
                .ToListAsync(ct))
            .SelectMany(e => new[] { e.Isbn13, e.Isbn10 })
            .OfType<string>()
            .ToHashSet();
        string? ClaimIsbn(string? isbn) => isbn is not null && usedIsbns.Add(isbn) ? isbn : null;

        foreach (var sourceEdition in source.Editions)
        {
            if (!existing.TryGetValue(sourceEdition.Id, out var edition))
            {
                edition = new Edition { HardcoverEditionId = sourceEdition.Id };
                book.Editions.Add(edition);
            }
            else if (edition.BookId != book.Id)
            {
                // Hardcover moved this edition to another book.
                edition.Book = book;
            }

            edition.Isbn13 = ClaimIsbn(sourceEdition.Isbn13);
            edition.Isbn10 = ClaimIsbn(sourceEdition.Isbn10);
            edition.Format = sourceEdition.ReadingFormatId switch
            {
                1 or 3 => EditionFormat.Print,
                2 => EditionFormat.Audio,
                4 => EditionFormat.Ebook,
                _ => null,
            };
            edition.PageCount = sourceEdition.Pages > 0 ? sourceEdition.Pages : null;
            edition.AudioSeconds = sourceEdition.AudioSeconds > 0 ? sourceEdition.AudioSeconds : null;
            edition.Publisher = sourceEdition.Publisher?.Name;
            edition.ReleaseDate = DateOnly.TryParseExact(sourceEdition.ReleaseDate, "yyyy-MM-dd", CultureInfo.InvariantCulture,
                DateTimeStyles.None, out var date) ? date : null;
            edition.Language = sourceEdition.Language?.Code2;
            edition.CoverUrl = sourceEdition.CachedImage?.Url;
        }
    }

    private static BookSearchResult ToSearchResult(HardcoverSearchDocument doc) => new(
        int.Parse(doc.Id, CultureInfo.InvariantCulture),
        doc.Title,
        doc.Subtitle,
        doc.AuthorNames ?? [],
        doc.ReleaseYear,
        doc.Image?.Url,
        doc.Rating is { } rating ? Math.Round(rating, 2) : null,
        doc.RatingsCount,
        doc.FeaturedSeries?.Series?.Name,
        doc.FeaturedSeries?.Series is null ? null : doc.FeaturedSeries.Position);

    private static BookDetail ToDetail(Book book) => new(
        book.Id,
        book.HardcoverId,
        book.Title,
        book.Subtitle,
        book.Description,
        book.DescriptionSource,
        book.CoverUrl,
        book.FirstPublishedYear,
        book.HardcoverRating,
        book.HardcoverRatingsCount,
        book.Authors.OrderBy(ba => ba.Position).Select(ba => new ContributorDto(ba.Author.Id, ba.Author.Name, ba.Role)).ToArray(),
        book.Series.OrderBy(bs => bs.Series.Name).Select(bs => new SeriesEntryDto(bs.Series.Id, bs.Series.Name, bs.Position)).ToArray(),
        book.Genres.Select(bg => bg.Genre.Name).Order().ToArray(),
        book.Editions.OrderBy(e => e.Id).Select(e => new EditionDto(e.Id, e.Isbn13, e.Isbn10, e.Format, e.PageCount, e.AudioSeconds,
            e.Publisher, e.ReleaseDate, e.Language, e.CoverUrl)).ToArray());
}
