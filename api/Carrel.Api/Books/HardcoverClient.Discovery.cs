namespace Carrel.Api.Books;

// Queries behind book suggestions: a book's tags, lists of books matching filters, and what's trending.
public partial class HardcoverClient
{
    private const string BookTagsQuery = """
        query BookTags($id: Int!) {
          books_by_pk(id: $id) {
            cached_tags
            contributions { contribution author { id name } }
            book_series { position featured series { id name } }
            taggings(distinct_on: tag_id, where: {tag: {tag_category: {slug: {_in: ["genre", "mood"]}}}}) {
              tag { id tag count tag_category { slug } }
            }
          }
        }
        """;

    private const string TrendingQuery = """
        query Trending($from: date!, $to: date!, $limit: Int!) {
          books_trending(from: $from, to: $to, limit: $limit, offset: 0) { ids }
        }
        """;

    private const string ListedBookFields = """
        id title release_year users_count cached_image genres: cached_tags(path: "Genre")
        contributions { contribution author { id name } }
        book_series { position featured series { id name } }
        """;

    /// <summary>Hardcover allows this many top-level queries in one request, each counted against its rate limits.</summary>
    public const int MaxQueriesPerRequest = 5;

    /// <summary>The book's genre and mood tags, with its authors and series; null if not found.</summary>
    public async Task<HardcoverBookTags?> GetBookTagsAsync(int id, CancellationToken ct) =>
        (await QueryAsync<HardcoverBookTagsData>(BookTagsQuery, new { id }, ct)).BooksByPk;

    /// <summary>Ids of the books most read on Hardcover between the two dates, most first.</summary>
    public async Task<int[]> GetTrendingIdsAsync(DateOnly from, DateOnly to, int limit, CancellationToken ct) =>
        (await QueryAsync<HardcoverTrendingData>(TrendingQuery, new { from, to, limit }, ct)).BooksTrending.Ids;

    /// <summary>
    /// One list of books per filter (a Hardcover books_bool_exp), in <paramref name="orderBy"/> order (most-read first by
    /// default), fetched in a single request. It saves round trips but not quota: Hardcover counts each list as a request.
    /// </summary>
    public async Task<HardcoverListedBook[][]> ListBooksAsync(
        IReadOnlyList<object> filters, int limit, CancellationToken ct, string orderBy = "{users_count: desc}")
    {
        if (filters.Count is 0 or > MaxQueriesPerRequest)
        {
            throw new ArgumentOutOfRangeException(nameof(filters), $"Give 1 to {MaxQueriesPerRequest} filters.");
        }
        var parameters = filters.Select((_, i) => $"$w{i}: books_bool_exp!");
        var lists = filters.Select((_, i) =>
            $"w{i}: books(where: $w{i}, order_by: {orderBy}, limit: {limit}) {{ {ListedBookFields} }}");
        var query = $"query ListBooks({string.Join(", ", parameters)}) {{ {string.Join("\n", lists)} }}";
        var variables = filters.Select((filter, i) => (filter, i)).ToDictionary(x => $"w{x.i}", x => x.filter);

        var data = await QueryAsync<Dictionary<string, HardcoverListedBook[]>>(query, variables, ct, filters.Count);
        return filters.Select((_, i) => data.GetValueOrDefault($"w{i}") ?? []).ToArray();
    }
}
