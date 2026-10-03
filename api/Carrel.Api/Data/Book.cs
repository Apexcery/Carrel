namespace Carrel.Api.Data;

/// <summary>A work, independent of any particular edition. Cached from external book sources.</summary>
public class Book
{
    public long Id { get; set; }
    public required string Title { get; set; }
    public string? Subtitle { get; set; }
    public string? Description { get; set; }
    public DescriptionSource? DescriptionSource { get; set; }
    public string? CoverUrl { get; set; }
    public int? FirstPublishedYear { get; set; }

    public long? HardcoverId { get; set; }
    public string? OpenLibraryWorkKey { get; set; }

    /// <summary>Hardcover's aggregate rating; must be credited to Hardcover when shown.</summary>
    public decimal? HardcoverRating { get; set; }
    public int? HardcoverRatingsCount { get; set; }

    /// <summary>When the metadata was last fetched from the external sources.</summary>
    public DateTimeOffset FetchedAt { get; set; }

    public List<Edition> Editions { get; } = [];
    public List<BookAuthor> Authors { get; } = [];
    public List<BookSeries> Series { get; } = [];
    public List<BookGenre> Genres { get; } = [];
}

public enum DescriptionSource
{
    Hardcover,
    OpenLibrary,
    GoogleBooks,
}
