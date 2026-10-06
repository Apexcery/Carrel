using System.Globalization;
using Carrel.Api.Data;
using Carrel.Api.Imports;
using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Library;

public enum ExportFormat
{
    /// <summary>Goodreads' export columns, which Goodreads, StoryGraph, and Carrel can all import.</summary>
    Goodreads,
    /// <summary>Carrel's own columns (<see cref="CarrelCsv"/>), with everything, for a backup or a move back.</summary>
    Carrel,
}

/// <summary>The reader's library as a CSV file, newest additions first, as Goodreads orders its exports.</summary>
public class LibraryExport(CarrelDbContext db)
{
    /// <summary>Goodreads' columns, exactly as its own exports have them.</summary>
    private static readonly string[] GoodreadsColumns =
    [
        "Book Id", "Title", "Author", "Author l-f", "Additional Authors", "ISBN", "ISBN13", "My Rating", "Publisher",
        "Binding", "Number of Pages", "Year Published", "Original Publication Year", "Date Read", "Date Added",
        "Bookshelves", "Bookshelves with positions", "Exclusive Shelf", "My Review", "Spoiler", "Private Notes",
        "Read Count", "Owned Copies",
    ];

    /// <summary>Goodreads' shelf names; paused and did-not-finish are custom shelves there, which Carrel reads back.</summary>
    private static readonly Dictionary<ReadingStatus, string> GoodreadsShelves = new()
    {
        [ReadingStatus.WantToRead] = "to-read",
        [ReadingStatus.Reading] = "currently-reading",
        [ReadingStatus.Paused] = "paused",
        [ReadingStatus.Read] = "read",
        [ReadingStatus.DidNotFinish] = "did-not-finish",
    };

    private const string GoodreadsDate = "yyyy/MM/dd";

    public async Task<string> WriteAsync(Guid userId, ExportFormat format, CancellationToken ct)
    {
        var entries = await db.LibraryEntries
            .Where(e => e.UserId == userId)
            .Include(e => e.Reads)
            .Include(e => e.Edition)
            .Include(e => e.Book).ThenInclude(b => b.Authors).ThenInclude(ba => ba.Author)
            .Include(e => e.Book).ThenInclude(b => b.Series).ThenInclude(bs => bs.Series)
            .Include(e => e.Book).ThenInclude(b => b.Editions)
            .AsSplitQuery()
            .OrderByDescending(e => e.AddedAt)
            .ToListAsync(ct);

        if (format == ExportFormat.Carrel)
        {
            return CarrelCsv.Write(entries.Select(CarrelRecord).Prepend(CarrelCsv.Columns));
        }
        // Goodreads ids from the reader's Goodreads imports let Goodreads and StoryGraph match those books exactly.
        var goodreadsIds = (await db.ImportItems
                .Where(i => i.Import.UserId == userId && i.GoodreadsId != null && i.BookId != null)
                .Select(i => new { BookId = i.BookId!.Value, i.GoodreadsId })
                .ToListAsync(ct))
            .DistinctBy(i => i.BookId)
            .ToDictionary(i => i.BookId, i => i.GoodreadsId);
        return CarrelCsv.Write(entries.Select(e => GoodreadsRecord(e, goodreadsIds.GetValueOrDefault(e.BookId))).Prepend(GoodreadsColumns));
    }

