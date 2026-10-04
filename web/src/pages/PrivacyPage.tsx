import { Contact } from '../components/Contact'
import { LEGAL_UPDATED } from '../legal'

/** The privacy notice (UK GDPR). Keep it in step with what the code actually stores and who it's shared with. */
export function PrivacyPage() {
  return (
    <article className="legal">
      <header className="search-header">
        <h1 className="search-title">Privacy</h1>
        <p className="muted mono">Last updated {LEGAL_UPDATED}</p>
      </header>

      <p className="legal-lede">
        Carrel is a place to keep track of what you read. This notice explains what information it keeps about you,
        why, who else handles it, and what you can do about it.
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
          <strong>Your library:</strong> the books on your shelves, their status, your ratings, your reading progress,
          the editions you chose, and the dates you started and finished reading. Your library is private.
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

      <h2>What stays in your browser</h2>
      <p>
        Carrel doesn’t use cookies, analytics, or advertising trackers. It keeps a few things in your browser’s storage
        so the site works the way you’ve set it up:
      </p>
      <ul>
        <li>your sign-in session, so you stay signed in;</li>
        <li>your theme and accent colour; and</li>
        <li>your recent searches, which never leave your browser.</li>
      </ul>
      <p>These are needed for features you’ve asked for, so Carrel doesn’t ask for consent to store them.</p>

      <h2>Why Carrel uses your information</h2>
      <ul>
        <li>
          <strong>To provide the service you signed up for:</strong> your account and your library. The legal basis is
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
          <strong>Supabase</strong> stores Carrel’s database and handles signing in. The data is stored in London.
        </li>
        <li>
          <strong>Google Cloud</strong> runs Carrel’s server, in Belgium.
        </li>
        <li>
          <strong>Cloudflare</strong> delivers the website to your browser.
        </li>
        <li>
          <strong>Hardcover</strong> and <strong>Open Library</strong> supply the book information. When you search,
          Carrel’s server sends them your search words, but nothing that identifies you. Book covers load straight from
          their image servers, so they see your IP address when your browser fetches a cover.
        </li>
      </ul>
      <p>
        Some of these companies are based in the United States. Where they handle information outside the UK, they use
        the safeguards UK law requires, such as approved contract terms.
      </p>

      <h2>How long it’s kept</h2>
      <p>
        Your account and library are kept until you delete your account. Deleting it (in Settings, under Account)
        removes your account, username, and library straight away. Sign-in records end with each session, and logs
        are deleted automatically, after 30 days by default.
      </p>
      {/* "Straight away" holds while the Supabase plan keeps no backups; revisit this if backups are turned on. */}

      <h2>Your rights</h2>
      <p>Under UK data protection law, you can ask to:</p>
      <ul>
        <li>get a copy of your information, including in a form you can take elsewhere;</li>
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
