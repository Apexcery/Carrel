using System.Globalization;
using System.Text;
using Carrel.Api.Data;

namespace Carrel.Api.Imports;

/// <summary>
/// Carrel's own export format, which holds everything in a library entry, so importing it back loses nothing. Shared by
/// the export and the import, so the two stay in step.
/// </summary>
public static class CarrelCsv
{
    public static readonly string[] Columns =
    [
        "Title", "Authors", "Series", "Series Position", "ISBN13", "ISBN10", "Hardcover Book Id", "Hardcover Edition Id",
        "Status", "Rating", "Progress Unit", "Progress Value", "Progress Percent", "Date Added", "Reads",
    ];

    /// <summary>Statuses as the shelf addresses spell them.</summary>
    public static readonly Dictionary<ReadingStatus, string> Statuses = new()
    {
        [ReadingStatus.WantToRead] = "want-to-read",
        [ReadingStatus.Reading] = "reading",
        [ReadingStatus.Paused] = "paused",
        [ReadingStatus.Read] = "read",
        [ReadingStatus.DidNotFinish] = "did-not-finish",
    };

    public static readonly Dictionary<ProgressUnit, string> ProgressUnits = new()
    {
        [ProgressUnit.Page] = "page",
        [ProgressUnit.Percent] = "percent",
        [ProgressUnit.Seconds] = "seconds",
    };

    /// <summary>Authors are separated by semicolons, since names can hold commas ("Martin Luther King, Jr.").</summary>
    public const char AuthorSeparator = ';';

    /// <summary>Date added, to the second, in UTC.</summary>
    public const string AddedFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    private const string DateFormat = "yyyy-MM-dd";
    private const string UnknownFinish = "?";

    /// <summary>
    /// Reads, oldest first, separated by semicolons, each as start/finish: "2024-01-02/2024-01-10". Either side can be
    /// empty ("/2023-05-02" has no start date; "2025-06-01/" is still being read), and a finish of "?" means finished on
    /// an unknown date.
    /// </summary>
    public static string FormatReads(IEnumerable<Read> reads) =>
        string.Join("; ", reads.Select(r =>
            $"{r.StartedOn?.ToString(DateFormat, CultureInfo.InvariantCulture)}/" +
            (r.FinishedDateUnknown ? UnknownFinish : r.FinishedOn?.ToString(DateFormat, CultureInfo.InvariantCulture))));

    /// <summary>Reads written by <see cref="FormatReads"/>. A start after its finish is dropped, keeping the finish.</summary>
    public static List<ImportedRead> ParseReads(string text)
    {
        var reads = new List<ImportedRead>();
        foreach (var part in text.Split(';', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
        {
            var sides = part.Split('/', 2, StringSplitOptions.TrimEntries);
            var started = ParseDate(sides[0]);
            var unknown = sides.Length == 2 && sides[1] == UnknownFinish;
            var finished = sides.Length == 2 && !unknown ? ParseDate(sides[1]) : null;
            reads.Add(new ImportedRead(started <= finished || finished is null ? started : null, finished, unknown));
        }
        return reads;
    }

    private static DateOnly? ParseDate(string text) =>
        DateOnly.TryParseExact(text, DateFormat, CultureInfo.InvariantCulture, DateTimeStyles.None, out var date) ? date : null;

    /// <summary>
    /// CSV text, quoting fields that need it. Line breaks are \n, as Goodreads writes them; no byte order mark, which
    /// some importers read as part of the first column's name.
    /// </summary>
    public static string Write(IEnumerable<IReadOnlyList<string?>> records)
    {
        var text = new StringBuilder();
        foreach (var record in records)
        {
            text.AppendJoin(',', record.Select(Field)).Append('\n');
        }
        return text.ToString();
    }

    private static string Field(string? value) =>
        value is null ? ""
        : value.IndexOfAny([',', '"', '\n', '\r']) >= 0 ? $"\"{value.Replace("\"", "\"\"")}\""
        : value;
}
