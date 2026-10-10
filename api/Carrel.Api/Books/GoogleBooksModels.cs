namespace Carrel.Api.Books;

// Shapes of Google Books API responses, deserialised with camelCase naming.
// Only the fields Carrel asks for; see https://developers.google.com/books/docs/v1/reference/volumes

public record GoogleBooksSearch(GoogleBooksVolume[]? Items);

public record GoogleBooksVolume(string Id, GoogleBooksVolumeInfo? VolumeInfo, GoogleBooksAccessInfo? AccessInfo, GoogleBooksSaleInfo? SaleInfo)
{
    /// <summary>
    /// Whether the publisher supplied the volume (Google sells or previews it, or its id ends "QBAJ", as uploaded
    /// volumes' ids do), rather than it being a catalogue record, whose descriptions are sometimes another book's.
    /// </summary>
    public bool FromPublisher => Id.EndsWith("QBAJ", StringComparison.Ordinal)
        || AccessInfo?.Viewability is not (null or "NO_PAGES")
        || SaleInfo?.Saleability == "FOR_SALE";
}

/// <summary>Description is HTML when the volume is fetched on its own, and plain text in search results.</summary>
public record GoogleBooksVolumeInfo(string? Language, GoogleBooksIdentifier[]? IndustryIdentifiers, string? Description);

public record GoogleBooksIdentifier(string Type, string Identifier);

public record GoogleBooksAccessInfo(string? Viewability);

public record GoogleBooksSaleInfo(string? Saleability);
