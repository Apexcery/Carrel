namespace Carrel.Api.Data;

/// <summary>
/// Where a reader is in a book they're reading in the Android app, so another device carries on from there. One per
/// library entry, deleted with it (removing the book, deleting the library, or deleting the account).
/// </summary>
public class ReadingPosition
{
    /// <summary>Readium Locators are small; this leaves room for a long chapter title.</summary>
    public const int MaxLocatorLength = 4000;

    public long LibraryEntryId { get; set; }
    public LibraryEntry LibraryEntry { get; set; } = null!;

    /// <summary>The place as a Readium Locator (JSON), exact within the same copy of the book.</summary>
    public string Locator { get; set; } = "";

    /// <summary>How far through the book, from 0 to 1, for a different copy (another edition, or a newer version).</summary>
    public decimal Progression { get; set; }

    /// <summary>When the reader was there, by the device's clock (never later than the server's).</summary>
    public DateTimeOffset UpdatedAt { get; set; }
}
