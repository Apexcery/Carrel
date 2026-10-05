namespace Carrel.Api.Data;

/// <summary>
/// A reader's upload of a Goodreads or StoryGraph export. Its rows are matched to books and written to the library
/// in the background, in batches, with all progress stored here so a restart or deploy carries on where it stopped.
/// </summary>
public class LibraryImport
{
    public long Id { get; set; }

    /// <summary>Supabase Auth user id; deleting the user deletes their imports.</summary>
    public Guid UserId { get; set; }

    public ImportSource Source { get; set; }

    /// <summary>Whether rows for books already in the library replace those entries, or leave them alone.</summary>
    public bool OverwriteExisting { get; set; }

    public ImportState State { get; set; }

    public DateTimeOffset CreatedAt { get; set; }

    /// <summary>When a batch last made progress; a stalled import is restarted from the status page.</summary>
    public DateTimeOffset UpdatedAt { get; set; }

    public DateTimeOffset? FinishedAt { get; set; }

    /// <summary>A batch is running until then; another run that starts meanwhile leaves the import to it.</summary>
    public DateTimeOffset? LeaseUntil { get; set; }

    /// <summary>Numbers the queued batches, so queueing the same batch twice is caught (Cloud Tasks task names).</summary>
    public int BatchNumber { get; set; }

    /// <summary>Batches in a row that failed; the import gives up after a few.</summary>
    public int Failures { get; set; }

    public List<ImportItem> Items { get; } = [];
}

/// <summary>One row of an export: what the reader recorded about a book, and the book it was matched to.</summary>
public class ImportItem
{
    public long Id { get; set; }
    public long ImportId { get; set; }
    public LibraryImport Import { get; set; } = null!;

    /// <summary>The row's position in the file, from 1, so results list in the file's order.</summary>
    public int Row { get; set; }

    public required string Title { get; set; }

    /// <summary>Authors as the export lists them (StoryGraph sometimes puts an illustrator first).</summary>
    public string[] Authors { get; set; } = [];

    public string? Isbn13 { get; set; }
    public string? Isbn10 { get; set; }
    public string? Asin { get; set; }
    public string? GoodreadsId { get; set; }

    public ReadingStatus Status { get; set; }

    /// <summary>Rounded to half stars.</summary>
    public decimal? Rating { get; set; }

    public DateOnly? AddedOn { get; set; }

    /// <summary>Reads to record, oldest first (JSON).</summary>
    public List<ImportedRead> Reads { get; set; } = [];

    public ImportMatch Match { get; set; }

    /// <summary>The matched Hardcover book, before it's stored as <see cref="BookId"/>.</summary>
    public int? HardcoverBookId { get; set; }

    public long? BookId { get; set; }
    public Book? Book { get; set; }

    /// <summary>Written to the library (or left alone, for a book already there with skip chosen).</summary>
    public bool Written { get; set; }

    /// <summary>The library entry was created by this import, so a wrong match can remove it again.</summary>
    public bool CreatedEntry { get; set; }

    /// <summary>The reader checked a title match and kept it.</summary>
    public bool Confirmed { get; set; }
}

/// <summary>A read from an export, with the same meaning as <see cref="Read"/>.</summary>
public record ImportedRead(DateOnly? StartedOn, DateOnly? FinishedOn, bool FinishedDateUnknown);

public enum ImportSource
{
    Goodreads,
    StoryGraph,
}

public enum ImportState
{
    /// <summary>Rows are being matched to books.</summary>
    Matching,
    /// <summary>Waiting for Hardcover's daily allowance to reset.</summary>
    Waiting,
    Done,
    Failed,
}

public enum ImportMatch
{
    /// <summary>Not looked up yet: Goodreads id, ISBN, and ASIN lookups come first.</summary>
    Pending,
    /// <summary>The id lookups found nothing; a title and author search is next.</summary>
    NeedsSearch,
    /// <summary>Found by Goodreads id, ISBN, or ASIN.</summary>
    Exact,
    /// <summary>Found by title and author search, so the reader is asked to check it.</summary>
    ByTitle,
    /// <summary>Chosen by the reader on the results page.</summary>
    Chosen,
    NotFound,
    /// <summary>The reader dismissed an unmatched row.</summary>
    Dismissed,
}
