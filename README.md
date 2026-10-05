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
