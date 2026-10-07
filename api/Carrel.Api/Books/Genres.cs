using System.Text.RegularExpressions;

namespace Carrel.Api.Books;

/// <summary>
/// The genres listed for browsing, picked by hand: Hardcover's own genre list mixes in duplicates ("Comics &amp;
/// Graphic Novels"), umbrellas ("General"), and odd casing. Names must match Hardcover's genre tags exactly. Other
/// genres can still be found by searching the genres page, or from a book page.
/// </summary>
public static partial class Genres
{
    private static readonly string[] FictionNames =
    [
        "Fantasy", "Science Fiction", "Romance", "Romantasy", "Mystery", "Thriller", "Crime", "Horror",
        "Historical Fiction", "Literary Fiction", "Contemporary", "Classics", "Dystopian", "Adventure", "Young Adult",
        "Middle Grade", "Comics", "Manga", "Humor", "Poetry",
    ];

    private static readonly string[] NonfictionNames =
    [
        "Biography", "Memoir", "History", "Philosophy", "Science", "Psychology", "Self-Help", "Business", "Politics",
        "Religion", "Travel", "Cooking", "Art", "Music",
    ];

    public static readonly GenreLink[] Fiction = FictionNames.Select(ToLink).ToArray();

    public static readonly GenreLink[] Nonfiction = NonfictionNames.Select(ToLink).ToArray();

    public static IEnumerable<GenreLink> Listed => Fiction.Concat(Nonfiction);

    /// <summary>
    /// A genre's address on Carrel, made from its name ("Self-Help" is self-help). Hardcover's own slugs have random
    /// suffixes on some genres, so they aren't used. The website makes the same (web/src/genres.ts).
    /// </summary>
    public static string Slug(string name) =>
        NotSlugCharacters().Replace(name.ToLowerInvariant().Replace("&", " and ").Replace("'", "").Replace("’", ""), "-")
            .Trim('-');

    public static GenreLink ToLink(string name) => new(name, Slug(name));

    [GeneratedRegex("[^a-z0-9]+")]
    private static partial Regex NotSlugCharacters();
}
