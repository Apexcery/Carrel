using System.Security.Claims;
using System.Text;
using Carrel.Api.Books;

namespace Carrel.Api.Library;

public static class LibraryEndpoints
{
    public static void MapLibraryEndpoints(this IEndpointRouteBuilder app)
    {
        var library = app.MapGroup("/library");

        library.MapGet("/", (ClaimsPrincipal user, LibraryService service, CancellationToken ct) =>
            service.ListAsync(UserId(user), ct));

        library.MapGet("/next-in-series", (ClaimsPrincipal user, RecommendationService service, CancellationToken ct) =>
                service.GetNextInSeriesAsync(UserId(user), ct))
            .RequireRateLimiting(BookEndpoints.BookSourcesRateLimit);

        library.MapGet("/genre-picks", (ClaimsPrincipal user, RecommendationService service, CancellationToken ct) =>
                service.GetGenrePicksAsync(UserId(user), ct))
            .RequireRateLimiting(BookEndpoints.BookSourcesRateLimit);

        // ?format=goodreads (Goodreads' columns) or carrel (everything). The website names the downloaded file.
        library.MapGet("/export", async (string? format, ClaimsPrincipal user, LibraryExport export, CancellationToken ct) =>
            format?.ToLowerInvariant() switch
            {
                "goodreads" => Results.Text(await export.WriteAsync(UserId(user), ExportFormat.Goodreads, ct), "text/csv", Encoding.UTF8),
                "carrel" => Results.Text(await export.WriteAsync(UserId(user), ExportFormat.Carrel, ct), "text/csv", Encoding.UTF8),
                _ => Results.ValidationProblem(new Dictionary<string, string[]> { ["format"] = ["Choose goodreads or carrel."] }),
            });

        library.MapGet("/books/{bookId:long}", async (long bookId, ClaimsPrincipal user, LibraryService service, CancellationToken ct) =>
            await service.GetAsync(UserId(user), bookId, ct) is { } entry ? Results.Ok(entry) : Results.NotFound());

        library.MapPut("/books/{bookId:long}", (long bookId, SaveEntryRequest request, ClaimsPrincipal user, LibraryService service,
                CancellationToken ct) =>
            Validated(async () => await service.SaveAsync(UserId(user), bookId, request, ct) is { } entry
                ? Results.Ok(entry)
                : Results.NotFound()));

        // Where the reader is in a book they're reading in the app, to carry on from on another device.
        library.MapGet("/books/{bookId:long}/position", async (long bookId, ClaimsPrincipal user, LibraryService service, CancellationToken ct) =>
            await service.GetPositionAsync(UserId(user), bookId, ct) is { } position ? Results.Ok(position) : Results.NotFound());

        library.MapPut("/books/{bookId:long}/position", (long bookId, SavePositionRequest request, ClaimsPrincipal user, LibraryService service,
                CancellationToken ct) =>
            Validated(async () => await service.SavePositionAsync(UserId(user), bookId, request, ct) is { } position
                ? Results.Ok(position)
                : Results.NotFound()));

        // ?changedAt= for a removal the app made offline (see SaveEntryRequest.ChangedAt).
        library.MapDelete("/books/{bookId:long}", (long bookId, DateTimeOffset? changedAt, ClaimsPrincipal user, LibraryService service,
                CancellationToken ct) =>
            Validated(async () => await service.DeleteAsync(UserId(user), bookId, changedAt, ct) ? Results.NoContent() : Results.NotFound()));
    }

    /// <summary>The Supabase user id from the validated token; every library query is scoped to it.</summary>
    private static Guid UserId(ClaimsPrincipal user) => Guid.Parse(user.FindFirstValue("sub")!);

    private static async Task<IResult> Validated(Func<Task<IResult>> action)
    {
        try
        {
            return await action();
        }
        catch (LibraryValidationException e)
        {
            return Results.ValidationProblem(new Dictionary<string, string[]> { [e.Field] = [e.Message] });
        }
        catch (LibraryConflictException e)
        {
            return Results.Problem(e.Message, statusCode: StatusCodes.Status409Conflict);
        }
    }
}
