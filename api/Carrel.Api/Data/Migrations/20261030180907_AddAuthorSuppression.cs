using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddAuthorSuppression : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "bio_suppressed",
                table: "authors",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "photo_suppressed",
                table: "authors",
                type: "boolean",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "bio_suppressed",
                table: "authors");

            migrationBuilder.DropColumn(
                name: "photo_suppressed",
                table: "authors");
        }
    }
}
