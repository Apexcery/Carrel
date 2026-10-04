using System.Security.Claims;
using System.Text.RegularExpressions;
using Carrel.Api.Data;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace Carrel.Api.Profiles;

public record ProfileDto(string? Username);

public record SaveProfileRequest(string Username);

public record UsernameAvailability(bool Available, string? Reason);

/// <summary>The signed-in user's public profile. Usernames are public handles; they can be changed.</summary>
public static partial class ProfileEndpoints
{
    // Names that would be confusing or could impersonate the site.
    private static readonly HashSet<string> Reserved = new(StringComparer.OrdinalIgnoreCase)
    {
        "admin", "administrator", "carrel", "support", "help", "settings", "account", "profile", "profiles",
        "library", "search", "books", "series", "shelves", "api", "root", "system", "moderator", "staff",
        "official", "null", "undefined", "me", "you", "user", "users", "about", "privacy", "terms",
    };

    public static void MapProfileEndpoints(this IEndpointRouteBuilder app)
    {
        var profile = app.MapGroup("/profile");

        // 200 with a null username (rather than 404) when none is chosen yet: it's an expected state.
        profile.MapGet("/", async (ClaimsPrincipal user, CarrelDbContext db, CancellationToken ct) =>
        {
            var userId = UserId(user);
            var username = await db.Profiles.Where(p => p.UserId == userId).Select(p => p.Username).FirstOrDefaultAsync(ct);
            return new ProfileDto(username);
        });

        profile.MapGet("/username-available", async (string username, ClaimsPrincipal user, CarrelDbContext db, CancellationToken ct) =>
            await CheckAsync(username.Trim(), UserId(user), db, ct));

        profile.MapPut("/", async (SaveProfileRequest request, ClaimsPrincipal user, CarrelDbContext db, CancellationToken ct) =>
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
                db.Profiles.Add(new Profile { UserId = userId, Username = username, CreatedAt = now, UpdatedAt = now });
            }
            else
            {
                existing.Username = username;
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
            return Results.Ok(new ProfileDto(username));
        });
    }

    private static async Task<UsernameAvailability> CheckAsync(string username, Guid userId, CarrelDbContext db, CancellationToken ct)
    {
        if (!ValidUsername().IsMatch(username))
        {
            return new(false, "Use 3 to 20 letters, numbers, underscores or hyphens.");
        }
        if (Reserved.Contains(username))
        {
            return new(false, "That username isn't available.");
        }
        var lower = username.ToLowerInvariant();
        var taken = await db.Profiles.AnyAsync(p => p.UserId != userId && p.Username.ToLower() == lower, ct);
        return taken ? new(false, "That username is taken.") : new(true, null);
    }

    /// <summary>The Supabase user id from the validated token.</summary>
    private static Guid UserId(ClaimsPrincipal user) => Guid.Parse(user.FindFirstValue("sub")!);

    [GeneratedRegex("^[A-Za-z0-9_-]{3,20}$")]
    private static partial Regex ValidUsername();
}
