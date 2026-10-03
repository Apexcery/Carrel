using System.Text.Json;
using System.Text.Json.Serialization;

namespace Carrel.Api.Books;

// Shapes of Open Library JSON responses, deserialised with snake_case naming.
// Keys look like "/works/OL45804W", "/books/OL7353617M" and "/authors/OL23919A".

public record OpenLibrarySearchResponse([property: JsonPropertyName("numFound")] int NumFound, OpenLibrarySearchDoc[] Docs);

public record OpenLibrarySearchDoc(string Key, string Title, string? Subtitle, string[]? AuthorName, int? FirstPublishYear, long? CoverI);

public record OpenLibraryWork(
    string? Title,
    string? Subtitle,
    // Either a plain string or {"type": "/type/text", "value": "..."}.
    JsonElement? Description,
    long[]? Covers,
    OpenLibraryWorkAuthor[]? Authors,
    string? FirstPublishDate);

public record OpenLibraryWorkAuthor(OpenLibraryKey? Author);

public record OpenLibraryKey(string Key);

public record OpenLibraryEditions(OpenLibraryEdition[] Entries);

public record OpenLibraryEdition(
    string Key,
    // The snake_case policy would map these to isbn13/isbn10.
    [property: JsonPropertyName("isbn_13")] string[]? Isbn13,
    [property: JsonPropertyName("isbn_10")] string[]? Isbn10,
    int? NumberOfPages,
    string[]? Publishers,
    string? PublishDate,
    string? PhysicalFormat,
    long[]? Covers,
    OpenLibraryKey[]? Works);

public record OpenLibraryAuthor(string Name);
