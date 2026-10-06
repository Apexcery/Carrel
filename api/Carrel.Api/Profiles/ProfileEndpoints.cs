using System.Security.Claims;
using System.Text.RegularExpressions;
using Carrel.Api.Accounts;
using Carrel.Api.Data;
using Carrel.Api.Library;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace Carrel.Api.Profiles;

public record ReadingGoalDto(int Year, int Books);

/// <summary>Goals are every year's the reader has set, oldest first. AvatarUrl is null without a picture.</summary>
public record ProfileDto(string? Username, bool IsPublic, string? AvatarUrl, ReadingGoalDto[] Goals);

/// <summary>Leaving out IsPublic keeps the current setting (public, for a new profile).</summary>
public record SaveProfileRequest(string Username, bool? IsPublic = null);

public record SaveGoalRequest(int Books);

/// <summary>A reader's profile page: their username, picture, reading goals, and whole library.</summary>
public record ReaderDto(string Username, bool IsPublic, string? AvatarUrl, ReadingGoalDto[] Goals, LibraryItemDto[] Library);

public record UsernameAvailability(bool Available, string? Reason);

/// <summary>
/// The signed-in user's public profile, and every reader's profile page. Usernames are public handles; they can be
/// changed.
/// </summary>
public static partial class ProfileEndpoints
{
    public const string ReadersRateLimit = "readers";

    // Names that would be confusing or could impersonate the site.
    private static readonly HashSet<string> Reserved = new(StringComparer.OrdinalIgnoreCase)
    {
        "admin", "administrator", "carrel", "support", "help", "settings", "account", "profile", "profiles",
        "library", "search", "books", "series", "shelves", "api", "root", "system", "moderator", "staff",
        "official", "null", "undefined", "me", "you", "user", "users", "about", "privacy", "terms",
        "copyright", "legal", "dmca",
    };

    public static void MapProfileEndpoints(this IEndpointRouteBuilder app)
    {
        var profile = app.MapGroup("/profile");

        // 200 with a null username (rather than 404) when none is chosen yet: it's an expected state.
        profile.MapGet("/", async (ClaimsPrincipal user, CarrelDbContext db, ProfilePictures pictures, CancellationToken ct) =>
        {
            var userId = UserId(user);
            var profile = await db.Profiles.AsNoTracking().FirstOrDefaultAsync(p => p.UserId == userId, ct);
            return new ProfileDto(profile?.Username, profile?.IsPublic ?? true, pictures.PublicUrl(profile?.AvatarPath),
                await GoalsAsync(userId, db, ct));
        });

        profile.MapGet("/username-available", async (string username, ClaimsPrincipal user, CarrelDbContext db, CancellationToken ct) =>
            await CheckAsync(username.Trim(), UserId(user), db, ct));

        profile.MapPut("/", async (SaveProfileRequest request, ClaimsPrincipal user, CarrelDbContext db, ProfilePictures pictures,
            ILogger<ProfilePictures> logger, CancellationToken ct) =>
        {
            var userId = UserId(user);
            var username = request.Username.Trim();
            var check = await CheckAsync(username, userId, db, ct);
            if (!check.Available)
            {
                return Results.ValidationProblem(new Dictionary<string, string[]> { ["username"] = [check.Reason!] });
            }

            var now = DateTimeOffset.UtcNow;
            var existing = await db.Profiles.FirstOrDefaultAsync(p => p.UserId == userId, ct);
            if (existing is null)
            {
                existing = new Profile { UserId = userId, Username = username, IsPublic = request.IsPublic ?? true, CreatedAt = now, UpdatedAt = now };
                db.Profiles.Add(existing);
            }
            else
            {
                existing.Username = username;
                // Going private gives the picture a new address, so links to it that people already have stop working.
                // If storage can't be reached, the profile still goes private and the picture keeps its address.
                if (existing.IsPublic && request.IsPublic == false && existing.AvatarPath is { } picture)
                {
                    try
                    {
                        existing.AvatarPath = await pictures.RenameAsync(userId, picture, ct);
                    }
                    catch (PictureStorageUnavailableException e)
                    {
                        logger.LogError(e, "Couldn't rename the profile picture {Path} when its profile went private", picture);
                    }
                }
                existing.IsPublic = request.IsPublic ?? existing.IsPublic;
                existing.UpdatedAt = now;
            }

            try
            {
                await db.SaveChangesAsync(ct);
            }
            catch (DbUpdateException e) when (e.InnerException is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation })
            {
                // Someone else took it between the check and the save.
                return Results.ValidationProblem(new Dictionary<string, string[]> { ["username"] = ["That username is taken."] });
            }
            return Results.Ok(new ProfileDto(username, existing.IsPublic, pictures.PublicUrl(existing.AvatarPath),
                await GoalsAsync(userId, db, ct)));
        });

