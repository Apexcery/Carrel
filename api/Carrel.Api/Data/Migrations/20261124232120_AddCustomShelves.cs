using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddCustomShelves : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "shelve_only",
                table: "import_items",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<string[]>(
                name: "shelves",
                table: "import_items",
                type: "text[]",
                nullable: false,
                defaultValueSql: "'{}'");

            migrationBuilder.CreateTable(
                name: "custom_shelves",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    name = table.Column<string>(type: "text", nullable: false),
                    is_public = table.Column<bool>(type: "boolean", nullable: false),
                    created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()"),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_custom_shelves", x => x.id);
                    table.CheckConstraint("ck_custom_shelves_name", "char_length(name) between 1 and 50");
                });

            migrationBuilder.CreateTable(
                name: "custom_shelf_entries",
                columns: table => new
                {
                    shelf_id = table.Column<long>(type: "bigint", nullable: false),
                    book_id = table.Column<long>(type: "bigint", nullable: false),
                    added_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_custom_shelf_entries", x => new { x.shelf_id, x.book_id });
                    table.ForeignKey(
                        name: "fk_custom_shelf_entries_books_book_id",
                        column: x => x.book_id,
                        principalTable: "books",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_custom_shelf_entries_custom_shelves_shelf_id",
                        column: x => x.shelf_id,
                        principalTable: "custom_shelves",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "ix_custom_shelf_entries_book_id",
                table: "custom_shelf_entries",
                column: "book_id");

            // Deleting a Supabase Auth user deletes their shelves (account deletion, UK GDPR).
            migrationBuilder.Sql("""
                alter table custom_shelves
                    add constraint fk_custom_shelves_users_user_id
                    foreign key (user_id) references auth.users (id) on delete cascade;
                """);

            // Names are shown as typed but unique per reader ignoring case; the index also finds a reader's shelves.
            migrationBuilder.Sql("create unique index ix_custom_shelves_user_id_name_lower on custom_shelves (user_id, lower(name));");

            // RLS with no policies denies the Data API roles; the API connects as the table owner.
            migrationBuilder.Sql("alter table custom_shelves enable row level security;");
            migrationBuilder.Sql("alter table custom_shelf_entries enable row level security;");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "custom_shelf_entries");

            migrationBuilder.DropTable(
                name: "custom_shelves");

            migrationBuilder.DropColumn(
                name: "shelve_only",
                table: "import_items");

            migrationBuilder.DropColumn(
                name: "shelves",
                table: "import_items");
        }
    }
}
