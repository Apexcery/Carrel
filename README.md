# Carrel

A book-tracking website: track what you read and find new books.

## Structure

- `api/` – ASP.NET Core minimal API (.NET 10). Solution: `api/Carrel.slnx`.
- `web/` – React + TypeScript single-page app (Vite).

## Prerequisites

- .NET 10 SDK
- Node.js 20.19+ or 22.12+ (24 LTS recommended)

## Running locally

The API reads secrets from .NET user secrets (`dotnet user-secrets set <key> <value> --project api/Carrel.Api`):

- `ConnectionStrings:Carrel` – Supabase session pooler connection string, in Npgsql format
- `Hardcover:ApiToken` – Hardcover API token with the `read:catalog` scope, without the `Bearer ` prefix
- `BookSources:ContactEmail` – contact address sent in the User-Agent to book data sources
- `Supabase:SecretKey` – Supabase secret key (`carrel_api`), used to check passwords and delete accounts; only account deletion fails without it

Local development uses a separate Supabase project, `carrel-dev`: its address is in `appsettings.Development.json` and `web/.env.development`, and the user secrets above should point at it. Production's settings live only in Cloud Run (secrets and `appsettings.json`) and in Cloudflare's build variables.

Restore local tools (EF Core migrations) with `dotnet tool restore`.

API (http://localhost:5155, health check at `/health`):

```bash
dotnet run --project api/Carrel.Api
```

Frontend (http://localhost:5173):

```bash
cd web
npm install
npm run dev
```

## Database migrations

The API applies any pending migrations when it starts, before it takes requests: locally that's the dev database (the user secret), and in production it happens as each new version deploys. If a migration fails, the new version doesn't start and Cloud Run keeps serving the old one; the failure is in the new revision's logs.

The running site keeps using the old code until the new version has started, so a migration must not break it: add columns and tables rather than renaming or dropping ones the deployed code still reads. To drop or rename, first deploy code that no longer uses the old column, then remove it in a later release.

To apply migrations without starting the API, `dotnet ef database update --project api/Carrel.Api` still works against the dev database.

## Library imports and exports

Readers can export their library from Settings → Import & Export in two formats: Goodreads' columns (which Goodreads, StoryGraph, and Carrel can import; it loses start dates, earlier reads, half stars, and progress) and Carrel's own (`Imports/CarrelCsv.cs`), which keeps everything and is matched by Hardcover id when imported back.

Goodreads, StoryGraph, and Carrel imports run in batches of about a minute, each queuing the next. Cloud Run only gives the API CPU while it handles a request, so in production each batch arrives from the Cloud Tasks queue `carrel-imports` (europe-west1) as a request to `/internal/imports/{id}/process`, signed as the `carrel-api` service account (settings under `Imports:Queue` in `appsettings.json`). Locally there's no queue, and batches run in the API process.

Imports only use Hardcover capacity readers aren't using, and pause for the day once fewer than 1,000 of Hardcover's daily requests are left. Progress is saved per row, so an import carries on after a restart; one that stalls is queued again when the reader opens the import page.

## Auth emails

Supabase sends sign-up, invitation, password reset, and email change emails through Resend (SMTP, from `no-reply@carrel.zenithal.co.uk`). Their templates are customised in Supabase (Authentication → Emails → Templates). Each template's link must point at the site, not Supabase's own address:

```
{{ .SiteURL }}/auth/confirm?token_hash={{ .TokenHash }}&type=<type>
```

with `type` `email` (confirm sign-up), `invite`, `recovery` (reset password), or `email_change`. That page confirms the link and signs the reader in. Links last an hour (Email OTP expiration 3600).

## Copyright takedowns

When a valid notice arrives (see `/copyright`), hide the material by setting a flag on the book in the Supabase SQL Editor. Book refreshes don't clear these flags.

```sql
-- Hide the cover (the book's and its editions') everywhere Carrel shows the book, including search and series pages.
update books set cover_suppressed = true where id = <book id>;

-- Hide the description.
update books set description_suppressed = true where id = <book id>;
```

The book id is the number in the page address, e.g. `/books/20`. A book that only appears in search results has no row yet: open its page once to store it, then set the flag. Tell the source (Hardcover or Open Library) as well.

A profile picture is the reader's own upload, so it's removed outright. Find its file by the username in the profile's address (`/@username`):

```sql
select avatar_path from profiles where lower(username) = lower('<username>');
```

Delete that file in the Supabase dashboard (Storage, the `avatars` bucket; the path is a folder named after the reader's id, then the file), then clear it on the profile so the site shows the silhouette instead:

```sql
update profiles set avatar_path = null where lower(username) = lower('<username>');
```

The reader can upload another picture afterwards.
