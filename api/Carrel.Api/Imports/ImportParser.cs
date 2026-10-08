using System.Globalization;
using System.Text;
using Carrel.Api.Books;
using Carrel.Api.Data;

namespace Carrel.Api.Imports;

/// <summary>Thrown when an upload can't be imported; the message is shown to the reader.</summary>
public class ImportFileException(string message) : Exception(message);

/// <summary>One export row, mapped to Carrel's terms. The fields after Reads only come from Carrel's own exports.</summary>
public record ParsedRow(
    string Title,
    string[] Authors,
    string? Isbn13,
    string? Isbn10,
    string? Asin,
    string? GoodreadsId,
    ReadingStatus Status,
    decimal? Rating,
    DateOnly? AddedOn,
    List<ImportedRead> Reads,
    int? HardcoverBookId = null,
    long? HardcoverEditionId = null,
    DateTimeOffset? AddedAt = null,
    ProgressUnit? ProgressUnit = null,
    decimal? ProgressValue = null,
    decimal? ProgressPercent = null);

/// <summary>Reads Goodreads, StoryGraph, and Carrel CSV exports, telling them apart by their columns.</summary>
public static class ImportParser
{
    public const int MaxRows = 5000;

    private static readonly string[] DateFormats = ["yyyy/MM/dd", "yyyy-MM-dd", "yyyy/M/d"];

    // Goodreads exports custom exclusive shelves under the reader's own names.
    private static readonly string[] DidNotFinishShelves = ["did-not-finish", "dnf", "abandoned", "gave-up", "unfinished"];
    private static readonly string[] PausedShelves = ["paused", "on-hold", "on-pause"];

    public static (ImportSource Source, List<ParsedRow> Rows) Parse(string text)
    {
        var records = ReadCsv(text);
        if (records.Count == 0)
        {
            throw new ImportFileException("That file is empty.");
        }
        var header = records[0].Select(h => h.Trim()).ToArray();
        var source = header.Contains("Exclusive Shelf") && header.Contains("Book Id") ? ImportSource.Goodreads
            : header.Contains("Read Status") && header.Contains("ISBN/UID") ? ImportSource.StoryGraph
            : header.Contains("Hardcover Book Id") && header.Contains("Reads") ? ImportSource.Carrel
            : throw new ImportFileException("That doesn’t look like a Goodreads, StoryGraph, or Carrel export. Upload the CSV file they give you.");
        if (records.Count - 1 > MaxRows)
        {
            throw new ImportFileException($"That export has more than {MaxRows:N0} books, which is more than Carrel can import at once.");
        }

        var rows = new List<ParsedRow>();
        foreach (var record in records.Skip(1))
        {
            var fields = header.Select((name, i) => (name, value: i < record.Length ? record[i].Trim() : ""))
                .GroupBy(f => f.name)
                .ToDictionary(g => g.Key, g => g.First().value);
            var row = source switch
            {
                ImportSource.Goodreads => FromGoodreads(fields),
                ImportSource.StoryGraph => FromStoryGraph(fields),
                _ => FromCarrel(fields),
            };
            if (row is not null)
            {
                rows.Add(row);
            }
        }
        if (rows.Count == 0)
        {
            throw new ImportFileException("That export doesn’t have any books in it.");
        }
        return (source, rows);
    }

    private static ParsedRow? FromGoodreads(Dictionary<string, string> f)
    {
        var title = f.GetValueOrDefault("Title", "");
        if (title.Length == 0)
        {
            return null;
        }
        var shelf = f.GetValueOrDefault("Exclusive Shelf", "").ToLowerInvariant();
        var status = shelf switch
        {
            "read" => ReadingStatus.Read,
            "currently-reading" => ReadingStatus.Reading,
            _ when DidNotFinishShelves.Contains(shelf) => ReadingStatus.DidNotFinish,
            _ when PausedShelves.Contains(shelf) => ReadingStatus.Paused,
            _ => ReadingStatus.WantToRead,
        };
        var authors = new[] { f.GetValueOrDefault("Author", "") }
            .Concat(SplitNames(f.GetValueOrDefault("Additional Authors", "")))
            .Where(a => a.Length > 0)
            .ToArray();
        // A rating of 0 means unrated.
        var rating = Stars(f.GetValueOrDefault("My Rating"));
        // Goodreads only exports the latest read's finish date.
        var dated = new List<(DateOnly? Started, DateOnly? Finished)>();
        if (ParseDate(f.GetValueOrDefault("Date Read")) is { } dateRead)
        {
            dated.Add((null, dateRead));
        }

        return new ParsedRow(
            title,
            authors,
            ValidIsbn(f.GetValueOrDefault("ISBN13")),
            ValidIsbn(f.GetValueOrDefault("ISBN")),
            null,
            f.GetValueOrDefault("Book Id") is { Length: > 0 } id ? id : null,
            status,
            rating,
            ParseDate(f.GetValueOrDefault("Date Added")),
            BuildReads(status, dated, ReadCount(f.GetValueOrDefault("Read Count"))));
    }

