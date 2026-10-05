using System.Security.Claims;
using System.Text;
using Carrel.Api.Books;

namespace Carrel.Api.Imports;

public static class ImportEndpoints
{
    /// <summary>Authorisation policy for the batch endpoint: a Google identity token for the API's own service account.</summary>
    public const string CloudTasksPolicy = "cloud-tasks";

    /// <summary>Authentication scheme for Cloud Tasks' Google identity tokens, kept apart from readers' Supabase tokens.</summary>
    public const string CloudTasksScheme = "CloudTasks";

    /// <summary>Exports run to a few hundred KB; this leaves plenty of room, reviews included.</summary>
    private const int MaxUploadBytes = 10 * 1024 * 1024;

    public static void MapImportEndpoints(this IEndpointRouteBuilder app, bool cloudTasks)
    {
        var imports = app.MapGroup("/imports");

        // The CSV is the request body, so there's no form to parse; ?existing=overwrite replaces books already in the library.
        imports.MapPost("/", async (HttpRequest request, string? existing, ClaimsPrincipal user, ImportService service, CancellationToken ct) =>
            {
                if (request.ContentLength is not > 0 || request.ContentLength > MaxUploadBytes)
                {
                    return Problem("file", $"Upload an export of up to {MaxUploadBytes / 1024 / 1024} MB.");
                }
                if (existing is not (null or "skip" or "overwrite"))
                {
                    return Problem("existing", "Choose to skip or overwrite books already in your library.");
                }
                using var reader = new StreamReader(request.Body, Encoding.UTF8);
                var csv = await reader.ReadToEndAsync(ct);
                try
                {
                    return Results.Ok(await service.CreateAsync(UserId(user), csv, existing == "overwrite", ct));
                }
                catch (ImportFileException e)
                {
                    return Problem("file", e.Message);
                }
            })
            .RequireRateLimiting(BookEndpoints.BookSourcesRateLimit);

        imports.MapGet("/latest", async (ClaimsPrincipal user, ImportService service, CancellationToken ct) =>
            await service.LatestAsync(UserId(user), ct) is { } status ? Results.Ok(status) : Results.NotFound());

        imports.MapPost("/{id:long}/resume", async (long id, ClaimsPrincipal user, ImportService service, CancellationToken ct) =>
            await service.ResumeAsync(UserId(user), id, ct) is { } status ? Results.Ok(status) : Results.NotFound());

        imports.MapGet("/{id:long}/review", async (long id, ClaimsPrincipal user, ImportService service, CancellationToken ct) =>
            await service.ReviewAsync(UserId(user), id, ct) is { } items ? Results.Ok(items) : Results.NotFound());

        imports.MapPost("/{id:long}/items/{itemId:long}/confirm",
            async (long id, long itemId, ClaimsPrincipal user, ImportService service, CancellationToken ct) =>
                await service.ConfirmAsync(UserId(user), id, itemId, ct) ? Results.NoContent() : Results.NotFound());

        imports.MapPost("/{id:long}/items/{itemId:long}/dismiss",
            async (long id, long itemId, ClaimsPrincipal user, ImportService service, CancellationToken ct) =>
                await service.DismissAsync(UserId(user), id, itemId, ct) ? Results.NoContent() : Results.NotFound());

        imports.MapPost("/{id:long}/items/{itemId:long}/choose",
                (long id, long itemId, ChooseBookRequest request, ClaimsPrincipal user, ImportService service, CancellationToken ct) =>
                    BookEndpoints.FromBookSource(async () => await service.ChooseAsync(UserId(user), id, itemId, request, ct)
                        ? Results.NoContent()
                        : Results.NotFound()))
            .RequireRateLimiting(BookEndpoints.BookSourcesRateLimit);

        if (cloudTasks)
        {
            // Called by Cloud Tasks for each batch. 503 while another batch holds the import, so Cloud Tasks retries later.
            app.MapPost("/internal/imports/{id:long}/process", async (long id, ImportProcessor processor, CancellationToken ct) =>
                    await processor.ProcessAsync(id, ct) ? Results.Ok() : Results.StatusCode(StatusCodes.Status503ServiceUnavailable))
                .RequireAuthorization(CloudTasksPolicy);
        }
    }

    private static Guid UserId(ClaimsPrincipal user) => Guid.Parse(user.FindFirstValue("sub")!);

    private static IResult Problem(string field, string message) =>
        Results.ValidationProblem(new Dictionary<string, string[]> { [field] = [message] });
}
