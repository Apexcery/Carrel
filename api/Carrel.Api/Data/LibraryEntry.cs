namespace Carrel.Api.Data;

/// <summary>A book in a user's library. One per user per book.</summary>
public class LibraryEntry
{
    public long Id { get; set; }

    /// <summary>Supabase Auth user id (auth.users.id), taken from the token's sub claim.</summary>
    public Guid UserId { get; set; }

    public long BookId { get; set; }
    public Book Book { get; set; } = null!;
    public long? EditionId { get; set; }
    public Edition? Edition { get; set; }

    public ReadingStatus Status { get; set; }

    /// <summary>Half-star rating from 0.5 to 5.</summary>
    public decimal? Rating { get; set; }

    /// <summary>Progress as the user entered it, e.g. page 120 or 45 percent.</summary>
    public ProgressUnit? ProgressUnit { get; set; }
    public decimal? ProgressValue { get; set; }

    /// <summary>Progress normalised to 0-100, for sorting and progress bars.</summary>
    public decimal? ProgressPercent { get; set; }

    /// <summary>When the book was added; kept from the source when importing.</summary>
    public DateTimeOffset AddedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }

    public List<Read> Reads { get; } = [];
}

public enum ReadingStatus
{
    WantToRead,
    Reading,
    Read,
    DidNotFinish,
}

public enum ProgressUnit
{
    Page,
    Percent,
    Seconds,
}
