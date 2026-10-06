using System.Net;
using SkiaSharp;

namespace Carrel.Api.Profiles;

/// <summary>An upload that isn't a picture Carrel can use; the message is shown to the reader.</summary>
public class ProfilePictureException(string message) : Exception(message);

/// <summary>Supabase Storage could not be reached, or the secret key is missing or rejected.</summary>
public class PictureStorageUnavailableException(string message, Exception? inner = null) : Exception(message, inner);

/// <summary>
/// Readers' profile pictures, in Supabase Storage's public "avatars" bucket (created by the AddProfilePictures
/// migration). Uploads are decoded and re-encoded here, never stored as sent: anything that isn't a real image is
/// refused, and the stored copy carries no metadata (a phone photo's location, for one). Calls use the project's secret
/// key (Supabase:SecretKey), read per call as SupabaseAuthClient does.
/// </summary>
public class ProfilePictures(HttpClient http, IConfiguration config, ILogger<ProfilePictures> logger)
{
    public const string Bucket = "avatars";

    /// <summary>The most an upload can be; the website sends a cropped copy far smaller than this.</summary>
    public const int MaxUploadBytes = 5 * 1024 * 1024;

    /// <summary>Stored pictures are this many pixels square, enough to view larger on the profile.</summary>
    private const int Size = 512;

    /// <summary>
    /// Larger images aren't decoded, so a small file can't unpack into one that fills the API's memory (512 MiB). The
    /// website sends 512 pixels square.
    /// </summary>
    private const int MaxSourcePixels = 16_000_000;

    /// <summary>
    /// How long browsers and Supabase's CDN keep a picture. Short, because on the Free plan the CDN isn't told when a
    /// file is deleted or moved: a removed picture, or one renamed when its profile went private, stays reachable at
    /// its old address until this runs out.
    /// </summary>
    private const string CacheControl = "max-age=600";

    private const int WebpQuality = 80;

    /// <summary>The picture's public address, for the website.</summary>
    public string? PublicUrl(string? path) =>
        path is null ? null : $"{config["Supabase:Url"]}/storage/v1/object/public/{Bucket}/{path}";

    /// <summary>
    /// Re-encodes the upload as a square WebP (the middle square, if it isn't one already) and stores it under a new
    /// name, so no one sees a cached old picture. Returns its path.
    /// </summary>
    public async Task<string> SaveAsync(Guid userId, byte[] upload, CancellationToken ct)
    {
        var picture = Reencode(upload);
        var path = NewPath(userId);
        using var request = new HttpRequestMessage(HttpMethod.Post, $"object/{Bucket}/{path}")
        {
            Content = new ByteArrayContent(picture) { Headers = { ContentType = new("image/webp") } },
        };
        request.Headers.Add("cache-control", CacheControl);
        using var response = await SendAsync(request, ct);
        if (!response.IsSuccessStatusCode)
        {
            throw Unexpected("storing a profile picture", response.StatusCode);
        }
        return path;
    }

    /// <summary>
    /// Moves the picture to a new name and returns it, so its old address stops working: done when a profile goes
    /// private, since the bucket is public and anyone who had the address could otherwise still open it.
    /// </summary>
    public async Task<string> RenameAsync(Guid userId, string path, CancellationToken ct)
    {
        var renamed = NewPath(userId);
        using var request = new HttpRequestMessage(HttpMethod.Post, "object/move")
        {
            Content = JsonContent.Create(new { bucketId = Bucket, sourceKey = path, destinationKey = renamed }),
        };
        using var response = await SendAsync(request, ct);
        if (!response.IsSuccessStatusCode)
        {
            throw Unexpected("renaming a profile picture", response.StatusCode);
        }
        return renamed;
    }

    /// <summary>Deletes a stored picture. Already gone counts as done.</summary>
    public async Task DeleteAsync(string path, CancellationToken ct)
    {
        using var request = new HttpRequestMessage(HttpMethod.Delete, $"object/{Bucket}")
        {
            Content = JsonContent.Create(new { prefixes = new[] { path } }),
        };
        using var response = await SendAsync(request, ct);
        if (!response.IsSuccessStatusCode && response.StatusCode != HttpStatusCode.NotFound)
        {
            throw Unexpected("deleting a profile picture", response.StatusCode);
        }
    }

    /// <summary>A folder per reader and an unguessable file name.</summary>
    private static string NewPath(Guid userId) => $"{userId}/{Guid.NewGuid():N}.webp";

    private static byte[] Reencode(byte[] upload)
    {
        using var codec = SKCodec.Create(new SKMemoryStream(upload))
            ?? throw new ProfilePictureException("That file isn’t a picture Carrel can read. Try a JPEG, PNG, or WebP.");
        if ((long)codec.Info.Width * codec.Info.Height > MaxSourcePixels)
        {
            throw new ProfilePictureException("That picture is too large. Try a smaller one.");
        }
        using var source = SKBitmap.Decode(codec)
            ?? throw new ProfilePictureException("That file isn’t a picture Carrel can read. Try a JPEG, PNG, or WebP.");

        var side = Math.Min(source.Width, source.Height);
        var crop = SKRectI.Create((source.Width - side) / 2, (source.Height - side) / 2, side, side);
        using var surface = SKSurface.Create(new SKImageInfo(Size, Size, SKColorType.Rgba8888, SKAlphaType.Premul));
        using (var image = SKImage.FromBitmap(source))
        {
            surface.Canvas.DrawImage(image, crop, SKRect.Create(Size, Size),
                new SKSamplingOptions(SKCubicResampler.Mitchell), null);
        }
        using var result = surface.Snapshot();
        using var data = result.Encode(SKEncodedImageFormat.Webp, WebpQuality);
        return data.ToArray();
    }

    private async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
    {
        var key = config["Supabase:SecretKey"];
        if (string.IsNullOrEmpty(key))
        {
            throw new PictureStorageUnavailableException("Supabase:SecretKey is not configured.");
        }
        request.Headers.Add("apikey", key);
        request.Headers.Authorization = new("Bearer", key);
        try
        {
            return await http.SendAsync(request, ct);
        }
        catch (HttpRequestException e)
        {
            throw new PictureStorageUnavailableException("Supabase Storage could not be reached.", e);
        }
    }

    private PictureStorageUnavailableException Unexpected(string action, HttpStatusCode status)
    {
        logger.LogError("Supabase Storage answered {Status} when {Action}", (int)status, action);
        return new PictureStorageUnavailableException($"Supabase Storage answered {(int)status} when {action}.");
    }
}
