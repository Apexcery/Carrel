using System.Globalization;
using System.Text.Json;
using System.Text.RegularExpressions;
using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Books;

// Importing from Open Library, the fallback for books Hardcover doesn't have.
public partial class BookService
{
    private const int MaxOpenLibraryAuthors = 5;

    private static readonly string[] OpenLibraryDateFormats = ["yyyy-MM-dd", "MMMM d, yyyy", "MMM d, yyyy", "d MMMM yyyy", "MMM dd, yyyy"];

    private async Task<BookSearchResponse> SearchOpenLibraryAsync(string query, int page, CancellationToken ct)
    {
        var results = await openLibrary.SearchAsync(query, page, SearchPageSize, ct);
        return new BookSearchResponse(results.NumFound, results.Docs.Select(doc => new BookSearchResult(
            null,
            OpenLibraryId(doc.Key),
            doc.Title,
            doc.Subtitle,
            doc.AuthorName ?? [],
            doc.FirstPublishYear,
            OpenLibraryCoverUrl(doc.CoverI),
            null,
            null,
            null,
            null,
            null)).ToArray());
    }

    /// <summary>
    /// Imports or refreshes an Open Library work. If Hardcover or a stored book has any of its ISBNs, that book is
    /// used instead, so the same book isn't stored twice. Call inside the import lock.
    /// </summary>
    private async Task<Book?> ImportFromOpenLibraryAsync(string workId, CancellationToken ct)
    {
        // Another request may have imported it while this one waited for the lock.
        var book = await LoadAsync(b => b.OpenLibraryWorkKey == workId, ct);
        if (book is not null && IsFresh(book))
        {
            return book;
        }

        var work = await openLibrary.GetWorkAsync(workId, ct);
        if (work is null)
        {
            return null;
        }
        var editions = ((await openLibrary.GetEditionsAsync(workId, MaxEditions, ct))?.Entries ?? [])
            .Select(e => ToEditionData(e))
            .ToList();
        var isbns = editions.SelectMany(e => new[] { e.Isbn13, e.Isbn10 }).OfType<string>().Distinct().ToList();

        // Hardcover is the preferred source: use its record when it knows any of these ISBNs.
        if (await TryFindHardcoverIdByIsbnAsync(isbns, ct) is { } hardcoverId
            && await ImportFromHardcoverAsync(hardcoverId, mergeInto: book, ct) is { } hardcoverBook)
        {
            await AttachOpenLibraryWorkAsync(hardcoverBook, workId, ct);
            return hardcoverBook;
        }

        book ??= await FindBookByIsbnAsync(isbns, openLibraryOnly: false, ct);
        if (book is { HardcoverId: not null })
        {
            await AttachOpenLibraryWorkAsync(book, workId, ct);
            return book;
        }
        if (book is null)
        {
            book = new Book { Title = "" };
            db.Books.Add(book);
        }

        ApplyOpenLibrary(book, workId, work, editions);
        await SyncAuthorsAsync(book, await GetOpenLibraryAuthorsAsync(work, ct), ct);
        await SyncEditionsAsync(book, editions, ct);
        await db.SaveChangesAsync(ct);
        return book;
    }

    /// <summary>Records the Open Library work on a book unless another book already has it.</summary>
    private async Task AttachOpenLibraryWorkAsync(Book book, string workId, CancellationToken ct)
    {
        if (book.OpenLibraryWorkKey == workId || await db.Books.AnyAsync(b => b.OpenLibraryWorkKey == workId && b.Id != book.Id, ct))
        {
            return;
        }
        book.OpenLibraryWorkKey = workId;
        await db.SaveChangesAsync(ct);
    }

    private static void ApplyOpenLibrary(Book book, string workId, OpenLibraryWork work, List<EditionData> editions)
    {
        book.Title = string.IsNullOrWhiteSpace(work.Title) ? "Untitled" : work.Title;
        book.Subtitle = work.Subtitle;
        if (DescriptionText(work.Description) is { } description)
        {
            book.Description = description;
            book.DescriptionSource = DescriptionSource.OpenLibrary;
        }
        book.CoverUrl = OpenLibraryCoverUrl(work.Covers?.FirstOrDefault()) ?? editions.Select(e => e.CoverUrl).FirstOrDefault(u => u is not null);
        book.FirstPublishedYear = Year(work.FirstPublishDate) ?? editions.Select(e => e.ReleaseDate?.Year).Min();
        book.OpenLibraryWorkKey = workId;
        book.FetchedAt = DateTimeOffset.UtcNow;
    }

    private async Task<List<AuthorData>> GetOpenLibraryAuthorsAsync(OpenLibraryWork work, CancellationToken ct)
    {
        var keys = (work.Authors ?? [])
            .Select(a => a.Author?.Key)
            .OfType<string>()
            .Select(OpenLibraryId)
            .Distinct()
            .Take(MaxOpenLibraryAuthors)
            .ToList();
        var known = await db.Authors
            .Where(a => a.OpenLibraryAuthorKey != null && keys.Contains(a.OpenLibraryAuthorKey))
            .ToDictionaryAsync(a => a.OpenLibraryAuthorKey!, a => a.Name, ct);

        var authors = new List<AuthorData>();
        foreach (var key in keys)
        {
            var name = known.GetValueOrDefault(key) ?? (await openLibrary.GetAuthorAsync(key, ct))?.Name;
            if (!string.IsNullOrWhiteSpace(name))
            {
                authors.Add(new AuthorData(null, key, name, null));
            }
        }
        return authors;
    }

    private static EditionData ToEditionData(OpenLibraryEdition edition) => new(
        null,
        OpenLibraryId(edition.Key),
        edition.Isbn13?.Select(NormalizeIsbn).FirstOrDefault(i => i is not null),
        edition.Isbn10?.Select(NormalizeIsbn).FirstOrDefault(i => i is not null),
        edition.PhysicalFormat?.ToLowerInvariant() switch
        {
            null or "" => null,
            var f when f.Contains("audio") => EditionFormat.Audio,
            var f when f.Contains("ebook") || f.Contains("e-book") || f.Contains("electronic") => EditionFormat.Ebook,
            _ => EditionFormat.Print,
        },
        edition.NumberOfPages > 0 ? edition.NumberOfPages : null,
        null,
        edition.Publishers?.FirstOrDefault(),
        DateOnly.TryParseExact(edition.PublishDate, OpenLibraryDateFormats, CultureInfo.InvariantCulture, DateTimeStyles.None, out var date)
            ? date
            : null,
        null,
        OpenLibraryCoverUrl(edition.Covers?.FirstOrDefault()));

    /// <summary>"/works/OL45804W" to "OL45804W".</summary>
    private static string OpenLibraryId(string key) => key[(key.LastIndexOf('/') + 1)..];

    private static string? OpenLibraryCoverUrl(long? coverId) =>
        coverId > 0 ? $"https://covers.openlibrary.org/b/id/{coverId}-L.jpg" : null;

    private static string? DescriptionText(JsonElement? description)
    {
        var text = description switch
        {
            { ValueKind: JsonValueKind.String } d => d.GetString(),
            { ValueKind: JsonValueKind.Object } d when d.TryGetProperty("value", out var value) => value.GetString(),
            _ => null,
        };
        return string.IsNullOrWhiteSpace(text) ? null : text.Trim();
    }

    private static int? Year(string? date) =>
        date is not null && FourDigitYear().Match(date) is { Success: true } match ? int.Parse(match.Value, CultureInfo.InvariantCulture) : null;

    [GeneratedRegex(@"\b\d{4}\b")]
    private static partial Regex FourDigitYear();
}