        // The picture is the request body: the website sends the square the reader cropped. It replaces any picture
        // they had. A username comes first, since the picture belongs to the profile.
        profile.MapPost("/picture", async (HttpRequest request, ClaimsPrincipal user, CarrelDbContext db, ProfilePictures pictures,
                ILogger<ProfilePictures> logger, CancellationToken ct) =>
            {
                if (request.ContentLength is not > 0 || request.ContentLength > ProfilePictures.MaxUploadBytes)
                {
                    return PictureProblem($"Choose a picture of up to {ProfilePictures.MaxUploadBytes / 1024 / 1024} MB.");
                }
                var userId = UserId(user);
                var existing = await db.Profiles.FirstOrDefaultAsync(p => p.UserId == userId, ct);
                if (existing is null)
                {
                    return Results.NotFound();
                }
                using var upload = new MemoryStream();
                await request.Body.CopyToAsync(upload, ct);
                try
                {
                    var old = existing.AvatarPath;
                    existing.AvatarPath = await pictures.SaveAsync(userId, upload.ToArray(), ct);
                    existing.UpdatedAt = DateTimeOffset.UtcNow;
                    await db.SaveChangesAsync(ct);
                    if (old is not null)
                    {
                        await DeleteOldPictureAsync(old, pictures, logger, ct);
                    }
                    return Results.Ok(new ProfileDto(existing.Username, existing.IsPublic, pictures.PublicUrl(existing.AvatarPath),
                        await GoalsAsync(userId, db, ct)));
                }
                catch (ProfilePictureException e)
                {
                    return PictureProblem(e.Message);
                }
                catch (PictureStorageUnavailableException e)
                {
                    logger.LogError(e, "Saving a profile picture is unavailable");
                    return PictureUnavailable();
                }
            })
            .RequireRateLimiting(AccountEndpoints.AccountRateLimit);

        // The file goes first, so a picture the reader removed can't linger in storage.
        profile.MapDelete("/picture", async (ClaimsPrincipal user, CarrelDbContext db, ProfilePictures pictures,
            ILogger<ProfilePictures> logger, CancellationToken ct) =>
        {
            var userId = UserId(user);
            var existing = await db.Profiles.FirstOrDefaultAsync(p => p.UserId == userId, ct);
            if (existing is null)
            {
                return Results.NotFound();
            }
            if (existing.AvatarPath is { } path)
            {
                try
                {
                    await pictures.DeleteAsync(path, ct);
                }
                catch (PictureStorageUnavailableException e)
                {
                    logger.LogError(e, "Removing a profile picture is unavailable");
                    return PictureUnavailable();
                }
                existing.AvatarPath = null;
                existing.UpdatedAt = DateTimeOffset.UtcNow;
                await db.SaveChangesAsync(ct);
            }
            return Results.Ok(new ProfileDto(existing.Username, existing.IsPublic, null, await GoalsAsync(userId, db, ct)));
        });

        // Sets a year's goal, replacing any it had. Returns every goal, for the profile.
        profile.MapPut("/goals/{year:int}", async (int year, SaveGoalRequest request, ClaimsPrincipal user, CarrelDbContext db,
            CancellationToken ct) =>
        {
            if (request.Books is < 1 or > ReadingGoal.MaxBooks)
            {
                return Results.ValidationProblem(new Dictionary<string, string[]>
                    { ["books"] = [$"Choose a goal from 1 to {ReadingGoal.MaxBooks:N0} books."] });
            }
            // This year, give or take one for readers whose new year comes before or after UTC's.
            if (Math.Abs(year - DateTime.UtcNow.Year) > 1)
            {
                return Results.ValidationProblem(new Dictionary<string, string[]> { ["year"] = ["You can only set this year's goal."] });
            }
            var userId = UserId(user);
            var now = DateTimeOffset.UtcNow;
            var goal = await db.ReadingGoals.FirstOrDefaultAsync(g => g.UserId == userId && g.Year == year, ct);
            if (goal is null)
            {
                db.ReadingGoals.Add(new ReadingGoal { UserId = userId, Year = year, Books = request.Books, CreatedAt = now, UpdatedAt = now });
            }
            else
            {
                goal.Books = request.Books;
                goal.UpdatedAt = now;
            }
            await db.SaveChangesAsync(ct);
            return Results.Ok(await GoalsAsync(userId, db, ct));
        });

