using System.Net;
using System.Text.Json;
using System.Threading.RateLimiting;

namespace Carrel.Api.Books;

/// <summary>
/// Client for the Google Books API, used for publishers' descriptions. Without an API key (GoogleBooks:ApiKey) it's
/// switched off and books keep their other sources' descriptions.
/// </summary>
public class GoogleBooksClient(HttpClient http, IConfiguration configuration, ILogger<GoogleBooksClient> logger)
{
    // Google allows 100 queries a minute and 1,000 a day; this stays under the minute limit.
    private static readonly TokenBucketRateLimiter Throttle = new(new TokenBucketRateLimiterOptions
    {
        TokenLimit = 9,
        TokensPerPeriod = 9,
        ReplenishmentPeriod = TimeSpan.FromSeconds(6),
        QueueLimit = 20,
        QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
    });

    // A 429 usually means the daily quota is spent, so Google isn't asked again for a while.
    private static readonly TimeSpan PauseAfterLimit = TimeSpan.FromHours(1);
    private static DateTimeOffset pausedUntil;

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    private readonly string? apiKey = configuration["GoogleBooks:ApiKey"];

    public bool IsConfigured => !string.IsNullOrEmpty(apiKey);

    /// <summary>The book's page on Google Books, which its branding guidelines ask descriptions to link to.</summary>
    public static string VolumeUrl(string volumeId) => $"https://books.google.com/books?id={Uri.EscapeDataString(volumeId)}";

    public async Task<GoogleBooksVolume[]> SearchAsync(string query, CancellationToken ct) =>
        (await GetAsync<GoogleBooksSearch>(
            $"volumes?q={Uri.EscapeDataString(query)}&maxResults=20&fields=items(id,volumeInfo(language,industryIdentifiers,description),accessInfo(viewability),saleInfo(saleability))", ct))?.Items
        ?? [];

    public Task<GoogleBooksVolume?> GetVolumeAsync(string volumeId, CancellationToken ct) =>
        GetAsync<GoogleBooksVolume>($"volumes/{Uri.EscapeDataString(volumeId)}?fields=id,volumeInfo(language,industryIdentifiers,description)", ct);

    /// <summary>Returns null for 404.</summary>
    private async Task<T?> GetAsync<T>(string path, CancellationToken ct) where T : class
    {
        if (DateTimeOffset.UtcNow < pausedUntil)
        {
            throw new BookSourceUnavailableException("Google Books is paused after reaching its limit.");
        }
        using var lease = await Throttle.AcquireAsync(1, ct);
        if (!lease.IsAcquired)
        {
            throw new BookSourceUnavailableException("Too many Google Books requests are queued.");
        }

        try
        {
            // The key goes in a header, not the URL, so it stays out of request logs.
            using var request = new HttpRequestMessage(HttpMethod.Get, path);
            request.Headers.Add("X-Goog-Api-Key", apiKey);
            using var response = await http.SendAsync(request, ct);
            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                return null;
            }
            if (response.StatusCode == HttpStatusCode.TooManyRequests)
            {
                pausedUntil = DateTimeOffset.UtcNow + PauseAfterLimit;
                logger.LogWarning("Google Books returned 429; pausing until {PausedUntil}", pausedUntil);
                throw new BookSourceUnavailableException("Google Books returned 429.");
            }
            if (!response.IsSuccessStatusCode)
            {
                logger.LogWarning("Google Books returned {Status} for {Path}", (int)response.StatusCode, path);
                throw new BookSourceUnavailableException($"Google Books returned {(int)response.StatusCode}.");
            }
            return await response.Content.ReadFromJsonAsync<T>(JsonOptions, ct);
        }
        catch (Exception e) when (e is HttpRequestException or JsonException
                                      || (e is TaskCanceledException && !ct.IsCancellationRequested))
        {
            logger.LogWarning(e, "Google Books request failed for {Path}", path);
            throw new BookSourceUnavailableException("Google Books request failed.");
        }
    }
}
