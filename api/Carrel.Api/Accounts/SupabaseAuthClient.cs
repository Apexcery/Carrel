using System.Net;

namespace Carrel.Api.Accounts;

public enum PasswordCheck
{
    Correct,
    Wrong,
    TooManyAttempts,
}

/// <summary>Supabase Auth could not be reached, or the secret key is missing or rejected.</summary>
public class AccountServiceUnavailableException(string message, Exception? inner = null) : Exception(message, inner);

/// <summary>
/// Calls Supabase Auth with the project's secret key (Supabase:SecretKey): checks a user's password and deletes users.
/// The key is read per call, so the API still starts without it and only these calls fail.
/// </summary>
public class SupabaseAuthClient(HttpClient http, IConfiguration config, ILogger<SupabaseAuthClient> logger)
{
    public async Task<PasswordCheck> CheckPasswordAsync(string email, string password, CancellationToken ct)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "token?grant_type=password")
        {
            Content = JsonContent.Create(new { email, password }),
        };
        using var response = await SendAsync(request, ct);
        return response.StatusCode switch
        {
            _ when response.IsSuccessStatusCode => PasswordCheck.Correct,
            // Supabase answers 400 (invalid_credentials) for a wrong password.
            HttpStatusCode.BadRequest => PasswordCheck.Wrong,
            HttpStatusCode.TooManyRequests => PasswordCheck.TooManyAttempts,
            _ => throw Unexpected("checking a password", response.StatusCode),
        };
    }

    /// <summary>Deletes the user; their profile and library go with them (on delete cascade). Already gone counts as done.</summary>
    public async Task DeleteUserAsync(Guid userId, CancellationToken ct)
    {
        using var request = new HttpRequestMessage(HttpMethod.Delete, $"admin/users/{userId}");
        using var response = await SendAsync(request, ct);
        if (!response.IsSuccessStatusCode && response.StatusCode != HttpStatusCode.NotFound)
        {
            throw Unexpected("deleting a user", response.StatusCode);
        }
    }

    private async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
    {
        var key = config["Supabase:SecretKey"];
        if (string.IsNullOrEmpty(key))
        {
            throw new AccountServiceUnavailableException("Supabase:SecretKey is not configured.");
        }
        request.Headers.Add("apikey", key);
        try
        {
            return await http.SendAsync(request, ct);
        }
        catch (HttpRequestException e)
        {
            throw new AccountServiceUnavailableException("Supabase Auth could not be reached.", e);
        }
    }

    private AccountServiceUnavailableException Unexpected(string action, HttpStatusCode status)
    {
        logger.LogError("Supabase Auth answered {Status} when {Action}", (int)status, action);
        return new AccountServiceUnavailableException($"Supabase Auth answered {(int)status} when {action}.");
    }
}
