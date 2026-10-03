using System.Text.Json;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;

namespace Carrel.Api.Data;

/// <summary>Stores an enum as snake_case text, e.g. ReadingStatus.WantToRead as 'want_to_read'.</summary>
public class SnakeCaseEnumConverter<TEnum>() : ValueConverter<TEnum, string>(v => ToDb(v), s => FromDb(s))
    where TEnum : struct, Enum
{
    public static string ToDb(TEnum value) => JsonNamingPolicy.SnakeCaseLower.ConvertName(value.ToString());

    public static TEnum FromDb(string value) => Enum.Parse<TEnum>(value.Replace("_", ""), ignoreCase: true);

    /// <summary>SQL check constraint restricting a column to the enum's values.</summary>
    public static string CheckSql(string column) =>
        $"{column} in ({string.Join(", ", Enum.GetValues<TEnum>().Select(v => $"'{ToDb(v)}'"))})";
}
