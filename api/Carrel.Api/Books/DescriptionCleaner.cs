using System.Net;
using System.Text;
using System.Text.RegularExpressions;

namespace Carrel.Api.Books;

/// <summary>
/// Turns a stored description into plain paragraphs separated by blank lines, without the publisher's marketing:
/// press quotes, "Praise for…" and "Other books by…" sections, edition notes, and sentences selling the author or
/// series. Google's descriptions are HTML; Hardcover's and Open Library's are plain text, sometimes with Markdown.
/// The rules are rules of thumb, tuned on real descriptions, so a description they'd mostly remove is kept whole.
/// </summary>
public static partial class DescriptionCleaner
{
    /// <summary>A line of text; bold if every letter is in bold, or it's in capitals (how publishers set marketing).</summary>
    private record Line(string Text, bool Bold);

    public static string? Clean(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw))
        {
            return null;
        }

        var paragraphs = Parse(raw);
        var kept = new List<string>();
        var backMatter = false;
        foreach (var paragraph in paragraphs)
        {
            if (backMatter)
            {
                break;
            }
            // A quote with its source on the next line: 'Gripping'<br>GUARDIAN.
            if (paragraph.Count <= 3 && IsAttributedQuote(string.Join(" ", paragraph.Select(l => l.Text))))
            {
                continue;
            }

            var lines = new List<string>();
            foreach (var line in paragraph)
            {
                if (!LetterOrDigit().IsMatch(line.Text))
                {
                    continue;
                }
                if (BackMatterHeading().IsMatch(line.Text))
                {
                    // Everything after "Praise for…" or "Other books by…" goes; at the very start it's just a notice.
                    backMatter = kept.Count > 0 || lines.Count > 0;
                    if (backMatter)
                    {
                        break;
                    }
                    continue;
                }
                if (IsPressQuote(line) || ListItem().IsMatch(line.Text) || line.Text.Count(c => c is '•' or '●') >= 2)
                {
                    continue;
                }

                var sentences = SentenceEnd().Split(line.Text);
                if (line.Bold && sentences.Any(s => Marketing().IsMatch(s)))
                {
                    continue;
                }
                var story = sentences
                    .Select(s => Marketing().IsMatch(s) ? TrimIntro(s) : s)
                    .OfType<string>()
                    .ToList();
                if (story.Count > 0)
                {
                    lines.Add(string.Join(" ", story));
                }
            }
            if (lines.Count > 0)
            {
                kept.Add(string.Join(" ", lines));
            }
        }
        // A heading left with nothing after it, once the quotes or list it introduced are gone ("Reader reviews:").
        while (kept.Count > 0 && kept[^1].EndsWith(':'))
        {
            kept.RemoveAt(kept.Count - 1);
        }

        var whole = string.Join("\n\n", paragraphs.Select(p => string.Join(" ", p.Select(l => l.Text))));
        var cleaned = string.Join("\n\n", kept);
        var result = cleaned.Length < 200 && cleaned.Length < whole.Length / 2 ? whole : cleaned;
        return result.Length > 0 ? result : null;
    }

    /// <summary>Splits HTML or plain text into paragraphs of lines, noting which lines are bold.</summary>
    private static List<List<Line>> Parse(string raw)
    {
        var paragraphs = new List<List<Line>>();
        var lines = new List<Line>();
        var current = new StringBuilder();
        var hasPlainLetters = false;
        var bold = 0;

        void EndLine()
        {
            var text = Spaces().Replace(current.ToString(), " ").Trim();
            if (text.Length > 0)
            {
                var letters = text.Where(char.IsAsciiLetter).ToList();
                var capitals = letters.Count >= 6 && letters.All(char.IsUpper);
                lines.Add(new Line(text, !hasPlainLetters || capitals));
            }
            current.Clear();
            hasPlainLetters = false;
        }

        void EndParagraph()
        {
            EndLine();
            if (lines.Count > 0)
            {
                paragraphs.Add(lines);
                lines = [];
            }
        }

        foreach (var token in Tags().Split(raw.Replace("\r\n", "\n")))
        {
            if (token.StartsWith('<'))
            {
                switch (TagName().Match(token).Groups[1].Value.ToLowerInvariant())
                {
                    case "b" or "strong":
                        bold = Math.Max(0, bold + (token.StartsWith("</") ? -1 : 1));
                        break;
                    case "br":
                        // Two in a row end the paragraph.
                        if (string.IsNullOrWhiteSpace(current.ToString()) && lines.Count > 0)
                        {
                            EndParagraph();
                        }
                        else
                        {
                            EndLine();
                        }
                        break;
                    case "p" or "div" or "li" or "ul" or "ol" or "h1" or "h2" or "h3" or "h4" or "h5" or "h6":
                        EndParagraph();
                        break;
                }
                continue;
            }

            var text = WebUtility.HtmlDecode(token).Replace(' ', ' ');
            text = C1Controls().Replace(text, m => Windows1252(m.Value[0]));
            text = MarkdownBold().Replace(text, "$2");
            text = MarkdownItalic().Replace(text, "$1");
            foreach (var part in LineBreaks().Split(text))
            {
                if (part.Contains('\n'))
                {
                    if (part.Count(c => c == '\n') > 1)
                    {
                        EndParagraph();
                    }
                    else
                    {
                        EndLine();
                    }
                    continue;
                }
                // Google loses some paragraph breaks: "series.The long path", "ENTER HEREThe Eastern Empire".
                var marked = LostBreak().Replace(part, "$1\u0001$2");
                marked = LostBreakAfterCapitals().Replace(marked, "$1\u0001$2");
                var pieces = marked.Split('\u0001');
                for (var i = 0; i < pieces.Length; i++)
                {
                    if (i > 0 || (current.Length > 0 && current[^1] is ('.' or '!' or '?' or '…') && StartsWord().IsMatch(pieces[i])))
                    {
                        EndParagraph();
                    }
                    if (bold == 0 && pieces[i].Any(char.IsAsciiLetter))
                    {
                        hasPlainLetters = true;
                    }
                    current.Append(pieces[i]);
                }
            }
        }
        EndParagraph();
        return paragraphs;
    }

    /// <summary>'Gripping' – Guardian; or, in bold without quote marks, Gripping – Guardian.</summary>
    private static bool IsPressQuote(Line line) =>
        IsAttributedQuote(line.Text)
        || (line.Bold && UnquotedSource().IsMatch(line.Text) && line.Text[^1] is not ('.' or '!' or '?'));

    /// <summary>A quotation followed by a short source, not by more of the story ("'I live for you,' I say.").</summary>
    private static bool IsAttributedQuote(string text)
    {
        if (text.Length == 0 || text[0] is not ('\'' or '"' or '‘' or '“'))
        {
            return false;
        }
        var close = text.LastIndexOfAny(['\'', '"', '’', '”']);
        if (close < 4)
        {
            return false;
        }
        var source = text[(close + 1)..].Trim(' ', '-', '–', '—', ',');
        return source.Length is > 0 and <= 120 && !SentenceInSource().IsMatch(source) && !Narration().IsMatch(source);
    }

    /// <summary>
    /// Keeps the story from a sentence that opens by selling: "From #1 bestselling author X, Warbreaker is the story
    /// of…" becomes "Warbreaker is the story of…". Null when there's no such split.
    /// </summary>
    private static string? TrimIntro(string sentence)
    {
        foreach (Match separator in IntroSeparator().Matches(sentence))
        {
            var intro = sentence[..separator.Index];
            var rest = sentence[(separator.Index + separator.Length)..];
            // A long "intro" is really the sentence itself, and what follows it only a fragment.
            if (intro.Length <= 120 && Marketing().IsMatch(intro) && !Marketing().IsMatch(rest) && rest.Length >= 40
                && (char.IsUpper(rest[0]) || Introductory().IsMatch(intro)))
            {
                return char.ToUpperInvariant(rest[0]) + rest[1..];
            }
        }
        return null;
    }

    private static string Windows1252(char c) => c switch
    {
        '\u0085' => "…",
        '\u0091' => "‘",
        '\u0092' => "’",
        '\u0093' => "“",
        '\u0094' => "”",
        '\u0096' => "–",
        '\u0097' => "—",
        _ => "",
    };

    [GeneratedRegex("""
        best-?sell|\#\s?1\b|new\ york\ times|sunday\ times|usa\ today|wall\ street\ journal
        |millions?\ (of\ )?(copies|readers|fans)|copies\ sold|award-?winning|\bwinner\ of\b|shortlisted|longlisted
        |goodreads\ choice|selected\ as|reading\ pick|book\ club\ pick|of\ all\ time
        |\btv\ (series|show|adaptation)|streaming\ (show|series)|major\ (tv|film|motion\ picture|series|movie)|motion\ picture|greenlit
        |\bnow\ an?\ (major\ |hit\ |\#1\ )?(netflix\ |amazon\ |apple\ tv\+?\ |hbo\ |prime\ video\ )?(film|movie|tv|series|motion\ picture|streaming)
        |soon\ to\ be\ a|perfect\ for\ (fans|readers)|for\ fans\ of|(fans|readers|lovers)\ of\ .{0,80}\ will\ (love|devour|enjoy)
        |narrated\ by\ (?-i:[A-Z][a-z]+\ [A-Z])|bonus\ (content|material|story|stories|chapter|scene)|exclusive\ (to\ this|bonus|content|material)
        |this\ (e-?book|edition|special\ edition|deluxe\ edition|collector'?s\ edition)
        |(deluxe|special|collector'?s|anniversary|illustrated|hardcover|paperback)\ edition
        |slipcase|sprayed\ edges|endpapers|digital\ rights\ management|\bdrm\b
        |includes\ (an|a)\ (excerpt|preview|sneak|teaser)|\bteaser\b|previously\ published\ as|newest\ addition\ to|don['’]t\ miss
        |\b(an|the)\ instant\ (\#1\ )?(bestseller|classic|hit)|internationally|international\ (bestsell|phenomenon|sensation)
        |(global|tiktok|publishing|international|worldwide|viral|booktok)\ (phenomenon|sensation)|record-breaking
        |\bdiscover\ (the|why)\ .{0,60}(that\ (started|everyone|launched)|series|saga|phenomenon)|translated\ into\ \d+\ languages|pre-?order
        |\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|final|next|latest|penultimate|\d+(st|nd|rd|th))
            \ (book|novel|instal?lment|volume|entry)\ (in|of)\b
        |(thrilling|stunning|epic|explosive|unforgettable|fearless|heart-?rending|riveting|captivating|electrifying|breathtaking|monumental|momentous)
            \ (conclusion|finale|sequel|follow-up|next\ instal?lment)
        |\bsequel\ to\b|\bprelude\ to\b|returns?\ to\ (the\ )?world\ of|\bauthor\ of\b|amazon\ customer|this\ description\ (comes|is\ taken)\ from
        """, RegexOptions.IgnoreCase | RegexOptions.IgnorePatternWhitespace)]
    private static partial Regex Marketing();

    [GeneratedRegex("""
        ^(praise\ for\b|acclaim\ for\b|(other\ |more\ )?(e-?)?books\ (by|from|in)\b|other\ .{0,20}(e-?books|books|titles|novels)\b
        |also\ by\b|also\ available\b|coming\ soon\b|writing\ as\b|look\ out\ for\b|also\ look\ out\b|read\ (all\ )?(them|the\ (books|series))
        |the\ story\ continues|((reader|customer|editorial)\ )?reviews?:?$|what\ (people|readers|critics)\ are\ saying|the\ .{0,50}\ (series|saga|trilogy|sequence|chronicles):?$)
        """, RegexOptions.IgnoreCase | RegexOptions.IgnorePatternWhitespace)]
    private static partial Regex BackMatterHeading();

    // Not after initials ("J.R.R. Tolkien") or titles ("Mr. Wednesday"); a closing quote may follow the full stop.
    [GeneratedRegex("""(?<!\b[A-Z]\.)(?<!\b(Mr|Ms|Mrs|Dr|St)\.)(?<=[.!?…]["'”’]?)\s+(?=[A-Z"'“‘])""")]
    private static partial Regex SentenceEnd();

    [GeneratedRegex(@"\S\s+[-–—]\s*[A-Z][\w .&'’]{1,40}$")]
    private static partial Regex UnquotedSource();

    [GeneratedRegex(@"[.!?]\s+[A-Z][a-z]+ [a-z]+")]
    private static partial Regex SentenceInSource();

    [GeneratedRegex(@"^(I|he|she|they|we|you|it)\b|\b(say|says|said|ask|asks|asked|whisper|whispers|whispered|reply|replies|replied|tell|tells|told)\b")]
    private static partial Regex Narration();

    [GeneratedRegex(@"^([•●▪]|\*\s|-\s)|^(?i:book|volume) \d+:")]
    private static partial Regex ListItem();

    [GeneratedRegex(@",\s+|\s*—\s*|\s+[–-]\s+")]
    private static partial Regex IntroSeparator();

    [GeneratedRegex(@"^(from|in|with|following|after|set|perfect|for|now|like|as|through|once)\b", RegexOptions.IgnoreCase)]
    private static partial Regex Introductory();

    [GeneratedRegex("(<[^>]+>)")]
    private static partial Regex Tags();

    [GeneratedRegex(@"^</?\s*([A-Za-z0-9]+)")]
    private static partial Regex TagName();

    [GeneratedRegex(@"(\n\s*\n|\n)")]
    private static partial Regex LineBreaks();

    [GeneratedRegex(@"([a-z][.!?…])([A-Z][a-z])")]
    private static partial Regex LostBreak();

    [GeneratedRegex(@"([A-Z]{2}[A-Z ,'’!?.]{4,}[A-Z!?.])([A-Z][a-z])")]
    private static partial Regex LostBreakAfterCapitals();

    [GeneratedRegex(@"^[A-Z][a-z]")]
    private static partial Regex StartsWord();

    [GeneratedRegex(@"(\*\*|__)(.+?)\1")]
    private static partial Regex MarkdownBold();

    [GeneratedRegex(@"(?<![\w*])\*(\S(?:.*?\S)?)\*(?![\w*])")]
    private static partial Regex MarkdownItalic();

    [GeneratedRegex(@"[A-Za-z0-9]")]
    private static partial Regex LetterOrDigit();

    [GeneratedRegex(@"[\u0080-\u009f]")]
    private static partial Regex C1Controls();

    [GeneratedRegex(@"\s+")]
    private static partial Regex Spaces();
}
