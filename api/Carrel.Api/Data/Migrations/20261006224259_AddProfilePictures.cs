using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddProfilePictures : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "avatar_path",
                table: "profiles",
                type: "text",
                nullable: true);

            // The pictures themselves, in Supabase Storage: public to read (profiles show them to anyone), written only
            // by the API with the secret key, and only the small WebP files it makes.
            migrationBuilder.Sql("""
                insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
                values ('avatars', 'avatars', true, 1048576, array['image/webp'])
                on conflict (id) do nothing;
                """);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "avatar_path",
                table: "profiles");
        }
    }
}
