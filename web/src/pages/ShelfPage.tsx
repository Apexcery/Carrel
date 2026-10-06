import { Field, Label } from '@headlessui/react'
import { Link, Navigate, useLocation, useParams, useSearchParams } from 'react-router'
import { Cover } from '../components/Cover'
import { ErrorNotice } from '../components/ErrorNotice'
import { Select } from '../components/Select'
import { StarDisplay } from '../components/StarRating'
import { Tooltip } from '../components/Tooltip'
import { listNames, STATUS_LABELS } from '../format'
import { usePageTitle } from '../pageTitle'
import { useProfile } from '../profile'
import { useReader } from '../readers'
import {
  defaultSort,
  directionLabel,
  filterItems,
  naturalDirection,
  profilePath,
  shelfPath,
  sortItems,
  sortOptions,
  statusFromSlug,
  type SortDirection,
  type SortKey,
} from '../shelves'
import { NOT_FOUND_TITLE, NotFoundPage } from './NotFoundPage'

/** The old address of the signed-in reader's shelves, /shelves/read, now under their profile. */
export function OwnShelfRedirect() {
  const status = statusFromSlug(useParams().slug)
  const { search } = useLocation()
  // Signed in, so the profile has loaded (see App).
  const username = useProfile().data!.username!
  return status ? <Navigate to={`${shelfPath(username, status)}${search}`} replace /> : <NotFoundPage />
}

/**
 * Every book on one of a reader's shelves, e.g. /@reader/shelves/want-to-read, with a filter and sort. Anyone can see
 * a public profile's shelves; a private one's only its reader.
 * The filter, sort and direction live in the URL (?q=&sort=&dir=), so they survive back/forward and bookmarks.
 */
export function ShelfPage() {
  const { handle = '', slug } = useParams()
  const status = statusFromSlug(slug)
  const username = handle.startsWith('@') ? handle.slice(1) : ''
  const [params, setParams] = useSearchParams()
  const { reader, isOwn, notFound, error, refetch } = useReader(username)
  const found = Boolean(status && username && !notFound)
  // Set here for an unknown shelf or reader too, since this runs after NotFoundPage's own.
  usePageTitle(!found ? NOT_FOUND_TITLE : isOwn || !reader ? STATUS_LABELS[status!] : `@${reader.username} · ${STATUS_LABELS[status!]}`)

  if (!status || !found) {
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

  if (error) {
    return <ErrorNotice error={error} onRetry={() => refetch()} />
  }
  if (!reader) {
    return <section aria-busy="true" />
  }

  const all = reader.library.filter((item) => item.entry.status === status)
  const items = sortItems(filterItems(all, query), sort, dir)

  return (
    <section className="shelf-page">
      <header className="search-header">
        <p className="kicker">
          {isOwn ? (
            <Link to="/" className="series-link">
              Your library
            </Link>
          ) : (
            <Link to={profilePath(reader.username)} className="series-link">
              @{reader.username}
            </Link>
          )}
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
