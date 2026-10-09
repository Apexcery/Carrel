# Carrel

A book-tracking website and Android app: track what you read and find new books.

## Structure

- `api/` – ASP.NET Core minimal API (.NET 10). Solution: `api/Carrel.slnx`.
- `web/` – React + TypeScript single-page app (Vite).
- `android/` – Android app (Kotlin, Jetpack Compose), for Android 8 and later.

## Prerequisites

- .NET 10 SDK
- Node.js 20.19+ or 22.12+ (24 LTS recommended)
- For the app: JDK 21 and the Android SDK (Android Studio's own is fine). The SDK path goes in `android/local.properties` (`sdk.dir=…`), which Studio writes for you.

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

## Android app

Two builds install side by side:

- **Carrel Dev** (`./gradlew assembleDebug`, package `uk.co.zenithal.carrel.debug`) uses the dev Supabase project and the local API. A phone or emulator connected over USB reaches the API through `adb reverse tcp:5155 tcp:5155`.
- **Carrel** (`./gradlew assembleRelease`, package `uk.co.zenithal.carrel`) uses production, signed with the release key.

Run Gradle with JDK 21 (`JAVA_HOME`). `./gradlew testDebugUnitTest` runs the unit tests. Install a build with `adb install -r app/build/outputs/apk/<debug|release>/app-<debug|release>.apk`.

The release key lives outside the repo, and its settings are in `~/.gradle/gradle.properties` (`carrel.release.storeFile`, `storePassword`, `keyAlias`, and `keyPassword`). Keep a backup of both: an app signed with a different key can't update the installed one, so it has to be uninstalled first, losing what it had saved. Without those settings, `assembleRelease` builds an unsigned APK.

Each merge into `main` that changes `android/` publishes the release APK as a GitHub release, `android-v<versionCode>`, with the file as `carrel.apk` (`.github/workflows/android.yml`). It runs the unit tests first, and fails if the APK isn't signed with the key in `assetlinks.json`. Bump `versionCode` and `versionName` in `android/app/build.gradle.kts` in the PR from `dev` into `main`: that PR's check fails if `versionCode` isn't above the latest release's. The workflow signs with the release key from four Actions secrets, set once with:

```bash
base64 -w0 ~/.carrel/carrel-release.jks | gh secret set CARREL_RELEASE_KEYSTORE
gh secret set CARREL_RELEASE_STORE_PASSWORD
gh secret set CARREL_RELEASE_KEY_ALIAS
gh secret set CARREL_RELEASE_KEY_PASSWORD
```

Each password command asks for its value (from `~/.gradle/gradle.properties`). In PowerShell, which has no `base64`, set the key with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.carrel\carrel-release.jks")) | gh secret set CARREL_RELEASE_KEYSTORE
```

The release's notes list the pull requests since the last release that changed `android/`, by their squash commits' titles. The app updates itself from these releases: each launch checks GitHub for a higher `android-v<versionCode>`, and offers it with those notes, to update now, at the next launch, or skip that version (Settings can check again at any time). It downloads `carrel.apk` and installs it, and Android reopens Carrel afterwards. Android asks to confirm the first update the app installs; after that, only Play Protect may ask to scan it. Versions before 0.2.0 have no updater, so install that one by hand.

To try the updater, Carrel Dev reads a test list of releases from `http://localhost:8765/releases.json` instead. Build a second Carrel Dev with a higher `versionCode`, serve it and a `releases.json` (in GitHub's format, tagged `android-v<versionCode>`, with the APK as `carrel.apk`) from a folder with `npx http-server -p 8765`, and run `adb reverse tcp:8765 tcp:8765`.

Links in Carrel's emails (`/auth/confirm`) open the app when it's installed. Android checks this against `web/public/.well-known/assetlinks.json`, which lists the release key's SHA-256 fingerprint: update it if the key ever changes. Carrel Dev handles the dev project's links (`http://localhost:5173/auth/confirm…`) only when they're sent to it directly, e.g. `adb shell am start -a android.intent.action.VIEW -d "<link>" uk.co.zenithal.carrel.debug`.

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

with `type` `email` (confirm sign-up), `invite`, `recovery` (reset password), or `email_change`. That page confirms the link and signs the reader in; with the app installed, the link opens the app, which does the same. Links last an hour (Email OTP expiration 3600).

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
