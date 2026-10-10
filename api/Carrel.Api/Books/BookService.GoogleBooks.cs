using Carrel.Api.Data;
using Carrel.Api.Imports;

namespace Carrel.Api.Books;

// Descriptions from Google Books, which carries publishers' own copy; it's preferred to the other sources'.
public partial class BookService
{
    /// <summary>
    /// Takes the description from the book's Google Books volume, unless Google was asked recently. Google is called
    /// outside the import lock and the result saved inside it. If Google is unavailable, it's asked again next time.
    /// </summary>
    private async Task AddGoogleBooksDescriptionAsync(Book book, CancellationToken ct)
    {
        if (!googleBooks.IsConfigured || book.GoogleBooksCheckedAt > DateTimeOffset.UtcNow - RefreshAfter)
        {
            return;
        }

        GoogleBooksVolume? volume;
        try
        {
            volume = await FindGoogleBooksVolumeAsync(book, ct);
        }
        catch (BookSourceUnavailableException)
        {
            return;
        }

        await WithImportLockAsync(async () =>
        {
            book.GoogleBooksCheckedAt = DateTimeOffset.UtcNow;
            // A volume no longer matched keeps the description it gave before.
            if (volume?.VolumeInfo?.Description is { } description && DescriptionCleaner.Clean(description) is not null)
            {
                book.Description = description;
                book.DescriptionSource = DescriptionSource.GoogleBooks;
                book.GoogleBooksId = volume.Id;
            }
            await db.SaveChangesAsync(ct);
            return book;
        }, ct);
    }

    /// <summary>
    /// Google's ISBN search finds nothing, so this searches by title and author, then takes an English volume
    /// sharing an ISBN with one of the book's editions: one the publisher supplied if there is one, then the
    /// earliest-listed edition's.
    /// </summary>
    private async Task<GoogleBooksVolume?> FindGoogleBooksVolumeAsync(Book book, CancellationToken ct)
    {
        var isbns = book.Editions.OrderBy(e => e.Id).SelectMany(e => new[] { e.Isbn13, e.Isbn10 }).OfType<string>().Distinct().ToList();
        if (isbns.Count == 0)
        {
            return null;
        }

        var author = book.Authors.OrderBy(ba => ba.Position).Select(ba => ba.Author.Name).FirstOrDefault();
        var results = await googleBooks.SearchAsync(TitleMatcher.SearchQuery(book.Title, author is null ? [] : [author]), ct);
        var match = results
            .Where(v => v.VolumeInfo is { Language: "en", Description: not null })
            .Select(v => (Volume: v, Rank: (v.VolumeInfo!.IndustryIdentifiers ?? [])
                .Select(i => isbns.IndexOf(i.Identifier))
                .Where(rank => rank >= 0)
                .DefaultIfEmpty(int.MaxValue)
                .Min()))
            .Where(c => c.Rank < int.MaxValue)
            .OrderByDescending(c => c.Volume.FromPublisher)
            .ThenBy(c => c.Rank)
            .Select(c => c.Volume)
            .FirstOrDefault();
        // Search results flatten the description; the volume itself has it as HTML, with its paragraphs.
        return match is null ? null : await googleBooks.GetVolumeAsync(match.Id, ct);
    }
}
