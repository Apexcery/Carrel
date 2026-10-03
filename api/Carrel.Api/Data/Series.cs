namespace Carrel.Api.Data;

public class Series
{
    public long Id { get; set; }
    public required string Name { get; set; }

    public long? HardcoverId { get; set; }

    public List<BookSeries> Books { get; } = [];
}

public class BookSeries
{
    public long BookId { get; set; }
    public Book Book { get; set; } = null!;
    public long SeriesId { get; set; }
    public Series Series { get; set; } = null!;

    /// <summary>Position in the series; fractional for novellas (e.g. 1.5), null if unnumbered.</summary>
    public decimal? Position { get; set; }
}
