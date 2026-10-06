using System.Security.Claims;
using Carrel.Api.Library;

namespace Carrel.Api.Accounts;

public record PasswordConfirmation(string Password);

/// <summary>The signed-in user's account itself. Email and password changes go straight to Supabase from the browser.</summary>
public static class AccountEndpoints
{
    /// <summary>Rate limit policy for password checks, much tighter than the general limit.</summary>
    public const string AccountRateLimit = "account";

    public static void MapAccountEndpoints(this IEndpointRouteBuilder app)
    {
        // POST rather than DELETE: these carry the password, and DELETE bodies aren't reliably passed along.
        app.MapPost("/account/delete", (PasswordConfirmation request, ClaimsPrincipal user, SupabaseAuthClient auth,
                ILogger<SupabaseAuthClient> logger, CancellationToken ct) =>
            AfterPasswordCheckAsync(request, user, auth, logger, "Account deletion", async userId =>
            {
                await auth.DeleteUserAsync(userId, ct);
                return Results.NoContent();
            }, ct)).RequireRateLimiting(AccountRateLimit);

        // Empties the library but keeps the account.
        app.MapPost("/account/delete-library", (PasswordConfirmation request, ClaimsPrincipal user, SupabaseAuthClient auth,
                LibraryService library, ILogger<SupabaseAuthClient> logger, CancellationToken ct) =>
            AfterPasswordCheckAsync(request, user, auth, logger, "Deleting book data", async userId =>
                await library.DeleteAllAsync(userId, ct)
                    ? Results.NoContent()
                    : Results.ValidationProblem(new Dictionary<string, string[]>
                    {
                        ["import"] = ["An import is still running. Wait for it to finish, then try again."],
                    }), ct)).RequireRateLimiting(AccountRateLimit);
    }

    /// <summary>
    /// Runs the action only once the password is confirmed, so it's really the account holder, not just someone at a
    /// signed-in browser.
    /// </summary>
    private static async Task<IResult> AfterPasswordCheckAsync(PasswordConfirmation request, ClaimsPrincipal user,
        SupabaseAuthClient auth, ILogger logger, string action, Func<Guid, Task<IResult>> then, CancellationToken ct)
    {
        var userId = Guid.Parse(user.FindFirstValue("sub")!);
        var email = user.FindFirstValue("email");
        if (string.IsNullOrEmpty(email) || string.IsNullOrEmpty(request.Password))
        {
            return Results.ValidationProblem(new Dictionary<string, string[]> { ["password"] = ["Enter your password."] });
        }

        try
        {
            switch (await auth.CheckPasswordAsync(email, request.Password, ct))
            {
                case PasswordCheck.Wrong:
                    return Results.ValidationProblem(new Dictionary<string, string[]> { ["password"] = ["That password isn’t right."] });
                case PasswordCheck.TooManyAttempts:
                    return Results.StatusCode(StatusCodes.Status429TooManyRequests);
            }

            return await then(userId);
        }
        catch (AccountServiceUnavailableException e)
        {
            logger.LogError(e, "{Action} is unavailable", action);
            return Results.Problem(statusCode: StatusCodes.Status503ServiceUnavailable,
                detail: $"{action} is unavailable right now. Try again later.");
        }
    }
}
