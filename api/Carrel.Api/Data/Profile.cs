namespace Carrel.Api.Data;

/// <summary>A user's public identity. Keyed on the Supabase Auth user id, so the username can change freely.</summary>
public class Profile
{
    /// <summary>Supabase Auth user id (auth.users.id).</summary>
    public Guid UserId { get; set; }

    /// <summary>Public handle, shown as typed; unique ignoring case (enforced by a lower(username) index).</summary>
    public required string Username { get; set; }

    /// <summary>Whether anyone can see the profile and library at /@username; if not, only the reader can.</summary>
    public bool IsPublic { get; set; } = true;

    /// <summary>The profile picture's path in Supabase Storage's avatars bucket; null for none.</summary>
    public string? AvatarPath { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}
