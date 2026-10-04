using System.Security.Claims;

namespace Carrel.Api.Accounts;

public record DeleteAccountRequest(string Password);

/// <summary>The signed-in user's account itself. Email and password changes go straight to Supabase from the browser.</summary>
public static class AccountEndpoints
{
    /// <summary>Rate limit policy for password checks, much tighter than the general limit.</summary>
    public const string AccountRateLimit = "account";

    public static void MapAccountEndpoints(this IEndpointRouteBuilder app)
    {
        // POST rather than DELETE: it carries the password, and DELETE bodies aren't reliably passed along.
        app.MapPost("/account/delete", async (DeleteAccountRequest request, ClaimsPrincipal user, SupabaseAuthClient auth,
            ILogger<SupabaseAuthClient> logger, CancellationToken ct) =>
        {
            var userId = Guid.Parse(user.FindFirstValue("sub")!);
            var email = user.FindFirstValue("email");
            if (string.IsNullOrEmpty(email) || string.IsNullOrEmpty(request.Password))
            {
                return Results.ValidationProblem(new Dictionary<string, string[]> { ["password"] = ["Enter your password."] });
            }

            try
            {
                // Confirm it's really the account holder, not just someone at a signed-in browser.
                switch (await auth.CheckPasswordAsync(email, request.Password, ct))
                {
                    case PasswordCheck.Wrong:
                        return Results.ValidationProblem(new Dictionary<string, string[]> { ["password"] = ["That password isn’t right."] });
                    case PasswordCheck.TooManyAttempts:
                        return Results.StatusCode(StatusCodes.Status429TooManyRequests);
                }

                await auth.DeleteUserAsync(userId, ct);
                return Results.NoContent();
            }
            catch (AccountServiceUnavailableException e)
            {
                logger.LogError(e, "Account deletion is unavailable");
                return Results.Problem(statusCode: StatusCodes.Status503ServiceUnavailable,
                    detail: "Account deletion is unavailable right now. Try again later.");
            }
        }).RequireRateLimiting(AccountRateLimit);
    }
}