    private static ParsedRow? FromStoryGraph(Dictionary<string, string> f)
    {
        var title = f.GetValueOrDefault("Title", "");
        if (title.Length == 0)
        {
            return null;
        }
        var status = f.GetValueOrDefault("Read Status", "").ToLowerInvariant() switch
        {
            "read" => ReadingStatus.Read,
            "currently-reading" => ReadingStatus.Reading,
            "paused" => ReadingStatus.Paused,
            "did-not-finish" => ReadingStatus.DidNotFinish,
            _ => ReadingStatus.WantToRead,
        };
        // The ISBN/UID column holds an ISBN, an Amazon ASIN, or StoryGraph's own id.
        var uid = f.GetValueOrDefault("ISBN/UID", "");
        var isbn = ValidIsbn(uid);
        var asin = isbn is null && uid.Length == 10 && uid.StartsWith("B0", StringComparison.Ordinal) && uid.All(char.IsAsciiLetterOrDigit)
            ? uid
            : null;
        var rating = Stars(f.GetValueOrDefault("Star Rating"));

        // "Dates Read" lists each read as a date range or a single (finish) date, e.g. "2026/08/24-2026/08/26".
        var dated = new List<(DateOnly? Started, DateOnly? Finished)>();
        foreach (var part in f.GetValueOrDefault("Dates Read", "").Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
        {
            var range = part.Split('-', 2, StringSplitOptions.TrimEntries);
            var read = range.Length == 2 ? (ParseDate(range[0]), ParseDate(range[1])) : ((DateOnly?)null, ParseDate(range[0]));
            if (read.Item1 is not null || read.Item2 is not null)
            {
                dated.Add(read);
            }
        }
        if (dated.Count == 0 && ParseDate(f.GetValueOrDefault("Last Date Read")) is { } last)
        {
            dated.Add((null, last));
        }

        return new ParsedRow(
            title,
            SplitNames(f.GetValueOrDefault("Authors", "")),
            isbn?.Length == 13 ? isbn : null,
            isbn?.Length == 10 ? isbn : null,
            asin,
            null,
            status,
            rating,
            ParseDate(f.GetValueOrDefault("Date Added")),
            BuildReads(status, dated, ReadCount(f.GetValueOrDefault("Read Count"))));
    }

    /// <summary>Carrel's own export: everything as the reader had it, including reads as they were and progress.</summary>
    private static ParsedRow? FromCarrel(Dictionary<string, string> f)
    {
        var title = f.GetValueOrDefault("Title", "");
        if (title.Length == 0)
        {
            return null;
        }
        var statusText = f.GetValueOrDefault("Status", "").ToLowerInvariant();
        var status = CarrelCsv.Statuses.Where(s => s.Value == statusText).Select(s => s.Key).DefaultIfEmpty(ReadingStatus.WantToRead).First();
        var rating = Number(f.GetValueOrDefault("Rating")) is { } stars && stars is >= 0.5m and <= 5 && stars * 2 == decimal.Truncate(stars * 2)
            ? stars
            : (decimal?)null;

        var unitText = f.GetValueOrDefault("Progress Unit", "").ToLowerInvariant();
        var unit = CarrelCsv.ProgressUnits.Where(u => u.Value == unitText).Select(u => (ProgressUnit?)u.Key).FirstOrDefault();
        var value = Number(f.GetValueOrDefault("Progress Value"));
        var percent = Number(f.GetValueOrDefault("Progress Percent")) is { } p && p is >= 0 and <= 100 ? p : (decimal?)null;
        // Out of range, or half a pair: left out, as the book page would refuse it.
        if (unit is null || value is null || value < 0 || value > 1_000_000 || (unit == ProgressUnit.Percent && value > 100))
        {
            unit = null;
            value = null;
        }
        var added = DateTimeOffset.TryParseExact(f.GetValueOrDefault("Date Added"), CarrelCsv.AddedFormat, CultureInfo.InvariantCulture,
            DateTimeStyles.AssumeUniversal, out var addedAt) ? addedAt : (DateTimeOffset?)null;

        return new ParsedRow(
            title,
            f.GetValueOrDefault("Authors", "").Split(CarrelCsv.AuthorSeparator, StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries),
            ValidIsbn(f.GetValueOrDefault("ISBN13")),
            ValidIsbn(f.GetValueOrDefault("ISBN10")),
            null,
            null,
            status,
            rating,
            added is { } a ? DateOnly.FromDateTime(a.UtcDateTime) : null,
            CarrelCsv.ParseReads(f.GetValueOrDefault("Reads", "")),
            int.TryParse(f.GetValueOrDefault("Hardcover Book Id"), out var bookId) && bookId > 0 ? bookId : null,
            long.TryParse(f.GetValueOrDefault("Hardcover Edition Id"), out var editionId) && editionId > 0 ? editionId : null,
            added,
            unit,
            value,
            percent);
    }

    private static decimal? Number(string? text) =>
        decimal.TryParse(text, NumberStyles.Number, CultureInfo.InvariantCulture, out var number) ? number : null;

    /// <summary>
    /// A Goodreads or StoryGraph rating in Carrel's half stars. Whole stars stay whole, and anything between becomes
    /// the half star: 3.25, 3.5, and 3.75 are all 3.5.
    /// </summary>
    private static decimal? Stars(string? text) =>
        Number(text) is { } stars && stars is > 0 and <= 5
            ? stars == decimal.Truncate(stars) ? stars : decimal.Truncate(stars) + 0.5m
            : null;

    /// <summary>
    /// The reads to record. Dated reads come from the export. Exports count more reads than they date, so the rest are
    /// added as finished on an unknown date. A book being read keeps its current read open, and the export's count
    /// includes that read. Books not finished get no finished reads; a did-not-finish book's count means nothing.
    /// </summary>
    private static List<ImportedRead> BuildReads(ReadingStatus status, List<(DateOnly? Started, DateOnly? Finished)> dated, int count)
    {
        var reads = new List<ImportedRead>();
        if (status == ReadingStatus.DidNotFinish)
        {
            return reads;
        }
        var finished = dated.Where(d => d.Finished is not null).ToList();
        foreach (var (started, finishedOn) in finished)
        {
            // A start after the finish is a data entry slip; keep the finish, which is what the stats use.
            reads.Add(new ImportedRead(started <= finishedOn ? started : null, finishedOn, false));
        }

        var inProgress = status is ReadingStatus.Reading or ReadingStatus.Paused;
        var finishedReads = status switch
        {
            ReadingStatus.Read => Math.Max(count, 1),
            _ when inProgress => Math.Max(count - 1, 0),
            _ => count,
        };
        for (var i = finished.Count; i < finishedReads; i++)
        {
            reads.Add(new ImportedRead(null, null, true));
        }
        if (inProgress)
        {
            var current = dated.LastOrDefault(d => d.Finished is null);
            reads.Add(new ImportedRead(current.Started, null, false));
        }
        return reads;
    }

    private static int ReadCount(string? text) => int.TryParse(text, out var count) && count is > 0 and < 1000 ? count : 0;

    private static string[] SplitNames(string text) =>
        text.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);

