using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddPausedStatus : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropCheckConstraint(
                name: "ck_library_entries_status",
                table: "library_entries");

            migrationBuilder.AddCheckConstraint(
                name: "ck_library_entries_status",
                table: "library_entries",
                sql: "status in ('want_to_read', 'reading', 'paused', 'read', 'did_not_finish')");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropCheckConstraint(
                name: "ck_library_entries_status",
                table: "library_entries");

            migrationBuilder.AddCheckConstraint(
                name: "ck_library_entries_status",
                table: "library_entries",
                sql: "status in ('want_to_read', 'reading', 'read', 'did_not_finish')");
        }
    }
}
