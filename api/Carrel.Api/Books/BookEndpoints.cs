namespace Carrel.Api.Books;

public static class BookEndpoints
{
    /// <summary>Rate limit policy for endpoints that may call external book sources.</summary>
    public const string BookSourcesRateLimit = "book-sources";

    private const int MaxQueryLength = 200;
    private const int MaxPage = 50;

    public static void MapBookEndpoints(this IEndpointRouteBuilder app)
    {
        var books = app.MapGroup("/books").RequireRateLimiting(BookSourcesRateLimit);

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

        books.MapGet("/{id:long}", (long id, BookService service, CancellationToken ct) =>
            FromBookSource(async () => await service.GetByIdAsync(id, ct) is { } book
                ? Results.Ok(book)
                : Results.NotFound()));
    }

    private static async Task<IResult> FromBookSource(Func<Task<IResult>> action)
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