    private static string?[] GoodreadsRecord(LibraryEntry entry, string? goodreadsId)
    {
        var book = entry.Book;
        var authors = Authors(book);
        var edition = IsbnEdition(entry);
        var shelf = GoodreadsShelves[entry.Status];
        var finished = entry.Reads.Count(r => r.FinishedOn is not null || r.FinishedDateUnknown);
        return
        [
            goodreadsId,
            book.Title,
            authors.FirstOrDefault(),
            authors.FirstOrDefault() is { } first ? LastFirst(first) : null,
            string.Join(", ", authors.Skip(1)),
            // Goodreads wraps ISBNs as ="…" so spreadsheets keep their leading zeros.
            $"=\"{edition?.Isbn10}\"",
            $"=\"{edition?.Isbn13}\"",
            // Whole stars only, rounded down, but never to 0, which means unrated.
            entry.Rating is { } rating ? Math.Max(1, (int)Math.Floor(rating)).ToString(CultureInfo.InvariantCulture) : "0",
            edition?.Publisher,
            edition?.Format switch
            {
                EditionFormat.Ebook => "ebook",
                EditionFormat.Audio => "Audible Audio",
                _ => null,
            },
            (edition?.PageCount ?? PageCount(entry))?.ToString(CultureInfo.InvariantCulture),
            edition?.ReleaseDate?.Year.ToString(CultureInfo.InvariantCulture),
            book.FirstPublishedYear?.ToString(CultureInfo.InvariantCulture),
            // Goodreads keeps one finish date: the latest.
            entry.Reads.Max(r => r.FinishedOn)?.ToString(GoodreadsDate, CultureInfo.InvariantCulture),
            entry.AddedAt.UtcDateTime.ToString(GoodreadsDate, CultureInfo.InvariantCulture),
            // Goodreads lists every shelf but read here.
            entry.Status == ReadingStatus.Read ? null : shelf,
            null,
            shelf,
            null,
            null,
            null,
            // Finished reads, and the one under way: Goodreads counts a book being read as a read.
            (finished + (entry.Status is ReadingStatus.Reading or ReadingStatus.Paused ? 1 : 0)).ToString(CultureInfo.InvariantCulture),
            "0",
        ];
    }

    private static string?[] CarrelRecord(LibraryEntry entry)
    {
        var book = entry.Book;
        var series = book.Series.OrderByDescending(bs => bs.IsFeatured).ThenBy(bs => bs.Position ?? decimal.MaxValue).FirstOrDefault();
        var edition = IsbnEdition(entry);
        return
        [
            book.Title,
            string.Join($"{CarrelCsv.AuthorSeparator} ", Authors(book)),
            series?.Series.Name,
            series?.Position?.ToString(CultureInfo.InvariantCulture),
            edition?.Isbn13,
            edition?.Isbn10,
            book.HardcoverId?.ToString(CultureInfo.InvariantCulture),
            entry.Edition?.HardcoverEditionId?.ToString(CultureInfo.InvariantCulture),
            CarrelCsv.Statuses[entry.Status],
            entry.Rating?.ToString(CultureInfo.InvariantCulture),
            entry.ProgressUnit is { } unit ? CarrelCsv.ProgressUnits[unit] : null,
            entry.ProgressValue?.ToString(CultureInfo.InvariantCulture),
            entry.ProgressPercent?.ToString(CultureInfo.InvariantCulture),
            entry.AddedAt.UtcDateTime.ToString(CarrelCsv.AddedFormat, CultureInfo.InvariantCulture),
            // Oldest first, as near as is known: reads finished on an unknown date (usually early rereads), then dated
            // reads by finish, then the one under way.
            CarrelCsv.FormatReads(entry.Reads
                .OrderByDescending(r => r.FinishedDateUnknown)
                .ThenBy(r => r.FinishedOn ?? DateOnly.MaxValue)
                .ThenBy(r => r.StartedOn)),
        ];
    }

    private static List<string> Authors(Book book) =>
        book.Authors.Where(ba => ba.Role == "author").OrderBy(ba => ba.Position).Select(ba => ba.Author.Name).ToList();

    /// <summary>The reader's edition, else the first with an ISBN, which is what importers match on.</summary>
    private static Edition? IsbnEdition(LibraryEntry entry) =>
        entry.Edition ?? entry.Book.Editions.OrderBy(e => e.Id).FirstOrDefault(e => e.Isbn13 is not null || e.Isbn10 is not null);

    /// <summary>The first edition with a page count, as shelves show it, when the ISBN edition has none.</summary>
    private static int? PageCount(LibraryEntry entry) =>
        entry.Book.Editions.OrderBy(e => e.Id).Select(e => e.PageCount).FirstOrDefault(p => p > 0);

    /// <summary>"Ursula K. Le Guin" as "Guin, Ursula K. Le": Goodreads splits at the last space too.</summary>
    private static string LastFirst(string name) =>
        name.LastIndexOf(' ') is var space and > 0 ? $"{name[(space + 1)..]}, {name[..space]}" : name;
}
