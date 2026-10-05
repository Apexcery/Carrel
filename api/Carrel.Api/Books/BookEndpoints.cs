namespace Carrel.Api.Books;

public static class BookEndpoints
{
    /// <summary>Rate limit policy for endpoints that may call external book sources.</summary>
    public const string BookSourcesRateLimit = "book-sources";

    private const int MaxQueryLength = 200;
    private const int MaxPage = 50;

    public static void MapBookEndpoints(this IEndpointRouteBuilder app)
    {
        // Browsing is public: anyone can search and read book and series pages; only the library needs signing in.
        var books = app.MapGroup("/books").AllowAnonymous().RequireRateLimiting(BookSourcesRateLimit);

        books.MapGet("/search", (string q, int? page, BookService service, CancellationToken ct) =>
        {
            if (string.IsNullOrWhiteSpace(q) || q.Length > MaxQueryLength || page is < 1 or > MaxPage)
            {
                return Task.FromResult(Results.ValidationProblem(new Dictionary<string, string[]>
                {
                    ["q"] = [$"Search text is required, up to {MaxQueryLength} characters; page must be 1 to {MaxPage}."],
                }));
            }
            return FromBookSource(async () => Results.Ok(await service.SearchAsync(q, page ?? 1, ct)));
        });

        books.MapGet("/hardcover/{hardcoverId:int}", (int hardcoverId, BookService service, CancellationToken ct) =>
            FromBookSource(async () => await service.GetByHardcoverIdAsync(hardcoverId, ct) is { } book
                ? Results.Ok(book)
                : Results.NotFound()));

        books.MapGet("/openlibrary/{workId:regex(^OL\\d+W$)}", (string workId, BookService service, CancellationToken ct) =>
            FromBookSource(async () => await service.GetByOpenLibraryIdAsync(workId, ct) is { } book
                ? Results.Ok(book)
                : Results.NotFound()));

        books.MapGet("/isbn/{isbn}", (string isbn, BookService service, CancellationToken ct) =>
            BookService.NormalizeIsbn(isbn) is { } normalized
                ? FromBookSource(async () => await service.GetByIsbnAsync(normalized, ct) is { } book
                    ? Results.Ok(book)
                    : Results.NotFound())
                : Task.FromResult(Results.ValidationProblem(new Dictionary<string, string[]>
                {
                    ["isbn"] = ["Must be a 10- or 13-character ISBN."],
                })));

        app.MapGroup("/series").AllowAnonymous().RequireRateLimiting(BookSourcesRateLimit)
            .MapGet("/hardcover/{hardcoverId:int}", (int hardcoverId, SeriesService service, CancellationToken ct) =>
                FromBookSource(async () => await service.GetByHardcoverIdAsync(hardcoverId, ct) is { } series
                    ? Results.Ok(series)
                    : Results.NotFound()));

        books.MapGet("/{id:long}", (long id, BookService service, CancellationToken ct) =>
            FromBookSource(async () => await service.GetByIdAsync(id, ct) is { } book
                ? Results.Ok(book)
                : Results.NotFound()));

        books.MapGet("/{id:long}/related", (long id, RecommendationService service, CancellationToken ct) =>
            FromBookSource(async () => await service.GetRelatedAsync(id, ct) is { } related
                ? Results.Ok(related)
                : Results.NotFound()));

        // Outside /books, so the home page doesn't count against signed-out visitors' book lookups: it's the same
        // shelves for everyone, cached for a day.
        app.MapGet("/discover", (RecommendationService service, CancellationToken ct) =>
                FromBookSource(async () => Results.Ok(await service.GetDiscoverAsync(ct))))
            .AllowAnonymous();
    }

    internal static async Task<IResult> FromBookSource(Func<Task<IResult>> action)
    {
        try
        {
            return await action();
        }
        catch (BookSourceUnavailableException)
        {
            return Results.Problem(
                statusCode: StatusCodes.Status503ServiceUnavailable,
                title: "Book data is temporarily unavailable",
                detail: "Try again in a few minutes.");
        }
    }
}
