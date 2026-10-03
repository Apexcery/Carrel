using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Books;

// Updating a book's authors and editions from either source.
public partial class BookService
{
    /// <summary>An author credit from a source; Role is the source's contribution type, null meaning author.</summary>
    private record AuthorData(long? HardcoverId, string? OpenLibraryKey, string Name, string? Role);

    private record EditionData(
        long? HardcoverEditionId,
        string? OpenLibraryEditionKey,
        string? Isbn13,
        string? Isbn10,
        EditionFormat? Format,
        int? PageCount,
        int? AudioSeconds,
        string? Publisher,
        DateOnly? ReleaseDate,
        string? Language,
        string? CoverUrl);

    /// <summary>Replaces the book's credits with these, reusing stored authors with the same source id.</summary>
    private async Task SyncAuthorsAsync(Book book, IReadOnlyList<AuthorData> sources, CancellationToken ct)
    {
        var hardcoverIds = sources.Select(s => s.HardcoverId).OfType<long>().ToList();
        var openLibraryKeys = sources.Select(s => s.OpenLibraryKey).OfType<string>().ToList();
        var authors = await db.Authors
            .Where(a => (a.HardcoverId != null && hardcoverIds.Contains(a.HardcoverId.Value))
                        || (a.OpenLibraryAuthorKey != null && openLibraryKeys.Contains(a.OpenLibraryAuthorKey)))
            .ToListAsync(ct);

        var wanted = new List<(Author Author, string Role)>();
        foreach (var source in sources)
        {
            var author = authors.FirstOrDefault(a =>
                (source.HardcoverId is not null && a.HardcoverId == source.HardcoverId)
                || (source.OpenLibraryKey is not null && a.OpenLibraryAuthorKey == source.OpenLibraryKey));
            if (author is null)
            {
                author = new Author { Name = source.Name, HardcoverId = source.HardcoverId, OpenLibraryAuthorKey = source.OpenLibraryKey };
                authors.Add(author);
            }
            author.Name = source.Name;
            var role = string.IsNullOrWhiteSpace(source.Role) ? "author" : source.Role.Trim().ToLowerInvariant();
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

    /// <summary>
    /// Adds or updates editions, matching the book's existing ones by source id, then ISBN. Editions are never
    /// removed, since library entries may point at them. ISBNs and source ids already used by another book's
    /// editions are left off (sources occasionally have duplicates).
    /// </summary>
    private async Task SyncEditionsAsync(Book book, IReadOnlyList<EditionData> sources, CancellationToken ct)
    {
        var hardcoverIds = sources.Select(s => s.HardcoverEditionId).OfType<long>().ToList();
        var openLibraryKeys = sources.Select(s => s.OpenLibraryEditionKey).OfType<string>().ToList();
        var isbns = sources.SelectMany(s => new[] { s.Isbn13, s.Isbn10 }).OfType<string>().ToList();
        var others = await db.Editions
            .Where(e => e.BookId != book.Id)
            .Where(e => (e.HardcoverEditionId != null && hardcoverIds.Contains(e.HardcoverEditionId.Value))
                        || (e.OpenLibraryEditionKey != null && openLibraryKeys.Contains(e.OpenLibraryEditionKey))
                        || (e.Isbn13 != null && isbns.Contains(e.Isbn13))
                        || (e.Isbn10 != null && isbns.Contains(e.Isbn10)))
            .Select(e => new { e.HardcoverEditionId, e.OpenLibraryEditionKey, e.Isbn13, e.Isbn10 })
            .ToListAsync(ct);
        var otherHardcoverIds = others.Select(o => o.HardcoverEditionId).OfType<long>().ToHashSet();
        var otherOpenLibraryKeys = others.Select(o => o.OpenLibraryEditionKey).OfType<string>().ToHashSet();
        var otherIsbns = others.SelectMany(o => new[] { o.Isbn13, o.Isbn10 }).OfType<string>().ToHashSet();

        bool IsbnFree(string isbn, Edition target) =>
            !otherIsbns.Contains(isbn) && !book.Editions.Any(e => e != target && (e.Isbn13 == isbn || e.Isbn10 == isbn));

        var updated = new HashSet<Edition>();
        foreach (var source in sources)
        {
            if ((source.HardcoverEditionId is { } hcId && otherHardcoverIds.Contains(hcId))
                || (source.OpenLibraryEditionKey is { } olKey && otherOpenLibraryKeys.Contains(olKey)))
            {
                continue;
            }

            var edition = book.Editions.FirstOrDefault(e =>
                              (source.HardcoverEditionId is not null && e.HardcoverEditionId == source.HardcoverEditionId)
                              || (source.OpenLibraryEditionKey is not null && e.OpenLibraryEditionKey == source.OpenLibraryEditionKey))
                          ?? book.Editions.FirstOrDefault(e => !updated.Contains(e)
                              && ((source.Isbn13 is not null && e.Isbn13 == source.Isbn13)
                                  || (source.Isbn10 is not null && e.Isbn10 == source.Isbn10)));
            if (edition is null)
            {
                edition = new Edition();
                book.Editions.Add(edition);
            }
            if (!updated.Add(edition))
            {
                continue;
            }

            edition.HardcoverEditionId ??= source.HardcoverEditionId;
            edition.OpenLibraryEditionKey ??= source.OpenLibraryEditionKey;
            if (source.Isbn13 is { } isbn13 && edition.Isbn13 != isbn13 && IsbnFree(isbn13, edition))
            {
                edition.Isbn13 = isbn13;
            }
            if (source.Isbn10 is { } isbn10 && edition.Isbn10 != isbn10 && IsbnFree(isbn10, edition))
            {
                edition.Isbn10 = isbn10;
            }
            edition.Format = source.Format ?? edition.Format;
            edition.PageCount = source.PageCount ?? edition.PageCount;
            edition.AudioSeconds = source.AudioSeconds ?? edition.AudioSeconds;
            edition.Publisher = source.Publisher ?? edition.Publisher;
            edition.ReleaseDate = source.ReleaseDate ?? edition.ReleaseDate;
            edition.Language = source.Language ?? edition.Language;
            edition.CoverUrl = source.CoverUrl ?? edition.CoverUrl;
        }
    }
}
