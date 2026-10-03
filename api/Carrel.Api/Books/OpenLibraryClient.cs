using System.Net;
using System.Text.Json;
using System.Threading.RateLimiting;

namespace Carrel.Api.Books;

/// <summary>Client for Open Library's public JSON API, used when Hardcover doesn't have a book.</summary>
public class OpenLibraryClient(HttpClient http, ILogger<OpenLibraryClient> logger)
{
    // Open Library allows about 3 requests a second with an identifying User-Agent.
    private static readonly TokenBucketRateLimiter Throttle = new(new TokenBucketRateLimiterOptions
    {
        TokenLimit = 3,
        TokensPerPeriod = 3,
        ReplenishmentPeriod = TimeSpan.FromSeconds(1),
        QueueLimit = 20,
        QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
    });

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
    };

    public async Task<OpenLibrarySearchResponse> SearchAsync(string query, int page, int limit, CancellationToken ct) =>
        await GetAsync<OpenLibrarySearchResponse>(
            $"search.json?q={Uri.EscapeDataString(query)}&page={page}&limit={limit}" +
            "&fields=key,title,subtitle,author_name,first_publish_year,cover_i", ct)
        ?? new OpenLibrarySearchResponse(0, []);

    public Task<OpenLibraryWork?> GetWorkAsync(string workId, CancellationToken ct) =>
        GetAsync<OpenLibraryWork>($"works/{workId}.json", ct);

    public Task<OpenLibraryEditions?> GetEditionsAsync(string workId, int limit, CancellationToken ct) =>
        GetAsync<OpenLibraryEditions>($"works/{workId}/editions.json?limit={limit}", ct);

    public Task<OpenLibraryAuthor?> GetAuthorAsync(string authorId, CancellationToken ct) =>
        GetAsync<OpenLibraryAuthor>($"authors/{authorId}.json", ct);

    public Task<OpenLibraryEdition?> GetEditionByIsbnAsync(string isbn, CancellationToken ct) =>
        GetAsync<OpenLibraryEdition>($"isbn/{isbn}.json", ct);

    /// <summary>Returns null for 404.</summary>
    private async Task<T?> GetAsync<T>(string path, CancellationToken ct) where T : class
    {
        using var lease = await Throttle.AcquireAsync(1, ct);
        if (!lease.IsAcquired)
        {
            throw new BookSourceUnavailableException("Too many Open Library requests are queued.");
        }

        try
        {
            using var response = await http.GetAsync(path, ct);
            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                return null;
            }
            if (!response.IsSuccessStatusCode)
            {
                logger.LogWarning("Open Library returned {Status} for {Path}", (int)response.StatusCode, path);
                throw new BookSourceUnavailableException($"Open Library returned {(int)response.StatusCode}.");
            }
            return await response.Content.ReadFromJsonAsync<T>(JsonOptions, ct);
        }
        catch (Exception e) when (e is HttpRequestException or JsonException
                                      || (e is TaskCanceledException && !ct.IsCancellationRequested))
        {
            logger.LogWarning(e, "Open Library request failed for {Path}", path);
            throw new BookSourceUnavailableException("Open Library request failed.");
        }
    }
}
