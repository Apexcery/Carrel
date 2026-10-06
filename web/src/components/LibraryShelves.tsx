import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { listNames, STATUS_LABELS } from '../format'
import { shelfPath } from '../shelves'
import type { LibraryItem, ReadingStatus } from '../types'
import { Cover } from './Cover'
import { ShelfRow } from './ShelfRow'
import { StarDisplay } from './StarRating'
import { Tooltip } from './Tooltip'

/** A shelf's title and count, linking to the whole shelf. */
export function ShelfTitle({
  username,
  status,
  count,
  level,
}: {
  username: string
  status: ReadingStatus
  count: number
  level: 'h2' | 'h3'
}) {
  const Heading = level
  return (
    <Heading className="section-title">
      <Link to={shelfPath(username, status)} className="shelf-title-link">
        {STATUS_LABELS[status]} ({count})
      </Link>
    </Heading>
  )
}

/** Books being read, each as a card with its progress. */
export function ReadingNow({ items }: { items: LibraryItem[] }) {
  return (
    <ul className="reading-now">
      {items.map((item) => (
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
  )
}

/** A shelf's first few books in a row, with their ratings, under its title. */
export function ShelfStrip({ heading, items, limit }: { heading: ReactNode; items: LibraryItem[]; limit: number }) {
  return (
    <ShelfRow heading={heading}>
      {items.slice(0, limit).map((item) => (
        <li key={item.entry.id}>
          <Tooltip content={item.book.title}>
            <Link to={`/books/${item.book.id}`} className="shelf-book">
              <Cover url={item.book.coverUrl} title={item.book.title} author={item.book.authors[0]} />
              {item.entry.rating !== null && <StarDisplay value={item.entry.rating} />}
            </Link>
          </Tooltip>
        </li>
      ))}
    </ShelfRow>
  )
}
