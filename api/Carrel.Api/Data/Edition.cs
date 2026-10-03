namespace Carrel.Api.Data;

public class Edition
{
    public long Id { get; set; }
    public long BookId { get; set; }
    public Book Book { get; set; } = null!;

    public string? Isbn13 { get; set; }
    public string? Isbn10 { get; set; }
    public EditionFormat? Format { get; set; }
    public int? PageCount { get; set; }
    public int? AudioSeconds { get; set; }
    public string? Publisher { get; set; }
    public DateOnly? ReleaseDate { get; set; }
    public string? Language { get; set; }
    public string? CoverUrl { get; set; }

    public long? HardcoverEditionId { get; set; }
    public string? OpenLibraryEditionKey { get; set; }
}

public enum EditionFormat
{
    Print,
    Ebook,
    Audio,
}
