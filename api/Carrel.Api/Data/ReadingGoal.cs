namespace Carrel.Api.Data;

/// <summary>How many books a reader means to finish in a year. Each year has its own, so past years keep theirs.</summary>
public class ReadingGoal
{
    public const int MaxBooks = 1000;

    /// <summary>Supabase Auth user id (auth.users.id).</summary>
    public Guid UserId { get; set; }

    public int Year { get; set; }

    public int Books { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
}
