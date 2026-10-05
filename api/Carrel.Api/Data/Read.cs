namespace Carrel.Api.Data;

/// <summary>One read-through of a library entry's book. Several rows mean rereads.</summary>
public class Read
{
    public long Id { get; set; }
    public long LibraryEntryId { get; set; }
    public LibraryEntry LibraryEntry { get; set; } = null!;

    public DateOnly? StartedOn { get; set; }
    public DateOnly? FinishedOn { get; set; }

    /// <summary>
    /// Finished, but when isn't known (common in imports). Such a read isn't open, so it isn't picked up as the
    /// current read, and it counts as a read without belonging to any year.
    /// </summary>
    public bool FinishedDateUnknown { get; set; }

    /// <summary>Still being read: not finished on any date, known or not.</summary>
    public bool IsOpen => FinishedOn is null && !FinishedDateUnknown;
}
