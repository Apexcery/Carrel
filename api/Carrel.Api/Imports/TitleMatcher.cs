using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using Carrel.Api.Books;

namespace Carrel.Api.Imports;

/// <summary>
/// Matches an export row without a usable id to a Hardcover search result. It errs towards no match, since a wrong
/// book in the library is worse than one the reader picks by hand: the title has to agree (either side of a colon),
/// volume numbers have to be the same, and an author has to appear on both.
/// </summary>
public static partial class TitleMatcher
{
    /// <summary>What to search for: the title without bracketed notes like "(Mistborn, #1)", and the first author.</summary>
    public static string SearchQuery(string title, string[] authors) =>
        $"{Brackets().Replace(title, " ").Trim()} {authors.FirstOrDefault()}".Trim();

    /// <summary>The first result that matches the row, or null.</summary>
    public static HardcoverSearchDocument? FindMatch(string title, string[] authors, IEnumerable<HardcoverSearchDocument> hits)
    {
        var titles = TitleVariants(title);
        // Volume numbers in the title must all be in the result's, and the result's must be in the title or its
        // bracketed series note: "Running the Risk (The Shapeshifter, #2)" is "The Shapeshifter 2: Running the Risk",
        // but "Mother of Learning" isn't "Mother of Learning: ARC 1".
        var numbers = Numbers(Normalize(Brackets().Replace(title, " ")));
        var allowed = Numbers(Normalize(title));
        var names = authors.Select(NameKey).Where(n => n.Length > 0).ToHashSet();
        return hits.FirstOrDefault(hit =>
        {
            var full = string.IsNullOrWhiteSpace(hit.Subtitle) ? hit.Title : $"{hit.Title}: {hit.Subtitle}";
            // Only the main title's numbers: Hardcover subtitles often carry the series, like "Cradle, Book 12".
            var hitNumbers = Numbers(Normalize(hit.Title));
            // The title on its own too, so a subtitle can't hide the part after its colon ("Mistborn: Secret History").
            return (TitleVariants(full).Overlaps(titles) || TitleVariants(hit.Title).Overlaps(titles))
                   && numbers.IsSubsetOf(hitNumbers) && hitNumbers.IsSubsetOf(allowed)
                   && (hit.AuthorNames ?? []).Any(a => names.Contains(NameKey(a)));
        });
    }

    /// <summary>The whole title, and the parts either side of its first colon ("Mistborn: Secret History").</summary>
    private static HashSet<string> TitleVariants(string title)
    {
        title = Brackets().Replace(title, " ");
        var colon = title.IndexOf(':');
        string[] variants = colon < 0 ? [title] : [title, title[..colon], title[(colon + 1)..]];
        return variants.Select(Normalize).Where(v => v.Length > 0).ToHashSet();
    }

    private static HashSet<string> Numbers(string normalized) => Digits().Matches(normalized).Select(m => m.Value.TrimStart('0')).ToHashSet();

    /// <summary>Lower case, accents and punctuation removed, "&amp;" as "and", single spaces.</summary>
    private static string Normalize(string text)
    {
        var builder = new StringBuilder();
        foreach (var c in text.Replace("&", " and ").Normalize(NormalizationForm.FormD).ToLowerInvariant())
        {
            if (CharUnicodeInfo.GetUnicodeCategory(c) == UnicodeCategory.NonSpacingMark || c is '\'' or '’')
            {
                continue;
            }
            builder.Append(char.IsLetterOrDigit(c) ? c : ' ');
        }
        return Spaces().Replace(builder.ToString(), " ").Trim();
    }

    /// <summary>A name with spacing and punctuation dropped, so "J.K. Rowling" and "J. K. Rowling" agree.</summary>
    private static string NameKey(string name) => Normalize(name).Replace(" ", "");

    [GeneratedRegex(@"\([^)]*\)|\[[^\]]*\]")]
    private static partial Regex Brackets();

    [GeneratedRegex(@"\d+")]
    private static partial Regex Digits();

    [GeneratedRegex(@"\s+")]
    private static partial Regex Spaces();
}
