using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class InitialSchema : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "authors",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    name = table.Column<string>(type: "text", nullable: false),
                    hardcover_id = table.Column<long>(type: "bigint", nullable: true),
                    open_library_author_key = table.Column<string>(type: "text", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_authors", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "books",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    title = table.Column<string>(type: "text", nullable: false),
                    subtitle = table.Column<string>(type: "text", nullable: true),
                    description = table.Column<string>(type: "text", nullable: true),
                    description_source = table.Column<string>(type: "text", nullable: true),
                    cover_url = table.Column<string>(type: "text", nullable: true),
                    first_published_year = table.Column<int>(type: "integer", nullable: true),
                    hardcover_id = table.Column<long>(type: "bigint", nullable: true),
                    open_library_work_key = table.Column<string>(type: "text", nullable: true),
                    hardcover_rating = table.Column<decimal>(type: "numeric(3,2)", precision: 3, scale: 2, nullable: true),
                    hardcover_ratings_count = table.Column<int>(type: "integer", nullable: true),
                    fetched_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_books", x => x.id);
                    table.CheckConstraint("ck_books_description_source", "description_source in ('hardcover', 'open_library', 'google_books')");
                });

            migrationBuilder.CreateTable(
                name: "genres",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    name = table.Column<string>(type: "text", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_genres", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "series",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    name = table.Column<string>(type: "text", nullable: false),
                    hardcover_id = table.Column<long>(type: "bigint", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_series", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "book_authors",
                columns: table => new
                {
                    book_id = table.Column<long>(type: "bigint", nullable: false),
                    author_id = table.Column<long>(type: "bigint", nullable: false),
                    role = table.Column<string>(type: "text", nullable: false),
                    position = table.Column<int>(type: "integer", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_book_authors", x => new { x.book_id, x.author_id, x.role });
                    table.ForeignKey(
                        name: "fk_book_authors_authors_author_id",
                        column: x => x.author_id,
                        principalTable: "authors",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "fk_book_authors_books_book_id",
                        column: x => x.book_id,
                        principalTable: "books",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "editions",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    book_id = table.Column<long>(type: "bigint", nullable: false),
                    isbn13 = table.Column<string>(type: "text", nullable: true),
                    isbn10 = table.Column<string>(type: "text", nullable: true),
                    format = table.Column<string>(type: "text", nullable: true),
                    page_count = table.Column<int>(type: "integer", nullable: true),
                    audio_seconds = table.Column<int>(type: "integer", nullable: true),
                    publisher = table.Column<string>(type: "text", nullable: true),
                    release_date = table.Column<DateOnly>(type: "date", nullable: true),
                    language = table.Column<string>(type: "text", nullable: true),
                    cover_url = table.Column<string>(type: "text", nullable: true),
                    hardcover_edition_id = table.Column<long>(type: "bigint", nullable: true),
                    open_library_edition_key = table.Column<string>(type: "text", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_editions", x => x.id);
                    table.CheckConstraint("ck_editions_audio_seconds", "audio_seconds > 0");
                    table.CheckConstraint("ck_editions_format", "format in ('print', 'ebook', 'audio')");
                    table.CheckConstraint("ck_editions_page_count", "page_count > 0");
                    table.ForeignKey(
                        name: "fk_editions_books_book_id",
                        column: x => x.book_id,
                        principalTable: "books",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "book_genres",
                columns: table => new
                {
                    book_id = table.Column<long>(type: "bigint", nullable: false),
                    genre_id = table.Column<long>(type: "bigint", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_book_genres", x => new { x.book_id, x.genre_id });
                    table.ForeignKey(
                        name: "fk_book_genres_books_book_id",
                        column: x => x.book_id,
                        principalTable: "books",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "fk_book_genres_genres_genre_id",
                        column: x => x.genre_id,
                        principalTable: "genres",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "book_series",
                columns: table => new
                {
                    book_id = table.Column<long>(type: "bigint", nullable: false),
                    series_id = table.Column<long>(type: "bigint", nullable: false),
                    position = table.Column<decimal>(type: "numeric", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_book_series", x => new { x.book_id, x.series_id });
                    table.ForeignKey(
                        name: "fk_book_series_books_book_id",
                        column: x => x.book_id,
                        principalTable: "books",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "fk_book_series_series_series_id",
                        column: x => x.series_id,
                        principalTable: "series",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "library_entries",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    book_id = table.Column<long>(type: "bigint", nullable: false),
                    edition_id = table.Column<long>(type: "bigint", nullable: true),
                    status = table.Column<string>(type: "text", nullable: false),
                    rating = table.Column<decimal>(type: "numeric(2,1)", precision: 2, scale: 1, nullable: true),
                    progress_unit = table.Column<string>(type: "text", nullable: true),
                    progress_value = table.Column<decimal>(type: "numeric", nullable: true),
                    progress_percent = table.Column<decimal>(type: "numeric(5,2)", precision: 5, scale: 2, nullable: true),
                    added_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()"),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_library_entries", x => x.id);
                    table.CheckConstraint("ck_library_entries_progress_pair", "(progress_unit is null) = (progress_value is null)");
                    table.CheckConstraint("ck_library_entries_progress_percent", "progress_percent between 0 and 100");
                    table.CheckConstraint("ck_library_entries_progress_unit", "progress_unit in ('page', 'percent', 'seconds')");
                    table.CheckConstraint("ck_library_entries_progress_value", "progress_value >= 0");
                    table.CheckConstraint("ck_library_entries_rating", "rating between 0.5 and 5 and mod(rating * 2, 1) = 0");
                    table.CheckConstraint("ck_library_entries_status", "status in ('want_to_read', 'reading', 'read', 'did_not_finish')");
                    table.ForeignKey(
                        name: "fk_library_entries_books_book_id",
                        column: x => x.book_id,
                        principalTable: "books",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_library_entries_editions_edition_id",
                        column: x => x.edition_id,
                        principalTable: "editions",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "reads",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    library_entry_id = table.Column<long>(type: "bigint", nullable: false),
                    started_on = table.Column<DateOnly>(type: "date", nullable: true),
                    finished_on = table.Column<DateOnly>(type: "date", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_reads", x => x.id);
                    table.CheckConstraint("ck_reads_dates", "finished_on >= started_on");
                    table.ForeignKey(
                        name: "fk_reads_library_entries_library_entry_id",
                        column: x => x.library_entry_id,
                        principalTable: "library_entries",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "ix_authors_hardcover_id",
                table: "authors",
                column: "hardcover_id",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_authors_open_library_author_key",
                table: "authors",
                column: "open_library_author_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_book_authors_author_id",
                table: "book_authors",
                column: "author_id");

            migrationBuilder.CreateIndex(
                name: "ix_book_genres_genre_id",
                table: "book_genres",
                column: "genre_id");

            migrationBuilder.CreateIndex(
                name: "ix_book_series_series_id",
                table: "book_series",
                column: "series_id");

            migrationBuilder.CreateIndex(
                name: "ix_books_hardcover_id",
                table: "books",
                column: "hardcover_id",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_books_open_library_work_key",
                table: "books",
                column: "open_library_work_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_editions_book_id",
                table: "editions",
                column: "book_id");

            migrationBuilder.CreateIndex(
                name: "ix_editions_hardcover_edition_id",
                table: "editions",
                column: "hardcover_edition_id",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_editions_isbn10",
                table: "editions",
                column: "isbn10",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_editions_isbn13",
                table: "editions",
                column: "isbn13",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_editions_open_library_edition_key",
                table: "editions",
                column: "open_library_edition_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_genres_name",
                table: "genres",
                column: "name",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_library_entries_book_id",
                table: "library_entries",
                column: "book_id");

            migrationBuilder.CreateIndex(
                name: "ix_library_entries_edition_id",
                table: "library_entries",
                column: "edition_id");

            migrationBuilder.CreateIndex(
                name: "ix_library_entries_user_id_book_id",
                table: "library_entries",
                columns: new[] { "user_id", "book_id" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_library_entries_user_id_status",
                table: "library_entries",
                columns: new[] { "user_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_reads_library_entry_id",
                table: "reads",
                column: "library_entry_id");

            migrationBuilder.CreateIndex(
                name: "ix_series_hardcover_id",
                table: "series",
                column: "hardcover_id",
                unique: true);

            // Deleting a Supabase Auth user deletes their library (account deletion, UK GDPR).
            migrationBuilder.Sql("""
                alter table library_entries
                    add constraint fk_library_entries_users_user_id
                    foreign key (user_id) references auth.users (id) on delete cascade;
                """);

            // RLS with no policies denies the Data API roles (anon, authenticated) everything.
            // The API connects as the table owner, which bypasses RLS.
            foreach (var table in new[] { "books", "editions", "authors", "book_authors", "series", "book_series",
                         "genres", "book_genres", "library_entries", "reads", "__EFMigrationsHistory" })
            {
                migrationBuilder.Sql($"""alter table "{table}" enable row level security;""");
            }
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "book_authors");

            migrationBuilder.DropTable(
                name: "book_genres");

            migrationBuilder.DropTable(
                name: "book_series");

            migrationBuilder.DropTable(
                name: "reads");

            migrationBuilder.DropTable(
                name: "authors");

            migrationBuilder.DropTable(
                name: "genres");

            migrationBuilder.DropTable(
                name: "series");

            migrationBuilder.DropTable(
                name: "library_entries");

            migrationBuilder.DropTable(
                name: "editions");

            migrationBuilder.DropTable(
                name: "books");
        }
    }
}
