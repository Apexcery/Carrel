using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddReadingPositions : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "reading_positions",
                columns: table => new
                {
                    library_entry_id = table.Column<long>(type: "bigint", nullable: false),
                    locator = table.Column<string>(type: "text", nullable: false),
                    progression = table.Column<decimal>(type: "numeric(7,6)", precision: 7, scale: 6, nullable: false),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_reading_positions", x => x.library_entry_id);
                    table.CheckConstraint("ck_reading_positions_locator", "char_length(locator) <= 4000");
                    table.CheckConstraint("ck_reading_positions_progression", "progression between 0 and 1");
                    table.ForeignKey(
                        name: "fk_reading_positions_library_entries_library_entry_id",
                        column: x => x.library_entry_id,
                        principalTable: "library_entries",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            // RLS with no policies denies the Data API roles; the API connects as the table owner.
            migrationBuilder.Sql("alter table reading_positions enable row level security;");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "reading_positions");
        }
    }
}
