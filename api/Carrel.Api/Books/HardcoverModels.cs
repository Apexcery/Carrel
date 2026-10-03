using System.Text.Json.Serialization;

namespace Carrel.Api.Books;

// Shapes of Hardcover GraphQL responses, deserialised with snake_case naming.
// Only the fields Carrel uses; see https://docs.hardcover.app/api/graphql/schemas/books/

public record GraphQlResponse<T>(T? Data, GraphQlError[]? Errors);

public record GraphQlError(string Message);

public record HardcoverSearchData(HardcoverSearch Search);

public record HardcoverSearch(HardcoverSearchResults Results);

public record HardcoverSearchResults(int Found, HardcoverSearchHit[] Hits);

public record HardcoverSearchHit(HardcoverSearchDocument Document);

public record HardcoverSearchDocument(
    string Id,
    string Title,
    string? Subtitle,
    string[]? AuthorNames,
    int? ReleaseYear,
    HardcoverImage? Image,
    decimal? Rating,
    int? RatingsCount,
    HardcoverBookSeries? FeaturedSeries);

public record HardcoverSeriesData(HardcoverSeriesDetail? SeriesByPk);

public record HardcoverSeriesDetail(
    int Id,
    string Name,
    bool? IsCompleted,
    HardcoverAuthor? Author,
    HardcoverSeriesEntry[] BookSeries);

public record HardcoverSeriesEntry(decimal? Position, HardcoverSeriesBook Book);

public record HardcoverSeriesBook(
    int Id,
    string Title,
    int? ReleaseYear,
    int UsersCount,
    decimal? Rating,
    int RatingsCount,
    HardcoverImage? CachedImage,
    HardcoverContribution[] Contributions);

public record HardcoverEditionsData(HardcoverEditionRef[] Editions);

public record HardcoverEditionRef(int BookId);

public record HardcoverBookData(HardcoverBook? BooksByPk);

public record HardcoverBook(
    int Id,
    int? CanonicalId,
    string? Title,
    string? Subtitle,
    string? Description,
    int? ReleaseYear,
    decimal? Rating,
    int RatingsCount,
    HardcoverImage? CachedImage,
    Dictionary<string, HardcoverTag[]>? CachedTags,
    HardcoverBookSeries[] BookSeries,
    HardcoverContribution[] Contributions,
    HardcoverEdition[] Editions);

public record HardcoverImage(string? Url);

public record HardcoverTag(string Tag);

// Series can be missing: search documents sometimes carry an empty featured_series object.
public record HardcoverBookSeries(decimal? Position, bool Featured, HardcoverSeries? Series);

public record HardcoverSeries(int Id, string Name);

public record HardcoverContribution(string? Contribution, HardcoverAuthor Author);

public record HardcoverAuthor(int Id, string Name);

public record HardcoverEdition(
    int Id,
    // The snake_case policy would map these to isbn13/isbn10.
    [property: JsonPropertyName("isbn_13")] string? Isbn13,
    [property: JsonPropertyName("isbn_10")] string? Isbn10,
    int? ReadingFormatId,
    int? Pages,
    int? AudioSeconds,
    string? ReleaseDate,
    HardcoverPublisher? Publisher,
    HardcoverLanguage? Language,
    HardcoverImage? CachedImage);

public record HardcoverPublisher(string Name);

public record HardcoverLanguage(string? Code2);
