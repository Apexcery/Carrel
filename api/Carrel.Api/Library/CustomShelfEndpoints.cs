using System.Security.Claims;
using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace Carrel.Api.Library;

/// <summary>A custom shelf and the books on it, most recently shelved first; they needn't be in the library.</summary>
public record CustomShelfDto(long Id, string Name, bool IsPublic, ShelvedBookDto[] Books);

/// <summary>A book on a custom shelf, and when it was put there.</summary>
public record ShelvedBookDto(LibraryBookDto Book, DateTimeOffset AddedAt);

/// <summary>Leaving out IsPublic keeps the current setting (private, for a new shelf).</summary>
public record SaveShelfRequest(string? Name, bool? IsPublic = null);

/// <summary>
/// The signed-in reader's custom shelves. Every query is scoped to their id, and another reader's shelf, or a book that
/// doesn't exist, is not found, as if there were no such thing.
/// </summary>
public static class CustomShelfEndpoints
{
    public static void MapCustomShelfEndpoints(this IEndpointRouteBuilder app)
    {
        var shelves = app.MapGroup("/shelves");

        shelves.MapGet("/", (ClaimsPrincipal user, CarrelDbContext db, CancellationToken ct) =>
        {
            var userId = UserId(user);
            return DtosAsync(db.CustomShelves.Where(s => s.UserId == userId), ct);
        });

        shelves.MapPost("/", async (SaveShelfRequest request, ClaimsPrincipal user, CarrelDbContext db, CancellationToken ct) =>
        {
            var name = request.Name?.Trim() ?? "";
            if (NameProblem(name) is { } problem)
            {
                return problem;
            }
            var now = DateTimeOffset.UtcNow;
            var shelf = new CustomShelf { UserId = UserId(user), Name = name, IsPublic = request.IsPublic ?? false, CreatedAt = now, UpdatedAt = now };
            db.CustomShelves.Add(shelf);
            return await SavedAsync(shelf, db, ct);
        });

        // Renames the shelf, or makes it public or private.
        shelves.MapPut("/{id:long}", async (long id, SaveShelfRequest request, ClaimsPrincipal user, CarrelDbContext db, CancellationToken ct) =>
        {
            var userId = UserId(user);
            var shelf = await db.CustomShelves.FirstOrDefaultAsync(s => s.Id == id && s.UserId == userId, ct);
            if (shelf is null)
            {
                return Results.NotFound();
            }
            var name = request.Name?.Trim() ?? "";
            if (NameProblem(name) is { } problem)
            {
                return problem;
            }
            shelf.Name = name;
            shelf.IsPublic = request.IsPublic ?? shelf.IsPublic;
            shelf.UpdatedAt = DateTimeOffset.UtcNow;
            return await SavedAsync(shelf, db, ct);
        });

        // The books stay in the library; only the shelf goes.
        shelves.MapDelete("/{id:long}", async (long id, ClaimsPrincipal user, CarrelDbContext db, CancellationToken ct) =>
        {
            var userId = UserId(user);
            return await db.CustomShelves.Where(s => s.Id == id && s.UserId == userId).ExecuteDeleteAsync(ct) > 0
                ? Results.NoContent()
                : Results.NotFound();
        });

        // Puts a book on the shelf (again is fine), in the library or not, and returns the shelf.
        shelves.MapPut("/{id:long}/books/{bookId:long}", async (long id, long bookId, ClaimsPrincipal user, CarrelDbContext db,
            CancellationToken ct) =>
        {
            var userId = UserId(user);
            if (!await db.CustomShelves.AnyAsync(s => s.Id == id && s.UserId == userId, ct) || !await db.Books.AnyAsync(b => b.Id == bookId, ct))
            {
                return Results.NotFound();
            }
            if (!await db.CustomShelfEntries.AnyAsync(e => e.ShelfId == id && e.BookId == bookId, ct))
            {
                db.CustomShelfEntries.Add(new CustomShelfEntry { ShelfId = id, BookId = bookId, AddedAt = DateTimeOffset.UtcNow });
                try
                {
                    await db.SaveChangesAsync(ct);
                }
                catch (DbUpdateException e) when (e.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation })
                {
                    // Another request put it there between the check and the save.
                }
            }
            return Results.Ok((await DtosAsync(db.CustomShelves.Where(s => s.Id == id), ct)).Single());
        });

        // Takes a book off the shelf (if it was on it), and returns the shelf.
        shelves.MapDelete("/{id:long}/books/{bookId:long}", async (long id, long bookId, ClaimsPrincipal user, CarrelDbContext db,
            CancellationToken ct) =>
        {
            var userId = UserId(user);
            if (!await db.CustomShelves.AnyAsync(s => s.Id == id && s.UserId == userId, ct))
            {
                return Results.NotFound();
            }
            await db.CustomShelfEntries.Where(e => e.ShelfId == id && e.BookId == bookId).ExecuteDeleteAsync(ct);
            return Results.Ok((await DtosAsync(db.CustomShelves.Where(s => s.Id == id), ct)).Single());
        });
    }

    /// <summary>A reader's public shelves, for their profile page; private ones never leave the API this way.</summary>
    public static Task<CustomShelfDto[]> PublicAsync(Guid userId, CarrelDbContext db, CancellationToken ct) =>
        DtosAsync(db.CustomShelves.Where(s => s.UserId == userId && s.IsPublic), ct);

    /// <summary>Shelves A–Z, each with enough of its books to show them.</summary>
    private static async Task<CustomShelfDto[]> DtosAsync(IQueryable<CustomShelf> shelves, CancellationToken ct)
    {
        var loaded = await shelves
            .Include(s => s.Entries).ThenInclude(e => e.Book).ThenInclude(b => b.Authors).ThenInclude(ba => ba.Author)
            .Include(s => s.Entries).ThenInclude(e => e.Book).ThenInclude(b => b.Series).ThenInclude(bs => bs.Series)
            .Include(s => s.Entries).ThenInclude(e => e.Book).ThenInclude(b => b.Editions)
            .AsSplitQuery()
            .AsNoTracking()
            .OrderBy(s => s.Name.ToLower())
            .ThenBy(s => s.Id)
            .ToListAsync(ct);
        return loaded
            .Select(s => new CustomShelfDto(s.Id, s.Name, s.IsPublic, s.Entries
                .OrderByDescending(e => e.AddedAt)
                .Select(e => new ShelvedBookDto(LibraryService.ToBook(e.Book, null), e.AddedAt))
                .ToArray()))
            .ToArray();
    }

    private static IResult? NameProblem(string name) =>
        name.Length == 0 ? NameProblemResult("Give the shelf a name.")
        : name.Length > CustomShelf.MaxNameLength ? NameProblemResult($"Use up to {CustomShelf.MaxNameLength} characters.")
        : null;

    private static async Task<IResult> SavedAsync(CustomShelf shelf, CarrelDbContext db, CancellationToken ct)
    {
        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch (DbUpdateException e) when (e.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation })
        {
            return NameProblemResult("You already have a shelf called that.");
        }
        return Results.Ok((await DtosAsync(db.CustomShelves.Where(s => s.Id == shelf.Id), ct)).Single());
    }

    private static IResult NameProblemResult(string message) =>
        Results.ValidationProblem(new Dictionary<string, string[]> { ["name"] = [message] });

    /// <summary>The Supabase user id from the validated token.</summary>
    private static Guid UserId(ClaimsPrincipal user) => Guid.Parse(user.FindFirstValue("sub")!);
}
