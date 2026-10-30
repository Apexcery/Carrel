# Operations

Running the live site: how library imports are queued, how the auth emails are set up, and how to handle copyright takedowns.

## Library imports

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