        profile.MapDelete("/goals/{year:int}", async (int year, ClaimsPrincipal user, CarrelDbContext db, CancellationToken ct) =>
        {
            var userId = UserId(user);
            await db.ReadingGoals.Where(g => g.UserId == userId && g.Year == year).ExecuteDeleteAsync(ct);
            return Results.Ok(await GoalsAsync(userId, db, ct));
        });

        // Anyone can open a public profile, signed in or not; a private one only its reader. Not found otherwise, as if
        // there were no such reader.
        app.MapGet("/readers/{username}", async (string username, ClaimsPrincipal user, CarrelDbContext db, LibraryService library,
                ProfilePictures pictures, CancellationToken ct) =>
            {
                var lower = username.ToLowerInvariant();
                var profile = await db.Profiles.AsNoTracking().FirstOrDefaultAsync(p => p.Username.ToLower() == lower, ct);
                var isOwn = user.FindFirstValue("sub") is { } sub && Guid.Parse(sub) == profile?.UserId;
                if (profile is null || (!profile.IsPublic && !isOwn))
                {
                    return Results.NotFound();
                }
                return Results.Ok(new ReaderDto(profile.Username, profile.IsPublic, pictures.PublicUrl(profile.AvatarPath),
                    await GoalsAsync(profile.UserId, db, ct), await library.ListAsync(profile.UserId, ct)));
            })
            .AllowAnonymous()
            .RequireRateLimiting(ReadersRateLimit);
    }

    private static async Task<UsernameAvailability> CheckAsync(string username, Guid userId, CarrelDbContext db, CancellationToken ct)
    {
        if (!ValidUsername().IsMatch(username))
        {
            return new(false, "Use 3 to 20 letters, numbers, underscores, or hyphens.");
        }
        if (Reserved.Contains(username))
        {
            return new(false, "That username isn't available.");
        }
        var lower = username.ToLowerInvariant();
        var taken = await db.Profiles.AnyAsync(p => p.UserId != userId && p.Username.ToLower() == lower, ct);
        return taken ? new(false, "That username is taken.") : new(true, null);
    }

    private static IResult PictureProblem(string message) =>
        Results.ValidationProblem(new Dictionary<string, string[]> { ["picture"] = [message] });

    private static IResult PictureUnavailable() =>
        Results.Problem(statusCode: StatusCodes.Status503ServiceUnavailable,
            detail: "Profile pictures are unavailable right now. Try again later.");

    /// <summary>The replaced picture; if it can't be deleted now, the new one still stands and the old file is logged.</summary>
    private static async Task DeleteOldPictureAsync(string path, ProfilePictures pictures, ILogger logger, CancellationToken ct)
    {
        try
        {
            await pictures.DeleteAsync(path, ct);
        }
        catch (PictureStorageUnavailableException e)
        {
            logger.LogError(e, "Couldn't delete the replaced profile picture {Path}", path);
        }
    }

    private static Task<ReadingGoalDto[]> GoalsAsync(Guid userId, CarrelDbContext db, CancellationToken ct) =>
        db.ReadingGoals.Where(g => g.UserId == userId).OrderBy(g => g.Year).Select(g => new ReadingGoalDto(g.Year, g.Books)).ToArrayAsync(ct);

    /// <summary>The Supabase user id from the validated token.</summary>
    private static Guid UserId(ClaimsPrincipal user) => Guid.Parse(user.FindFirstValue("sub")!);

    [GeneratedRegex("^[A-Za-z0-9_-]{3,20}$")]
    private static partial Regex ValidUsername();
}
