using System.Security.Claims;
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

        library.MapGet("/books/{bookId:long}", async (long bookId, ClaimsPrincipal user, LibraryService service, CancellationToken ct) =>
            await service.GetAsync(UserId(user), bookId, ct) is { } entry ? Results.Ok(entry) : Results.NotFound());

        library.MapPut("/books/{bookId:long}", (long bookId, SaveEntryRequest request, ClaimsPrincipal user, LibraryService service,
                CancellationToken ct) =>
            Validated(async () => await service.SaveAsync(UserId(user), bookId, request, ct) is { } entry
                ? Results.Ok(entry)
                : Results.NotFound()));

        library.MapDelete("/books/{bookId:long}", async (long bookId, ClaimsPrincipal user, LibraryService service, CancellationToken ct) =>
            await service.DeleteAsync(UserId(user), bookId, ct) ? Results.NoContent() : Results.NotFound());
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
    }
}
