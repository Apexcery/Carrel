using System.Text.Json.Serialization;
using System.Threading.RateLimiting;

namespace Carrel.Api.Books;

// Batched lookups for library imports. They run in the background and only use spare capacity, so an import never
// slows down or crowds out readers using the site.
public partial class HardcoverClient
{
    /// <summary>Most ids one lookup takes; each lookup is a single request whatever its size.</summary>
    public const int MaxLookupBatch = 100;

    /// <summary>Most books one GetBooksAsync request returns; 50 full books are about 750 KB.</summary>
    public const int MaxBookBatch = 50;

    // Background requests wait until the bucket holds this many tokens beyond their own, leaving bursts for readers.
    private const int ReaderReserve = 4;

    // Background requests stop while fewer than this many of the day's requests are left, keeping them for readers.
    private const int DailyReserve = 1000;

    private static readonly TimeSpan SpareCapacityPoll = TimeSpan.FromMilliseconds(500);

    // The daily allowance left, from the latest response; unknown (assumed plenty) until the first one.
    private static int dailyRemaining = int.MaxValue;

    private const string BookFields = """
        id canonical_id title subtitle description release_year rating ratings_count cached_image cached_tags
        book_series { position featured series { id name } }
        contributions { contribution author { id name } }
        editions(limit: 20, order_by: {users_count: desc}) {
          id isbn_13 isbn_10 reading_format_id pages audio_seconds release_date cached_image
          publisher { name }
          language { code2 }
        }
        """;

    private static readonly string BooksQuery = $$$"""
        query Books($ids: [Int!]!) { books(where: {id: {_in: $ids}}) { {{{BookFields}}} } }
        """;

    private const string IsbnsQuery = """
        query BooksByIsbn($isbns: [String!]!) {
          editions(where: {_or: [{isbn_13: {_in: $isbns}}, {isbn_10: {_in: $isbns}}]}) { isbn_13 isbn_10 book_id }
        }
        """;

    private const string AsinsQuery = """
        query BooksByAsin($asins: [String!]!) { editions(where: {asin: {_in: $asins}}) { asin book_id } }
        """;

    // Platform 1 is Goodreads in Hardcover's book_mappings.
    private const string GoodreadsQuery = """
        query BooksByGoodreadsId($ids: [String!]!) {
          book_mappings(where: {platform_id: {_eq: 1}, external_id: {_in: $ids}}) { external_id book_id }
        }
        """;

    /// <summary>True while the day's allowance is too low for background work; it resets daily.</summary>
    public static bool DailyAllowanceReserved => Volatile.Read(ref dailyRemaining) < DailyReserve;

    /// <summary>Hardcover book ids by ISBN (10 or 13), for the ISBNs it knows.</summary>
    public async Task<Dictionary<string, int>> FindBookIdsByIsbnsAsync(IReadOnlyCollection<string> isbns, CancellationToken ct)
    {
        var data = await QueryAsync<IsbnEditions>(IsbnsQuery, new { isbns }, ct, background: true);
        var found = new Dictionary<string, int>();
        foreach (var edition in data.Editions)
        {
            foreach (var isbn in new[] { edition.Isbn13, edition.Isbn10 }.OfType<string>().Where(isbns.Contains))
            {
                found.TryAdd(isbn, edition.BookId);
            }
        }
        return found;
    }

    /// <summary>Hardcover book ids by Amazon ASIN, for the ASINs it knows.</summary>
    public async Task<Dictionary<string, int>> FindBookIdsByAsinsAsync(IReadOnlyCollection<string> asins, CancellationToken ct)
    {
        var data = await QueryAsync<AsinEditions>(AsinsQuery, new { asins }, ct, background: true);
        return data.Editions.Where(e => e.Asin is not null).DistinctBy(e => e.Asin).ToDictionary(e => e.Asin!, e => e.BookId);
    }

    /// <summary>Hardcover book ids by Goodreads book id, for the ids it has mapped.</summary>
    public async Task<Dictionary<string, int>> FindBookIdsByGoodreadsIdsAsync(IReadOnlyCollection<string> ids, CancellationToken ct)
    {
        var data = await QueryAsync<GoodreadsMappings>(GoodreadsQuery, new { ids }, ct, background: true);
        return data.BookMappings.DistinctBy(m => m.ExternalId).ToDictionary(m => m.ExternalId, m => m.BookId);
    }

    /// <summary>
    /// The books with these ids, following Hardcover's duplicate merges, keyed by the id asked for. A merged book maps
    /// to its canonical record; ids Hardcover doesn't have are left out.
    /// </summary>
    public async Task<Dictionary<int, HardcoverBook>> GetBooksAsync(IReadOnlyCollection<int> ids, CancellationToken ct)
    {
        var books = (await QueryAsync<BooksData>(BooksQuery, new { ids }, ct, background: true)).Books.ToDictionary(b => b.Id);
        var canonicalIds = books.Values
            .Select(b => b.CanonicalId)
            .OfType<int>()
            .Where(id => !books.ContainsKey(id))
            .Distinct()
            .ToList();
        if (canonicalIds.Count > 0)
        {
            foreach (var book in (await QueryAsync<BooksData>(BooksQuery, new { ids = canonicalIds }, ct, background: true)).Books)
            {
                books[book.Id] = book;
            }
        }

        var found = new Dictionary<int, HardcoverBook>();
        foreach (var id in ids)
        {
            if (books.TryGetValue(id, out var book) && book.CanonicalId is { } canonicalId && canonicalId != id)
            {
                books.TryGetValue(canonicalId, out book);
            }
            if (book is not null)
            {
                found[id] = book;
            }
        }
        return found;
    }

    /// <summary>A search for one import row; background priority, otherwise the same as the site's search.</summary>
    public async Task<HardcoverSearchResults> SearchBooksInBackgroundAsync(string query, int perPage, CancellationToken ct)
    {
        var data = await QueryAsync<HardcoverSearchData>(SearchQuery, new { query, perPage, page = 1 }, ct, background: true);
        return data.Search.Results;
    }

    /// <summary>
    /// Waits until the throttle has the tokens plus a reserve for readers, then takes them, so background work only uses
    /// capacity nobody else wants. Never queues, so it can't fill the queue readers' requests wait in.
    /// </summary>
    private static async Task<RateLimitLease> AcquireSpareAsync(int queries, CancellationToken ct)
    {
        while (true)
        {
            if (DailyAllowanceReserved)
            {
                throw new BookSourceUnavailableException("Hardcover's daily allowance is kept for readers.");
            }
            if (Throttle.GetStatistics()?.CurrentAvailablePermits >= queries + ReaderReserve)
            {
                var lease = Throttle.AttemptAcquire(queries);
                if (lease.IsAcquired)
                {
                    return lease;
                }
                lease.Dispose();
            }
            await Task.Delay(SpareCapacityPoll, ct);
        }
    }

    private record BooksData(HardcoverBook[] Books);

    private record IsbnEditions(IsbnEdition[] Editions);

    private record IsbnEdition(
        [property: JsonPropertyName("isbn_13")] string? Isbn13,
        [property: JsonPropertyName("isbn_10")] string? Isbn10,
        int BookId);

    private record AsinEditions(AsinEdition[] Editions);

    private record AsinEdition(string? Asin, int BookId);

    private record GoodreadsMappings(GoodreadsMapping[] BookMappings);

    private record GoodreadsMapping(string ExternalId, int BookId);
}
