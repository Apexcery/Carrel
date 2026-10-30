namespace Carrel.Api.Data;

public class Author
{
    public long Id { get; set; }
    public required string Name { get; set; }

    public long? HardcoverId { get; set; }
    public string? OpenLibraryAuthorKey { get; set; }

    /// <summary>
    /// Copyright takedowns, set by hand after a valid notice (see docs/operations.md). They hide the photo or the bio
    /// on the author's page and in search results. Author pages come live from Hardcover, so an author taken down may
    /// have a row only for these flags.
    /// </summary>
    public bool PhotoSuppressed { get; set; }
    public bool BioSuppressed { get; set; }

    public List<BookAuthor> Books { get; } = [];
}

public class BookAuthor
{
    public long BookId { get; set; }
    public Book Book { get; set; } = null!;
    public long AuthorId { get; set; }
    public Author Author { get; set; } = null!;

    /// <summary>Contribution type as given by the source, e.g. "author", "translator", "narrator".</summary>
    public string Role { get; set; } = "author";

    /// <summary>Display order among the book's contributors.</summary>
    public int Position { get; set; }
}
