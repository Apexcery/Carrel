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
