namespace Carrel.Api.Data;

/// <summary>
/// A shelf the reader names themselves, such as "Favourites" or "Book club". A book can be on any number of them,
/// whether or not it's in the library, alongside its reading status. Only public ones show on the reader's profile.
/// </summary>
public class CustomShelf
{
    public const int MaxNameLength = 50;

    public long Id { get; set; }

    /// <summary>Supabase Auth user id (auth.users.id).</summary>
    public Guid UserId { get; set; }

    /// <summary>Shown as typed; unique per reader ignoring case (enforced by a lower(name) index).</summary>
    public required string Name { get; set; }

    /// <summary>Whether it shows on the reader's profile; new shelves, and imported ones, are private.</summary>
    public bool IsPublic { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }

    public List<CustomShelfEntry> Entries { get; } = [];
}

/// <summary>
/// A book on a custom shelf. It's the book rather than a library entry, so a book can be shelved without being in the
/// library, and stays on its shelves when it leaves the library.
/// </summary>
public class CustomShelfEntry
{
    public long ShelfId { get; set; }
    public CustomShelf Shelf { get; set; } = null!;

    public long BookId { get; set; }
    public Book Book { get; set; } = null!;

    /// <summary>When the book was put on the shelf, which a custom shelf's "Date added" sorts by.</summary>
    public DateTimeOffset AddedAt { get; set; }
}
