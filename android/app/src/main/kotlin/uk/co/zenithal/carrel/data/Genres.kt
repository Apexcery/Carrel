package uk.co.zenithal.carrel.data

/**
 * A genre's address on Carrel, made from its name ("Self-Help" is self-help), the same way the API (Genres.Slug) and
 * the website (genreSlug) make it. Empty for a name with no letters or digits in it.
 */
fun genreSlug(name: String): String =
    name.lowercase()
        .replace("&", " and ")
        .replace(Regex("['’]"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

/** A genre named on a book, linked to its page (none if its name makes no address). */
fun genreLink(name: String) = GenreLink(name, genreSlug(name))
