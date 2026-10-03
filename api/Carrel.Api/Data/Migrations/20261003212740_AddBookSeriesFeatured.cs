using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddBookSeriesFeatured : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "is_featured",
                table: "book_series",
                type: "boolean",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "is_featured",
                table: "book_series");
        }
    }
}
