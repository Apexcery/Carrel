using System.Net;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading.RateLimiting;

namespace Carrel.Api.Books;

/// <summary>Thrown when a book source can't be used right now (rate limit, outage, bad token).</summary>
public class BookSourceUnavailableException(string message) : Exception(message);

/// <summary>Client for Hardcover's GraphQL API. Uses the server-side token; never exposed to browsers.</summary>
public partial class HardcoverClient(HttpClient http, ILogger<HardcoverClient> logger)
{
    // Hardcover's free plan allows a burst of 10, refilling at 60 a minute. One bucket for the whole process,
    // which is correct while Cloud Run runs at most one instance.
    private static readonly TokenBucketRateLimiter Throttle = new(new TokenBucketRateLimiterOptions
    {
        TokenLimit = 10,
        TokensPerPeriod = 1,
        ReplenishmentPeriod = TimeSpan.FromSeconds(1),
        QueueLimit = 20,
        QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
    });

    private const int DailyQuotaWarningThreshold = 500;

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
    };

    private const string SearchQuery = """
        query Search($query: String!, $perPage: Int!, $page: Int!) {
          search(query: $query, query_type: "Book", per_page: $perPage, page: $page) { results }
        }
        """;

    private const string BookQuery = """
        query Book($id: Int!) {
          books_by_pk(id: $id) {
            id canonical_id title subtitle description release_year rating ratings_count cached_image cached_tags
            book_series { position series { id name } }
            contributions { contribution author { id name } }
            editions(limit: 20, order_by: {users_count: desc}) {
              id isbn_13 isbn_10 reading_format_id pages audio_seconds release_date cached_image
              publisher { name }
              language { code2 }
            }
          }
        }
        """;

    private const string IsbnQuery = """
        query BookByIsbn($isbns: [String!]!) {
          editions(where: {_or: [{isbn_13: {_in: $isbns}}, {isbn_10: {_in: $isbns}}]}, limit: 1) { book_id }
        }
        """;

    /// <summary>Hardcover's book id for any edition with one of these ISBNs; null if none.</summary>
    public async Task<int?> FindBookIdByIsbnAsync(IReadOnlyCollection<string> isbns, CancellationToken ct)
    {
        var data = await QueryAsync<HardcoverEditionsData>(IsbnQuery, new { isbns }, ct);
        return data.Editions.FirstOrDefault()?.BookId;
    }

    public async Task<HardcoverSearchResults> SearchBooksAsync(string query, int page, int perPage, CancellationToken ct)
    {
        var data = await QueryAsync<HardcoverSearchData>(SearchQuery, new { query, perPage, page }, ct);
        return data.Search.Results;
    }

    /// <summary>Returns the book, following Hardcover's duplicate merges to the canonical record; null if not found.</summary>
    public async Task<HardcoverBook?> GetBookAsync(int id, CancellationToken ct)
    {
        var book = (await QueryAsync<HardcoverBookData>(BookQuery, new { id }, ct)).BooksByPk;
        if (book?.CanonicalId is { } canonicalId && canonicalId != id)
        {
            book = (await QueryAsync<HardcoverBookData>(BookQuery, new { id = canonicalId }, ct)).BooksByPk;
        }
        return book;
    }

    private async Task<T> QueryAsync<T>(string query, object variables, CancellationToken ct) where T : class
    {
        using var lease = await Throttle.AcquireAsync(1, ct);
        if (!lease.IsAcquired)
        {
            throw new BookSourceUnavailableException("Too many Hardcover requests are queued.");
        }

        HttpResponseMessage response;
        try
        {
            response = await http.PostAsJsonAsync("", new { query, variables }, ct);
        }
        catch (Exception e) when (e is HttpRequestException || (e is TaskCanceledException && !ct.IsCancellationRequested))
        {
            logger.LogWarning(e, "Hardcover request failed.");
            throw new BookSourceUnavailableException("Hardcover request failed.");
        }
        using var _ = response;
        LogDailyQuota(response);

        switch (response.StatusCode)
        {
            case HttpStatusCode.Unauthorized:
                logger.LogError("Hardcover rejected the API token; it may have expired or been reset. Create a new one.");
                throw new BookSourceUnavailableException("Hardcover authentication failed.");
            case HttpStatusCode.TooManyRequests:
                logger.LogWarning("Hardcover rate limit reached.");
                throw new BookSourceUnavailableException("Hardcover rate limit reached.");
            case var status when !response.IsSuccessStatusCode:
                logger.LogWarning("Hardcover returned {Status}: {Body}", (int)status, await response.Content.ReadAsStringAsync(ct));
                throw new BookSourceUnavailableException($"Hardcover returned {(int)status}.");
        }

        var body = await response.Content.ReadFromJsonAsync<GraphQlResponse<T>>(JsonOptions, ct);
        if (body?.Errors is { Length: > 0 } errors || body?.Data is null)
        {
            logger.LogWarning("Hardcover query failed: {Errors}", string.Join("; ", body?.Errors?.Select(e => e.Message) ?? []));
            throw new BookSourceUnavailableException("Hardcover query failed.");
        }
        return body.Data;
    }

    // RateLimit header, e.g. "Free";r=8;t=42, "daily";r=4231;t=51234
    private void LogDailyQuota(HttpResponseMessage response)
    {
        if (!response.Headers.TryGetValues("RateLimit", out var values))
        {
            return;
        }
        var match = DailyRemaining().Match(string.Join(",", values));
        if (match.Success && int.Parse(match.Groups[1].Value) < DailyQuotaWarningThreshold)
        {
            logger.LogWarning("Hardcover daily quota low: {Remaining} requests left.", match.Groups[1].Value);
        }
    }

    [GeneratedRegex("\"daily\";r=(\\d+)")]
    private static partial Regex DailyRemaining();
}
