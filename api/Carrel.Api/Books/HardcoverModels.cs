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

/// <summary>A tag in a book's cached_tags; Count is how many readers gave the book this tag.</summary>
public record HardcoverTag(string Tag, int Count);

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

public record HardcoverBookTagsData(HardcoverBookTags? BooksByPk);

public record HardcoverBookTags(
    Dictionary<string, HardcoverTag[]>? CachedTags,
    HardcoverContribution[] Contributions,
    HardcoverBookSeries[] BookSeries,
    HardcoverTagging[] Taggings);

public record HardcoverTagging(HardcoverTagDetail Tag);

/// <summary>A tag itself; Count is how many books it's on across Hardcover.</summary>
public record HardcoverTagDetail(int Id, string Tag, int Count, HardcoverTagCategory TagCategory);

public record HardcoverTagCategory(string Slug);

/// <summary>Genre tags; each Count is how many books the tag is on across Hardcover.</summary>
public record HardcoverGenreTagsData(HardcoverTag[] Tags);

public record HardcoverTrendingData(HardcoverTrending BooksTrending);

public record HardcoverTrending(int[] Ids);

public record HardcoverListedBook(
    int Id,
    string Title,
    int? ReleaseYear,
    int UsersCount,
    HardcoverImage? CachedImage,
    HardcoverContribution[] Contributions,
    HardcoverBookSeries[] BookSeries,
    HardcoverTag[]? Genres); // The book's genre tags, most votes first.
