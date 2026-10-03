namespace Carrel.Api.Data;

public class Genre
{
    public long Id { get; set; }
    public required string Name { get; set; }

    public List<BookGenre> Books { get; } = [];
}

public class BookGenre
{
    public long BookId { get; set; }
    public Book Book { get; set; } = null!;
    public long GenreId { get; set; }
    public Genre Genre { get; set; } = null!;
}
