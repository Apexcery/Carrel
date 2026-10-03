# Carrel

A book-tracking website: track what you read and find new books.

## Structure

- `api/` – ASP.NET Core minimal API (.NET 10). Solution: `api/Carrel.slnx`.
- `web/` – React + TypeScript single-page app (Vite).

## Prerequisites

- .NET 10 SDK
- Node.js 20.19+ or 22.12+ (24 LTS recommended)

## Running locally

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
