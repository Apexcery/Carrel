using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddContentSuppression : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "cover_suppressed",
                table: "books",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "description_suppressed",
                table: "books",
                type: "boolean",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "cover_suppressed",
                table: "books");

            migrationBuilder.DropColumn(
                name: "description_suppressed",
                table: "books");
        }
    }
}
