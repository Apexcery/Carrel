using System.Linq.Expressions;
using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;

namespace Carrel.Api.Books;

/// <summary>
/// Book search and details. Hardcover is the primary source, Open Library the fallback. Books are stored when
/// someone opens them, then refreshed when stale. Source-specific import code is in the other partial files.
/// </summary>
public partial class BookService(
    CarrelDbContext db,
    HardcoverClient hardcover,
    OpenLibraryClient openLibrary,
    IMemoryCache cache,
    ILogger<BookService> logger)
{
    private static readonly TimeSpan RefreshAfter = TimeSpan.FromDays(30);
    private static readonly TimeSpan SearchCacheDuration = TimeSpan.FromHours(1);
    private const int SearchPageSize = 20;
    private const int MaxEditions = 20;

    // Serialises imports so two requests for the same new book don't both insert it.
    // Sufficient while Cloud Run runs at most one instance.
    private static readonly SemaphoreSlim ImportLock = new(1, 1);

    public async Task<BookSearchResponse> SearchAsync(string query, int page, CancellationToken ct)
    {
        query = query.Trim();
        var key = $"book-search:{page}:{query.ToLowerInvariant()}";
        if (cache.TryGetValue(key, out BookSearchResponse? cached))
        {
            return cached!;
        }

        var response = await SearchHardcoverAsync(query, page, ct) ?? await SearchOpenLibraryAsync(query, page, ct);
        cache.Set(key, response, new MemoryCacheEntryOptions { Size = 1, AbsoluteExpirationRelativeToNow = SearchCacheDuration });
        return response;
    }

    public async Task<BookDetail?> GetByIdAsync(long id, CancellationToken ct)
    {
        var book = await LoadAsync(b => b.Id == id, ct);
        if (book is not null)
        {
            book = await RefreshIfStaleAsync(book, () => RefreshStoredAsync(book, ct));
        }
        return book is null ? null : ToDetail(book);
    }

    public async Task<BookDetail?> GetByHardcoverIdAsync(int hardcoverId, CancellationToken ct)
    {
        var book = await LoadAsync(b => b.HardcoverId == hardcoverId, ct);
        book = await RefreshIfStaleAsync(book, () => WithImportLockAsync(() => ImportFromHardcoverAsync(hardcoverId, null, ct), ct));
        return book is null ? null : ToDetail(book);
    }

    public async Task<BookDetail?> GetByOpenLibraryIdAsync(string workId, CancellationToken ct)
    {
        var book = await LoadAsync(b => b.OpenLibraryWorkKey == workId, ct);
        book = await RefreshIfStaleAsync(book, () => WithImportLockAsync(() => ImportFromOpenLibraryAsync(workId, ct), ct));
        return book is null ? null : ToDetail(book);
    }

    /// <summary>Looks a book up by ISBN-10 or ISBN-13: stored books first, then Hardcover, then Open Library.</summary>
    public async Task<BookDetail?> GetByIsbnAsync(string isbn, CancellationToken ct)
    {
        var book = await LoadAsync(b => b.Editions.Any(e => e.Isbn13 == isbn || e.Isbn10 == isbn), ct);
        if (book is not null)
        {
            return ToDetail(await RefreshIfStaleAsync(book, () => RefreshStoredAsync(book, ct)) ?? book);
        }

        if (await TryFindHardcoverIdByIsbnAsync([isbn], ct) is { } hardcoverId)
        {
            return await GetByHardcoverIdAsync(hardcoverId, ct);
        }

        var edition = await openLibrary.GetEditionByIsbnAsync(isbn, ct);
        return edition?.Works?.FirstOrDefault() is { } work
            ? await GetByOpenLibraryIdAsync(OpenLibraryId(work.Key), ct)
            : null;
    }

    /// <summary>Digits (and a final X) of a 10- or 13-character ISBN; null if it isn't one.</summary>
    public static string? NormalizeIsbn(string? isbn)
    {
        var normalized = new string((isbn ?? "").Where(c => char.IsAsciiDigit(c) || c is 'X' or 'x').ToArray()).ToUpperInvariant();
        return normalized.Length is 10 or 13 ? normalized : null;
    }

    private static bool IsFresh(Book book) => book.FetchedAt > DateTimeOffset.UtcNow - RefreshAfter;

    private static async Task<Book?> RefreshIfStaleAsync(Book? stored, Func<Task<Book?>> import)
    {
        if (stored is not null && IsFresh(stored))
        {
            return stored;
        }
        try
        {
            return await import() ?? stored;
        }
        catch (BookSourceUnavailableException) when (stored is not null)
        {
            // Stale data beats no data.
            return stored;
        }
    }

    private Task<Book?> RefreshStoredAsync(Book book, CancellationToken ct) => WithImportLockAsync(() => book switch
    {
        { HardcoverId: { } hardcoverId } => ImportFromHardcoverAsync((int)hardcoverId, null, ct),
        { OpenLibraryWorkKey: { } workId } => ImportFromOpenLibraryAsync(workId, ct),
        _ => Task.FromResult<Book?>(book),
    }, ct);

    private static async Task<T> WithImportLockAsync<T>(Func<Task<T>> action, CancellationToken ct)
    {
        await ImportLock.WaitAsync(ct);
        try
        {
            return await action();
        }
        finally
        {
            ImportLock.Release();
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

    /// <summary>A stored book with an edition having one of these ISBNs.</summary>
    private async Task<Book?> FindBookByIsbnAsync(IReadOnlyCollection<string> isbns, bool openLibraryOnly, CancellationToken ct)
    {
        if (isbns.Count == 0)
        {
            return null;
        }
        var bookId = await db.Editions
            .Where(e => (e.Isbn13 != null && isbns.Contains(e.Isbn13)) || (e.Isbn10 != null && isbns.Contains(e.Isbn10)))
            .Where(e => !openLibraryOnly || e.Book.HardcoverId == null)
            .Select(e => (long?)e.BookId)
            .FirstOrDefaultAsync(ct);
        return bookId is { } id ? await LoadAsync(b => b.Id == id, ct) : null;
    }

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
        // Featured series first (the frontend headlines it), then by position.
        book.Series.OrderByDescending(bs => bs.IsFeatured).ThenBy(bs => bs.Position ?? decimal.MaxValue).ThenBy(bs => bs.Series.Name).Select(bs => new SeriesEntryDto(bs.Series.Id, bs.Series.HardcoverId, bs.Series.Name, bs.Position)).ToArray(),
        book.Genres.Select(bg => bg.Genre.Name).Order().ToArray(),
        book.Editions.OrderBy(e => e.Id).Select(e => new EditionDto(e.Id, e.Isbn13, e.Isbn10, e.Format, e.PageCount, e.AudioSeconds,
            e.Publisher, e.ReleaseDate, e.Language, e.CoverUrl)).ToArray());
}
