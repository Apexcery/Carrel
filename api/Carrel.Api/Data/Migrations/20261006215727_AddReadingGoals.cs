using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddReadingGoals : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "reading_goals",
                columns: table => new
                {
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    year = table.Column<int>(type: "integer", nullable: false),
                    books = table.Column<int>(type: "integer", nullable: false),
                    created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()"),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_reading_goals", x => new { x.user_id, x.year });
                    table.CheckConstraint("ck_reading_goals_books", "books between 1 and 1000");
                });

            // Deleting a Supabase Auth user deletes their goals (account deletion, UK GDPR).
            migrationBuilder.Sql("""
                alter table reading_goals
                    add constraint fk_reading_goals_users_user_id
                    foreign key (user_id) references auth.users (id) on delete cascade;
                """);

            // RLS with no policies denies the Data API roles; the API connects as the table owner.
            migrationBuilder.Sql("alter table reading_goals enable row level security;");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "reading_goals");
        }
    }
}
