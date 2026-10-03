namespace Carrel.Api.Data;

public class Author
{
    public long Id { get; set; }
    public required string Name { get; set; }

    public long? HardcoverId { get; set; }
    public string? OpenLibraryAuthorKey { get; set; }

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
