using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddLibraryImports : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "finished_date_unknown",
                table: "reads",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.CreateTable(
                name: "library_imports",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    source = table.Column<string>(type: "text", nullable: false),
                    overwrite_existing = table.Column<bool>(type: "boolean", nullable: false),
                    state = table.Column<string>(type: "text", nullable: false),
                    created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()"),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()"),
                    finished_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    lease_until = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    batch_number = table.Column<int>(type: "integer", nullable: false),
                    failures = table.Column<int>(type: "integer", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_library_imports", x => x.id);
                    table.CheckConstraint("ck_library_imports_source", "source in ('goodreads', 'story_graph')");
                    table.CheckConstraint("ck_library_imports_state", "state in ('matching', 'waiting', 'done', 'failed')");
                });

            migrationBuilder.CreateTable(
                name: "import_items",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    import_id = table.Column<long>(type: "bigint", nullable: false),
                    row = table.Column<int>(type: "integer", nullable: false),
                    title = table.Column<string>(type: "text", nullable: false),
                    authors = table.Column<string[]>(type: "text[]", nullable: false),
                    isbn13 = table.Column<string>(type: "text", nullable: true),
                    isbn10 = table.Column<string>(type: "text", nullable: true),
                    asin = table.Column<string>(type: "text", nullable: true),
                    goodreads_id = table.Column<string>(type: "text", nullable: true),
                    status = table.Column<string>(type: "text", nullable: false),
                    rating = table.Column<decimal>(type: "numeric(2,1)", precision: 2, scale: 1, nullable: true),
                    added_on = table.Column<DateOnly>(type: "date", nullable: true),
                    match = table.Column<string>(type: "text", nullable: false),
                    hardcover_book_id = table.Column<int>(type: "integer", nullable: true),
                    book_id = table.Column<long>(type: "bigint", nullable: true),
                    written = table.Column<bool>(type: "boolean", nullable: false),
                    created_entry = table.Column<bool>(type: "boolean", nullable: false),
                    confirmed = table.Column<bool>(type: "boolean", nullable: false),
                    reads = table.Column<string>(type: "jsonb", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_import_items", x => x.id);
                    table.CheckConstraint("ck_import_items_match", "match in ('pending', 'needs_search', 'exact', 'by_title', 'chosen', 'not_found', 'dismissed')");
                    table.CheckConstraint("ck_import_items_rating", "rating between 0.5 and 5 and mod(rating * 2, 1) = 0");
                    table.CheckConstraint("ck_import_items_status", "status in ('want_to_read', 'reading', 'paused', 'read', 'did_not_finish')");
                    table.ForeignKey(
                        name: "fk_import_items_books_book_id",
                        column: x => x.book_id,
                        principalTable: "books",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_import_items_library_imports_import_id",
                        column: x => x.import_id,
                        principalTable: "library_imports",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.AddCheckConstraint(
                name: "ck_reads_finished_date_unknown",
                table: "reads",
                sql: "not (finished_date_unknown and finished_on is not null)");

            migrationBuilder.CreateIndex(
                name: "ix_import_items_book_id",
                table: "import_items",
                column: "book_id");

            migrationBuilder.CreateIndex(
                name: "ix_import_items_import_id_row",
                table: "import_items",
                columns: new[] { "import_id", "row" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_library_imports_user_id_created_at",
                table: "library_imports",
                columns: new[] { "user_id", "created_at" });

            // Deleting a Supabase Auth user deletes their imports, like their library (account deletion, UK GDPR).
            migrationBuilder.Sql("""
                alter table library_imports
                    add constraint fk_library_imports_users_user_id
                    foreign key (user_id) references auth.users (id) on delete cascade;
                """);

            // RLS with no policies denies the Data API roles; the API connects as the table owner.
            migrationBuilder.Sql("alter table library_imports enable row level security;");
            migrationBuilder.Sql("alter table import_items enable row level security;");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "import_items");

            migrationBuilder.DropTable(
                name: "library_imports");

            migrationBuilder.DropCheckConstraint(
                name: "ck_reads_finished_date_unknown",
                table: "reads");

            migrationBuilder.DropColumn(
                name: "finished_date_unknown",
                table: "reads");
        }
    }
}
