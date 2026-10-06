import { Contact } from '../components/Contact'
import { LEGAL_UPDATED } from '../legal'
import { usePageTitle } from '../pageTitle'

/** Copyright and takedown policy (follows the DMCA notice format). */
export function CopyrightPage() {
  usePageTitle('Copyright')
  return (
    <article className="legal">
      <header className="search-header">
        <h1 className="search-title">Copyright</h1>
        <p className="muted mono">Last updated {LEGAL_UPDATED}</p>
      </header>

      <p className="legal-lede">
        Book covers and descriptions on Carrel come from Hardcover and Open Library. If you own the copyright in
        something shown here and don’t want it used, tell us and it will be taken down.
      </p>

      <h2>Where Carrel’s book information comes from</h2>
      <p>
        Book details, covers, and descriptions are supplied by{' '}
        <a href="https://hardcover.app" rel="noreferrer">
          Hardcover
        </a>{' '}
        and{' '}
        <a href="https://openlibrary.org" rel="noreferrer">
          Open Library
        </a>
        , and covers load from their servers. Readers can’t upload anything to Carrel.
      </p>

      <h2>Asking for something to be taken down</h2>
      <p>Send a notice to the contact below that includes:</p>
      <ol>
        <li>the work you believe is being infringed;</li>
        <li>the web address of the page on Carrel where it appears;</li>
        <li>your name and how to reach you (email address, postal address, and phone number);</li>
        <li>
          a statement that you believe in good faith that the use isn’t authorised by the copyright owner, their agent,
          or the law;
        </li>
        <li>
          a statement that the information in your notice is accurate and, under penalty of perjury, that you own the
          copyright or are authorised to act for the owner; and
        </li>
        <li>your physical or electronic signature.</li>
      </ol>
      <p>
        This is the notice format of the US Digital Millennium Copyright Act (DMCA). Notices under UK law are welcome in
        the same form.
      </p>
      <Contact subject="copyright notices" />

      <h2>What happens next</h2>
      <p>
        When a complete notice arrives, the material is removed from Carrel promptly and the source (Hardcover or Open
        Library) is told, so it can be dealt with there too.
      </p>

      <h2>If you think something was removed by mistake</h2>
      <p>
        Contact us with the web address of the page, what was removed, and why you believe the removal was a mistake.
      </p>
    </article>
  )
}
