using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddCarrelImports : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropCheckConstraint(
                name: "ck_library_imports_source",
                table: "library_imports");

            migrationBuilder.AddColumn<DateTimeOffset>(
                name: "added_at",
                table: "import_items",
                type: "timestamp with time zone",
                nullable: true);

            migrationBuilder.AddColumn<long>(
                name: "hardcover_edition_id",
                table: "import_items",
                type: "bigint",
                nullable: true);

            migrationBuilder.AddColumn<decimal>(
                name: "progress_percent",
                table: "import_items",
                type: "numeric(5,2)",
                precision: 5,
                scale: 2,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "progress_unit",
                table: "import_items",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<decimal>(
                name: "progress_value",
                table: "import_items",
                type: "numeric",
                nullable: true);

            migrationBuilder.AddCheckConstraint(
                name: "ck_library_imports_source",
                table: "library_imports",
                sql: "source in ('goodreads', 'story_graph', 'carrel')");

            migrationBuilder.AddCheckConstraint(
                name: "ck_import_items_progress_unit",
                table: "import_items",
                sql: "progress_unit in ('page', 'percent', 'seconds')");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropCheckConstraint(
                name: "ck_library_imports_source",
                table: "library_imports");

            migrationBuilder.DropCheckConstraint(
                name: "ck_import_items_progress_unit",
                table: "import_items");

            migrationBuilder.DropColumn(
                name: "added_at",
                table: "import_items");

            migrationBuilder.DropColumn(
                name: "hardcover_edition_id",
                table: "import_items");

            migrationBuilder.DropColumn(
                name: "progress_percent",
                table: "import_items");

            migrationBuilder.DropColumn(
                name: "progress_unit",
                table: "import_items");

            migrationBuilder.DropColumn(
                name: "progress_value",
                table: "import_items");

            migrationBuilder.AddCheckConstraint(
                name: "ck_library_imports_source",
                table: "library_imports",
                sql: "source in ('goodreads', 'story_graph')");
        }
    }
}
