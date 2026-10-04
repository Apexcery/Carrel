using Microsoft.EntityFrameworkCore;

namespace Carrel.Api.Data;

public class CarrelDbContext(DbContextOptions<CarrelDbContext> options) : DbContext(options)
{
    public DbSet<Book> Books => Set<Book>();
    public DbSet<Edition> Editions => Set<Edition>();
    public DbSet<Author> Authors => Set<Author>();
    public DbSet<BookAuthor> BookAuthors => Set<BookAuthor>();
    public DbSet<Series> Series => Set<Series>();
    public DbSet<BookSeries> BookSeries => Set<BookSeries>();
    public DbSet<Genre> Genres => Set<Genre>();
    public DbSet<BookGenre> BookGenres => Set<BookGenre>();
    public DbSet<LibraryEntry> LibraryEntries => Set<LibraryEntry>();
    public DbSet<Read> Reads => Set<Read>();
    public DbSet<Profile> Profiles => Set<Profile>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Book>(book =>
        {
            book.ToTable(t => t.HasCheckConstraint("ck_books_description_source",
                SnakeCaseEnumConverter<DescriptionSource>.CheckSql("description_source")));
            book.Property(b => b.DescriptionSource).HasConversion<SnakeCaseEnumConverter<DescriptionSource>>();
            book.Property(b => b.HardcoverRating).HasPrecision(3, 2);
            book.Property(b => b.FetchedAt).HasDefaultValueSql("now()");
            book.HasIndex(b => b.HardcoverId).IsUnique();
            book.HasIndex(b => b.OpenLibraryWorkKey).IsUnique();
        });

        modelBuilder.Entity<Edition>(edition =>
        {
            edition.ToTable(t =>
            {
                t.HasCheckConstraint("ck_editions_format", SnakeCaseEnumConverter<EditionFormat>.CheckSql("format"));
                t.HasCheckConstraint("ck_editions_page_count", "page_count > 0");
                t.HasCheckConstraint("ck_editions_audio_seconds", "audio_seconds > 0");
            });
            edition.Property(e => e.Format).HasConversion<SnakeCaseEnumConverter<EditionFormat>>();
            edition.HasIndex(e => e.BookId);
            edition.HasIndex(e => e.Isbn13).IsUnique();
            edition.HasIndex(e => e.Isbn10).IsUnique();
            edition.HasIndex(e => e.HardcoverEditionId).IsUnique();
            edition.HasIndex(e => e.OpenLibraryEditionKey).IsUnique();
        });

        modelBuilder.Entity<Author>(author =>
        {
            author.HasIndex(a => a.HardcoverId).IsUnique();
            author.HasIndex(a => a.OpenLibraryAuthorKey).IsUnique();
        });

        modelBuilder.Entity<BookAuthor>(bookAuthor =>
        {
            bookAuthor.HasKey(ba => new { ba.BookId, ba.AuthorId, ba.Role });
            bookAuthor.HasIndex(ba => ba.AuthorId);
        });

        modelBuilder.Entity<Series>(series => series.HasIndex(s => s.HardcoverId).IsUnique());

        modelBuilder.Entity<BookSeries>(bookSeries =>
        {
            bookSeries.HasKey(bs => new { bs.BookId, bs.SeriesId });
            bookSeries.HasIndex(bs => bs.SeriesId);
        });

        modelBuilder.Entity<Genre>(genre => genre.HasIndex(g => g.Name).IsUnique());

        modelBuilder.Entity<BookGenre>(bookGenre =>
        {
            bookGenre.HasKey(bg => new { bg.BookId, bg.GenreId });
            bookGenre.HasIndex(bg => bg.GenreId);
        });

        modelBuilder.Entity<LibraryEntry>(entry =>
        {
            entry.ToTable(t =>
            {
                t.HasCheckConstraint("ck_library_entries_status", SnakeCaseEnumConverter<ReadingStatus>.CheckSql("status"));
                t.HasCheckConstraint("ck_library_entries_progress_unit", SnakeCaseEnumConverter<ProgressUnit>.CheckSql("progress_unit"));
                t.HasCheckConstraint("ck_library_entries_progress_pair", "(progress_unit is null) = (progress_value is null)");
                t.HasCheckConstraint("ck_library_entries_progress_value", "progress_value >= 0");
                t.HasCheckConstraint("ck_library_entries_progress_percent", "progress_percent between 0 and 100");
                t.HasCheckConstraint("ck_library_entries_rating", "rating between 0.5 and 5 and mod(rating * 2, 1) = 0");
            });
            entry.Property(e => e.Status).HasConversion<SnakeCaseEnumConverter<ReadingStatus>>();
            entry.Property(e => e.ProgressUnit).HasConversion<SnakeCaseEnumConverter<ProgressUnit>>();
            entry.Property(e => e.Rating).HasPrecision(2, 1);
            entry.Property(e => e.ProgressPercent).HasPrecision(5, 2);
            entry.Property(e => e.AddedAt).HasDefaultValueSql("now()");
            entry.Property(e => e.UpdatedAt).HasDefaultValueSql("now()");

            // user_id references auth.users, which EF Core doesn't model; that foreign key is added in the migration.
            entry.HasIndex(e => new { e.UserId, e.BookId }).IsUnique();
            entry.HasIndex(e => new { e.UserId, e.Status });
            entry.HasIndex(e => e.BookId);
            entry.HasIndex(e => e.EditionId);

            entry.HasOne(e => e.Book).WithMany().OnDelete(DeleteBehavior.Restrict);
            entry.HasOne(e => e.Edition).WithMany().OnDelete(DeleteBehavior.SetNull);
        });

        modelBuilder.Entity<Profile>(profile =>
        {
            // user_id references auth.users and usernames are unique ignoring case; both are added in the migration.
            profile.HasKey(p => p.UserId);
            profile.ToTable(t => t.HasCheckConstraint("ck_profiles_username", "username ~ '^[A-Za-z0-9_-]{3,20}$'"));
            profile.Property(p => p.CreatedAt).HasDefaultValueSql("now()");
            profile.Property(p => p.UpdatedAt).HasDefaultValueSql("now()");
        });

        modelBuilder.Entity<Read>(read =>
        {
            read.ToTable(t => t.HasCheckConstraint("ck_reads_dates", "finished_on >= started_on"));
            read.HasIndex(r => r.LibraryEntryId);
        });
    }
}
