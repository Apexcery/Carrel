import { Contact } from '../components/Contact'
import { LEGAL_UPDATED } from '../legal'
import { usePageTitle } from '../pageTitle'

/** The privacy notice (UK GDPR). Keep it in step with what the code actually stores and who it's shared with. */
export function PrivacyPage() {
  usePageTitle('Privacy')
  return (
    <article className="legal">
      <header className="search-header">
        <h1 className="search-title">Privacy</h1>
        <p className="muted mono">Last updated {LEGAL_UPDATED}</p>
      </header>

      <p className="legal-lede">
        Carrel is a place to keep track of what you read, on the website and in the Android app. This notice explains
        what information it keeps about you, why, who else handles it, and what you can do about it.
      </p>

      <h2>Who runs Carrel</h2>
      <p>
        Carrel is run by an individual in the United Kingdom, who is responsible for your information under UK data
        protection law (the UK GDPR).
      </p>
      <Contact subject="privacy questions" />

      <h2>What Carrel keeps</h2>
      <ul>
        <li>
          <strong>Your account:</strong> your email address and password. The password is only stored in scrambled
          (hashed) form, so it can’t be read back.
        </li>
        <li>
          <strong>Your username</strong>, which is how other readers will see you.
        </li>
        <li>
          <strong>Your profile picture</strong>, if you add one. Carrel keeps a small copy of the part you choose, made
          from your picture without its photo details (such as where and when it was taken).
        </li>
        <li>
          <strong>Your library:</strong> the books on your shelves, their status, your ratings, your reading progress,
          the editions you chose, and the dates you started and finished reading. It’s shown on your profile (see
          below).
        </li>
        <li>
          <strong>Your place in books you read in the Android app</strong>, so another phone or tablet can carry on
          from there. It isn’t shown on your profile, and it’s deleted when you remove the book from your library.
        </li>
        <li>
          <strong>Your reading goals:</strong> how many books you mean to read each year, if you set a goal.
        </li>
        <li>
          <strong>Whether your profile is public</strong>, which you choose.
        </li>
        <li>
          <strong>Your imports:</strong> when you import a Goodreads or StoryGraph export, each book’s title, authors,
          ISBN, status, rating, and reading dates from the file, so Carrel can match it to a book and show you any it
          couldn’t. Reviews, notes, and anything else in the file aren’t kept.
        </li>
        <li>
          <strong>Sign-in records:</strong> the IP address and browser of each signed-in session, kept until that
          session ends.
        </li>
        <li>
          <strong>Server logs:</strong> each request your browser makes to Carrel is logged with your IP address, your
          browser, and the address requested, which includes the words you search for. Logs are used to keep Carrel
          working and secure.
        </li>
      </ul>

      <h2>Your profile</h2>
      <p>
        Your profile, at your username after <span className="mono">carrel.zenithal.co.uk/@</span>, shows your username,
        your picture, and your library: your shelves, ratings, reading progress, reading dates, and reading goals.
        Profiles are public unless you make yours private, so anyone can see it, including people who aren’t signed in.
        You can make it private when you choose your username, or at any time in Settings, under Account; then only you
        can see it. Your picture also shows beside your username at the top of each page you visit while signed in. Your
        email address is never shown.
      </p>

      <h2>What stays on your device</h2>
      <p>
        Carrel doesn’t use cookies, analytics, crash reporting, or advertising trackers, on the website or in the app.
        It keeps a few things in your browser’s storage, or on your phone if you use the app, so Carrel works the way
        you’ve set it up:
      </p>
      <ul>
        <li>your sign-in session, so you stay signed in;</li>
        <li>your theme and accent colour;</li>
        <li>your recent searches, which never leave your device; and</li>
        <li>
          in the app, a copy of what it last loaded from Carrel, such as your profile, so it opens quickly and still
          works without a signal. It’s cleared when you sign out.
        </li>
      </ul>
      <p>These are needed for features you’ve asked for, so Carrel doesn’t ask for consent to store them.</p>

      <h2>Why Carrel uses your information</h2>
      <ul>
        <li>
          <strong>To provide the service you signed up for:</strong> your account, your library, and your profile. The
          legal basis is
          performing our agreement with you.
        </li>
        <li>
          <strong>To keep Carrel secure and working:</strong> sign-in records, logs, and limits that stop abuse. The
          legal basis is legitimate interests.
        </li>
      </ul>
      <p>Carrel doesn’t sell your information, show adverts, or use your information to profile you.</p>

      <h2>Who else handles it</h2>
      <p>Carrel relies on these services, which handle information only to provide their service to Carrel:</p>
      <ul>
        <li>
          <strong>Supabase</strong> stores Carrel’s database and profile pictures, and handles signing in. The data is
          stored in London.
        </li>
        <li>
          <strong>Google Cloud</strong> runs Carrel’s server, in Belgium.
        </li>
        <li>
          <strong>Resend</strong> sends Carrel’s emails, such as confirming your account or changing your email address,
          so it handles your email address.
        </li>
        <li>
          <strong>Cloudflare</strong> delivers the website to your browser and forwards emails sent to Carrel’s contact
          address.
        </li>
        <li>
          <strong>Hardcover</strong> and <strong>Open Library</strong> supply the book information. When you search or
          import, Carrel’s server sends them your search words or the books’ titles, authors, and ISBNs, but nothing
          that identifies you. Book covers load straight from
          their image servers, so they see your IP address when your browser fetches a cover.
        </li>
      </ul>
      <p>
        Some of these companies are based in the United States. Where they handle information outside the UK, they use
        the safeguards UK law requires, such as approved contract terms.
      </p>

      <h2>How long it’s kept</h2>
      <p>
        Your account, picture, library, reading goals, and imports are kept until you delete your account. Deleting it
        (in Settings, under Account) removes your account, username, picture, library, reading goals, and imports
        straight away. You can also remove just your picture there at any time, or delete just your library and imports
        (under Delete book data) and keep your account. Sign-in records end with each session, and logs are deleted
        automatically, after 30 days by default.
      </p>
      {/* "Straight away" holds while the Supabase plan keeps no backups; revisit this if backups are turned on. */}

      <h2>Your rights</h2>
      <p>Under UK data protection law, you can ask to:</p>
      <ul>
        <li>
          get a copy of your information, including in a form you can take elsewhere (you can download your library
          yourself in Settings, under Import &amp; Export);
        </li>
        <li>correct it (you can change your username, email, and password yourself in Settings);</li>
        <li>delete it (Settings, then Account, then Delete account); and</li>
        <li>object to or limit how it’s used.</li>
      </ul>
      <p>
        To ask, use the contact details above. You can also complain to the{' '}
        <a href="https://ico.org.uk/make-a-complaint/" rel="noreferrer">
          Information Commissioner’s Office
        </a>
        , the UK’s data protection regulator.
      </p>

      <h2>Children</h2>
      <p>Carrel isn’t for children: you must be 13 or over to create an account.</p>

      <h2>Changes</h2>
      <p>
        If this notice changes in a way that matters, Carrel will say so on the site. The date at the top shows when it
        last changed.
      </p>
    </article>
  )
}
