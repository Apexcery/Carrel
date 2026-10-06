import { Link, useParams } from 'react-router'
import { ErrorNotice } from '../components/ErrorNotice'
import { ReadingNow, ShelfStrip, ShelfTitle } from '../components/LibraryShelves'
import { ReadingGoalPanel } from '../components/ReadingGoal'
import { YearInBooks } from '../components/YearInBooks'
import { usePageTitle } from '../pageTitle'
import { useReader } from '../readers'
import { shelfItems } from '../shelves'
import type { ReadingStatus } from '../types'
import { NOT_FOUND_TITLE, NotFoundPage } from './NotFoundPage'

/** Every shelf, Paused included, in this order; Reading shows its books' progress. */
const SHELVES: ReadingStatus[] = ['reading', 'paused', 'want_to_read', 'read', 'did_not_finish']
/** Books shown on each shelf; the shelf title links to the whole shelf. */
const SHELF_BOOKS = 12

/**
 * A reader's profile at /@username: every shelf and their year so far. Anyone can see a public profile; a private one
 * only its reader.
 */
export function ProfilePage() {
  const handle = useParams().handle ?? ''
  // The route matches any single segment, so this page also answers addresses that aren't profiles.
  const username = handle.startsWith('@') ? handle.slice(1) : ''
  const { reader, isOwn, notFound, error, refetch } = useReader(username)
  // Set here for a missing profile too, since this runs after NotFoundPage's own.
  usePageTitle(!username || notFound ? NOT_FOUND_TITLE : `@${reader?.username ?? username}`)

  if (!username || notFound) {
    return <NotFoundPage />
  }
  if (error) {
    return <ErrorNotice error={error} onRetry={() => refetch()} />
  }
  if (!reader) {
    return <section aria-busy="true" />
  }

  const { library } = reader
  return (
    <div className="profile-layout">
      <section className="profile-main">
        <header className="library-header profile-header">
          <h1 className="library-title">
            {/* Literata's @ sits low, centred on the lowercase letters; this lines it up with the capitals. */}
            <span className="profile-at">@</span>
            {reader.username}
          </h1>
          {/* Every book in the library, on any shelf. */}
          <p className="muted mono">
            – {library.length} {library.length === 1 ? 'book' : 'books'}
          </p>
        </header>

        {library.length === 0 ? (
          isOwn ? (
            <p className="muted">
              Nothing on your shelves yet. Books you add to your library will appear here, or{' '}
              <Link to="/settings/import-export" className="series-link">
                import them from Goodreads or StoryGraph
              </Link>
              .
            </p>
          ) : (
            <p className="muted">@{reader.username} hasn’t shelved any books yet.</p>
          )
        ) : (
          <div className="profile-shelves">
            {SHELVES.map((status) => {
              const items = shelfItems(library, status)
              const heading = <ShelfTitle username={reader.username} status={status} count={items.length} level="h2" />
              return (
                <section key={status} className="shelf">
                  {items.length === 0 ? (
                    <>
                      {heading}
                      <p className="muted">Nothing on this shelf yet.</p>
                    </>
                  ) : status === 'reading' ? (
                    <>
                      {heading}
                      <ReadingNow items={items} />
                    </>
                  ) : (
                    <ShelfStrip heading={heading} items={items} limit={SHELF_BOOKS} />
                  )}
                </section>
              )
            })}
          </div>
        )}
      </section>
      <aside className="profile-year">
        <ReadingGoalPanel items={library} goals={reader.goals} username={reader.username} isOwn={isOwn} />
        <YearInBooks items={library} username={reader.username} isOwn={isOwn} />
      </aside>
    </div>
  )
}
