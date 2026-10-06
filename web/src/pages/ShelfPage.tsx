import { Field, Label } from '@headlessui/react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router'
import { apiGet } from '../api'
import { Cover } from '../components/Cover'
import { ErrorNotice } from '../components/ErrorNotice'
import { Select } from '../components/Select'
import { StarDisplay } from '../components/StarRating'
import { Tooltip } from '../components/Tooltip'
import { listNames, STATUS_LABELS } from '../format'
import { usePageTitle } from '../pageTitle'
import {
  defaultSort,
  directionLabel,
  filterItems,
  naturalDirection,
  sortItems,
  sortOptions,
  statusFromSlug,
  type SortDirection,
  type SortKey,
} from '../shelves'
import type { LibraryItem } from '../types'
import { NOT_FOUND_TITLE, NotFoundPage } from './NotFoundPage'

/**
 * Every book on one of the reader's shelves, e.g. /shelves/want-to-read, with a filter and sort.
 * The filter, sort and direction live in the URL (?q=&sort=&dir=), so they survive back/forward and bookmarks.
 */
export function ShelfPage() {
  const status = statusFromSlug(useParams().slug)
  const [params, setParams] = useSearchParams()
  const library = useQuery({ queryKey: ['library'], queryFn: () => apiGet<LibraryItem[]>('/library') })
  // Set here for an unknown shelf too, since this runs after NotFoundPage's own.
  usePageTitle(status ? STATUS_LABELS[status] : NOT_FOUND_TITLE)

  if (!status) {
    return <NotFoundPage />
  }

  const options = sortOptions(status)
  const sort = options.find((o) => o.key === params.get('sort'))?.key ?? defaultSort(status)
  const dir: SortDirection = params.get('dir') === 'asc' || params.get('dir') === 'desc' ? (params.get('dir') as SortDirection) : naturalDirection(sort)
  const query = params.get('q') ?? ''

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) {
        next.set(key, value)
      } else {
        next.delete(key)
      }
    }
    setParams(next, { replace: true })
  }

  if (library.isError) {
    return <ErrorNotice error={library.error} onRetry={() => library.refetch()} />
  }
  if (!library.data) {
    return <section aria-busy="true" />
  }

  const all = library.data.filter((item) => item.entry.status === status)
  const items = sortItems(filterItems(all, query), sort, dir)

  return (
    <section className="shelf-page">
      <header className="search-header">
        <p className="kicker">
          <Link to="/" className="series-link">
            Your library
          </Link>
        </p>
        <h1 className="search-title">{STATUS_LABELS[status]}</h1>
        <p className="muted mono">
          {query ? `${items.length} of ${all.length}` : all.length} {all.length === 1 ? 'book' : 'books'}
        </p>
      </header>

      {all.length > 0 && (
        <div className="shelf-toolbar">
          <div className="filter-box">
            <label htmlFor="shelf-filter" className="visually-hidden">
              Filter this shelf
            </label>
            <input
              id="shelf-filter"
              type="search"
              autoComplete="off"
              placeholder="Filter by title, author, or series"
              value={query}
              onChange={(e) => update({ q: e.target.value })}
            />
          </div>
          <Field className="sort-control">
            <Label className="shelf-label">Sort</Label>
            {/* The sort and its direction are joined into one control. */}
            <div className="sort-group">
              <Select<SortKey>
                value={sort}
                // A new sort starts in its natural direction (A–Z, newest first, highest first).
                onChange={(key) => update({ sort: key, dir: naturalDirection(key) })}
                options={options.map((o) => ({ value: o.key, label: o.label }))}
              />
              {/* The arrow alone doesn't say what "up" means for this sort, so the tooltip does. */}
              <Tooltip content={`${directionLabel(sort, dir)}. Click to reverse`}>
                <button
                  type="button"
                  className="sort-direction"
                  aria-label={`${directionLabel(sort, dir)}. Reverse the order`}
                  onClick={() => update({ dir: dir === 'asc' ? 'desc' : 'asc' })}
                >
                  <span aria-hidden="true">{dir === 'asc' ? '↑' : '↓'}</span>
                </button>
              </Tooltip>
            </div>
          </Field>
        </div>
      )}

      {all.length === 0 && <p className="muted">Nothing on this shelf yet.</p>}
      {all.length > 0 && items.length === 0 && <p className="muted">No books on this shelf match “{query}”.</p>}

      <ul className="shelf-grid">
        {items.map((item) => (
          <li key={item.entry.id}>
            <Link to={`/books/${item.book.id}`} className="shelf-grid-book">
              <Cover url={item.book.coverUrl} title={item.book.title} author={item.book.authors[0]} />
              <span className="shelf-grid-title">{item.book.title}</span>
              {item.book.authors.length > 0 && <span className="shelf-grid-author">{listNames(item.book.authors)}</span>}
              {item.entry.rating !== null && <StarDisplay value={item.entry.rating} />}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
