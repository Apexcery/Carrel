namespace Carrel.Api.Data;

/// <summary>One read-through of a library entry's book. Several rows mean rereads.</summary>
public class Read
{
    public long Id { get; set; }
    public long LibraryEntryId { get; set; }
    public LibraryEntry LibraryEntry { get; set; } = null!;

    public DateOnly? StartedOn { get; set; }
    public DateOnly? FinishedOn { get; set; }
}
