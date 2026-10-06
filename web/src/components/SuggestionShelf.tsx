import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { apiGet } from '../api'
import { useSession } from '../auth'
import { listNames, seriesPosition } from '../format'
import type { BookSuggestion, LibraryItem } from '../types'
import { Cover } from './Cover'
import { ShelfRow } from './ShelfRow'
import { Tooltip } from './Tooltip'

const THIS_YEAR = new Date().getFullYear()
const NONE = new Set<number>()

/**
 * A titled row of suggested book covers, or nothing when there are none to show. Books not out yet are marked
 * Upcoming, unless markUpcoming is false (on a shelf where they all are).
 */
export function SuggestionShelf({
  title,
  books,
  limit,
  markUpcoming = true,
}: {
  title: string
  books: BookSuggestion[]
  limit: number
  markUpcoming?: boolean
}) {
  if (books.length === 0) {
    return null
  }
  return (
    <section className="book-section">
      <ShelfRow heading={<h2 className="section-title">{title}</h2>}>
        {books.slice(0, limit).map((book) => {
          const upcoming = markUpcoming && book.releaseYear !== null && book.releaseYear > THIS_YEAR
          return (
            <li key={book.hardcoverId}>
              <Tooltip content={describe(book)}>
                {/* Labelled with the same, since the tooltip is only for sighted readers. */}
                <Link
                  to={`/books/hardcover/${book.hardcoverId}`}
                  className="shelf-book"
                  aria-label={upcoming ? `${describe(book)}. Upcoming` : describe(book)}
                >
                  <Cover url={book.coverUrl} title={book.title} author={book.authors[0]} />
                  {upcoming && <span className="badge">Upcoming</span>}
                </Link>
              </Tooltip>
            </li>
          )
        })}
      </ShelfRow>
    </section>
  )
}

/** A placeholder shelf, the shape of SuggestionShelf, while suggestions load. */
export function SuggestionShelfSkeleton() {
  return (
    <section className="book-section skeleton" aria-hidden="true">
      <div className="section-title">
        <div className="skeleton-line short" />
      </div>
      <div className="shelf-scroll">
        <ul className="shelf-strip">
          {Array.from({ length: 6 }, (_, i) => (
            <li key={i}>
              <div className="cover" />
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

/**
 * Hardcover ids of the books in the reader's library, so suggestions can leave them out. Null until the library has
 * loaded, so owned books don't appear and then vanish; empty when signed out, or if the library can't be loaded.
 */
export function useOwnedHardcoverIds(): Set<number> | null {
  const signedIn = Boolean(useSession())
  const library = useQuery({ queryKey: ['library'], queryFn: () => apiGet<LibraryItem[]>('/library'), enabled: signedIn })
  const owned = useMemo(
    () => (library.data ? new Set(library.data.flatMap((item) => item.book.hardcoverId ?? [])) : null),
    [library.data],
  )
  return !signedIn || library.isError ? NONE : owned
}

/** Tooltip and label: title, authors, and place in its series. */
function describe(book: BookSuggestion): string {
  const position = seriesPosition(book.seriesPosition)
  const series = book.seriesName ? ` (${book.seriesName}${position ? `, book ${position}` : ''})` : ''
  const authors = book.authors.length > 0 ? ` by ${listNames(book.authors)}` : ''
  return `${book.title}${series}${authors}`
}