    private static DateOnly? ParseDate(string? text) =>
        DateOnly.TryParseExact(text?.Trim(), DateFormats, CultureInfo.InvariantCulture, DateTimeStyles.None, out var date) ? date : null;

    /// <summary>The ISBN if its check digit is right. Goodreads wraps ISBNs as ="…"; junk ISBNs match unrelated books.</summary>
    public static string? ValidIsbn(string? text)
    {
        var isbn = BookService.NormalizeIsbn(text);
        return isbn switch
        {
            { Length: 13 } when isbn.All(char.IsAsciiDigit)
                && isbn.Select((c, i) => (c - '0') * (i % 2 == 0 ? 1 : 3)).Sum() % 10 == 0 => isbn,
            { Length: 10 } when isbn[..9].All(char.IsAsciiDigit)
                && isbn.Select((c, i) => (c == 'X' ? 10 : c - '0') * (10 - i)).Sum() % 11 == 0 => isbn,
            _ => null,
        };
    }

    /// <summary>RFC 4180 CSV: quoted fields can hold commas, doubled quotes, and line breaks (Goodreads reviews do).</summary>
    private static List<string[]> ReadCsv(string text)
    {
        var records = new List<string[]>();
        var fields = new List<string>();
        var field = new StringBuilder();
        var quoted = false;
        for (var i = 0; i < text.Length; i++)
        {
            var c = text[i];
            if (quoted)
            {
                if (c == '"' && i + 1 < text.Length && text[i + 1] == '"')
                {
                    field.Append('"');
                    i++;
                }
                else if (c == '"')
                {
                    quoted = false;
                }
                else
                {
                    field.Append(c);
                }
                continue;
            }
            switch (c)
            {
                case '"':
                    quoted = true;
                    break;
                case ',':
                    fields.Add(field.ToString());
                    field.Clear();
                    break;
                case '\r':
                    break;
                case '\n':
                    fields.Add(field.ToString());
                    field.Clear();
                    if (fields.Any(f => f.Length > 0))
                    {
                        records.Add([.. fields]);
                    }
                    fields.Clear();
                    break;
                default:
                    field.Append(c);
                    break;
            }
        }
        fields.Add(field.ToString());
        if (fields.Any(f => f.Length > 0))
        {
            records.Add([.. fields]);
        }
        return records;
    }
}
