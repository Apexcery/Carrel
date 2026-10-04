import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { apiGet } from '../api'
import { Cover } from '../components/Cover'
import { ErrorNotice } from '../components/ErrorNotice'
import { StarDisplay } from '../components/StarRating'
import { listNames, STATUS_LABELS } from '../format'
import { shelfItems, shelfPath } from '../shelves'
import type { LibraryItem, ReadingStatus } from '../types'

const SHELVES: ReadingStatus[] = ['want_to_read', 'read', 'did_not_finish']
/** Books shown on each home-page shelf; the shelf title links to the full shelf. */
const SHELF_BOOKS = 9

/** The main column is for discovery (recommendations, new releases); the reader's library sits on the right. */
export function HomePage() {
  return (
    <div className="home-layout">
      <section className="home-main">
        <h1 className="home-title">What are you reading?</h1>
        <p className="home-lede">Search above by title, author or ISBN to find a book, its editions and the series it belongs to.</p>
      </section>
      <aside className="home-library" aria-label="Your library">
        <Library />
      </aside>
    </div>
  )
}

function Library() {
  const library = useQuery({ queryKey: ['library'], queryFn: () => apiGet<LibraryItem[]>('/library') })

  if (library.isError) {
    return <ErrorNotice error={library.error} onRetry={() => library.refetch()} />
  }
  if (!library.data) {
    return <div aria-busy="true" />
  }

  const reading = shelfItems(library.data, 'reading')

  return (
    <>
      <header className="library-header">
        <h2 className="library-title">Your library</h2>
        <p className="muted mono">
          {library.data.length} {library.data.length === 1 ? 'book' : 'books'}
        </p>
      </header>

      {library.data.length === 0 && (
        <p className="muted">Nothing on your shelves yet. Open a book and choose a status to add it here.</p>
      )}

      {reading.length > 0 && (
        <section className="shelf">
          <ShelfTitle status="reading" count={reading.length} />
          <ul className="reading-now">
            {reading.map((item) => (
              <li key={item.entry.id}>
                <Link to={`/books/${item.book.id}`} className="reading-card">
                  <Cover url={item.book.coverUrl} title={item.book.title} author={item.book.authors[0]} size="small" />
                  <div>
                    <p className="reading-card-title">{item.book.title}</p>
                    {item.book.authors.length > 0 && <p className="reading-card-byline">{listNames(item.book.authors)}</p>}
                    <div className="progress-bar" aria-hidden="true">
                      <span style={{ width: `${item.entry.progressPercent ?? 0}%` }} />
                    </div>
                    <p className="progress-summary mono">
                      {item.entry.progressPercent === null ? 'Just started' : `${Math.round(item.entry.progressPercent)}%`}
                    </p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {SHELVES.map((status) => {
        const items = shelfItems(library.data, status)
        return (
          items.length > 0 && (
            <section key={status} className="shelf">
              <ShelfTitle status={status} count={items.length} />
              <ul className="shelf-strip">
                {items.slice(0, SHELF_BOOKS).map((item) => (
                  <li key={item.entry.id}>
                    <Link to={`/books/${item.book.id}`} className="shelf-book" title={item.book.title}>
                      <Cover url={item.book.coverUrl} title={item.book.title} author={item.book.authors[0]} />
                      {item.entry.rating !== null && <StarDisplay value={item.entry.rating} />}
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )
        )
      })}
    </>
  )
}

function ShelfTitle({ status, count }: { status: ReadingStatus; count: number }) {
  return (
    <h3 className="section-title">
      <Link to={shelfPath(status)} className="shelf-title-link">
        {STATUS_LABELS[status]} ({count})
      </Link>
    </h3>
  )
}
