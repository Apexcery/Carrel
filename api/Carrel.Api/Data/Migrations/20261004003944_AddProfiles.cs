using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Carrel.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class AddProfiles : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "profiles",
                columns: table => new
                {
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    username = table.Column<string>(type: "text", nullable: false),
                    created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()"),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "now()")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_profiles", x => x.user_id);
                    table.CheckConstraint("ck_profiles_username", "username ~ '^[A-Za-z0-9_-]{3,20}$'");
                });

            // Deleting a Supabase Auth user deletes their profile (account deletion, UK GDPR).
            migrationBuilder.Sql("""
                alter table profiles
                    add constraint fk_profiles_users_user_id
                    foreign key (user_id) references auth.users (id) on delete cascade;
                """);

            // Usernames are shown as typed but unique ignoring case ("Stuart" and "stuart" can't both exist).
            migrationBuilder.Sql("create unique index ix_profiles_username_lower on profiles (lower(username));");

            // RLS with no policies denies the Data API roles; the API connects as the table owner.
            migrationBuilder.Sql("alter table profiles enable row level security;");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "profiles");
        }
    }
}
