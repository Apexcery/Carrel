import { useState, type FormEvent, type ReactNode } from 'react'
import { Field, Label } from '@headlessui/react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { apiSend } from '../api'
import { Cover } from '../components/Cover'
import { ErrorNotice } from '../components/ErrorNotice'
import { Select } from '../components/Select'
import { StarDisplay } from '../components/StarRating'
import { Tooltip } from '../components/Tooltip'
import { CUSTOM_SHELVES_KEY, storeCustomShelf } from '../customShelves'
import { listNames, STATUS_LABELS } from '../format'
import { usePageTitle } from '../pageTitle'
import { useProfile } from '../profile'
import { useReader } from '../readers'
import {
  defaultSort,
  directionLabel,
  filterItems,
  naturalDirection,
  onCustomShelf,
  profilePath,
  shelfPath,
  sortItems,
  sortOptions,
  statusFromSlug,
  type ShelfItem,
  type SortDirection,
  type SortKey,
} from '../shelves'
import type { CustomShelf, Reader, ReadingStatus } from '../types'
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
 */
export function ShelfPage() {
  const { handle = '', slug } = useParams()
  const status = statusFromSlug(slug)
  const username = handle.startsWith('@') ? handle.slice(1) : ''
  const { reader, isOwn, notFound, error, refetch } = useReader(username)
  const found = Boolean(status && username && !notFound)
  // Set here for an unknown shelf or reader too, since this runs after NotFoundPage's own.
  usePageTitle(!found ? NOT_FOUND_TITLE : isOwn || !reader ? STATUS_LABELS[status!] : `@${reader.username} · ${STATUS_LABELS[status!]}`)

  if (!status || !found) {
    return <NotFoundPage />
  }
  if (error) {
    return <ErrorNotice error={error} onRetry={() => refetch()} />
  }
  if (!reader) {
    return <section aria-busy="true" />
  }
  return (
    <ShelfBooks
      reader={reader}
      isOwn={isOwn}
      title={STATUS_LABELS[status]}
      status={status}
      all={reader.library.filter((item) => item.entry.status === status)}
    />
  )
}

/**
 * Every book on one of a reader's custom shelves, e.g. /@reader/shelves/custom/12, as on a status shelf. Others only
 * find a public shelf on a public profile; the reader can also rename theirs, make it public or private, or delete it.
 */
export function CustomShelfPage() {
  const { handle = '', id } = useParams()
  const username = handle.startsWith('@') ? handle.slice(1) : ''
  const { reader, isOwn, notFound, error, refetch } = useReader(username)
  const shelf = reader?.shelves.find((s) => String(s.id) === id)
  // Whether the shelf exists is only known once the reader has loaded.
  const missing = !username || notFound || (reader !== undefined && !shelf)
  usePageTitle(missing ? NOT_FOUND_TITLE : shelf && (isOwn ? shelf.name : `@${reader!.username} · ${shelf.name}`))

  if (missing) {
    return <NotFoundPage />
  }
  if (error) {
    return <ErrorNotice error={error} onRetry={() => refetch()} />
  }
  if (!reader || !shelf) {
    return <section aria-busy="true" />
  }
  return (
    <ShelfBooks reader={reader} isOwn={isOwn} title={shelf.name} status={null} all={onCustomShelf(reader.library, shelf)}>
      {isOwn && <ShelfSettings key={shelf.id} shelf={shelf} username={reader.username} />}
    </ShelfBooks>
  )
}

/**
 * A shelf's books, `all` of them, with the filter and sort; `status` is null for a custom shelf. The filter, sort, and
 * direction live in the URL (?q=&sort=&dir=), so they survive back/forward and bookmarks. `children` go under the title.
 */
function ShelfBooks({
  reader,
  isOwn,
  title,
  status,
  all,
  children,
}: {
  reader: Reader
  isOwn: boolean
  title: string
  status: ReadingStatus | null
  all: ShelfItem[]
  children?: ReactNode
}) {
  const [params, setParams] = useSearchParams()
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

  const items = sortItems(filterItems(all, query), sort, dir)

  return (
    <section className="shelf-page">
      <header className="search-header">
        <p className="kicker">
          {isOwn ? (
            <Link to="/" className="series-link">
              {/* A custom shelf can hold books that aren't in the library. */}
              {status ? 'Your library' : 'Your shelves'}
            </Link>
          ) : (
            <Link to={profilePath(reader.username)} className="series-link">
              @{reader.username}
            </Link>
          )}
        </p>
        <h1 className="search-title">{title}</h1>
        <p className="muted mono">
          {query ? `${items.length} of ${all.length}` : all.length} {all.length === 1 ? 'book' : 'books'}
        </p>
        {children}
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

      {all.length === 0 && (
        <p className="muted">
          Nothing on this shelf yet.{status === null && isOwn && ' Put books on it from their pages.'}
        </p>
      )}
      {all.length > 0 && items.length === 0 && <p className="muted">No books on this shelf match “{query}”.</p>}

      <ul className="shelf-grid">
        {items.map((item) => (
          <li key={item.book.id}>
            <Link to={`/books/${item.book.id}`} className="shelf-grid-book">
              <Cover url={item.book.coverUrl} title={item.book.title} author={item.book.authors[0]} />
              <span className="shelf-grid-title">{item.book.title}</span>
              {item.book.authors.length > 0 && <span className="shelf-grid-author">{listNames(item.book.authors)}</span>}
              {item.entry?.rating != null && <StarDisplay value={item.entry.rating} />}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** The reader's own custom shelf: whether it's public, with ways to switch that, rename it, or delete it. */
function ShelfSettings({ shelf, username }: { shelf: CustomShelf; username: string }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [renaming, setRenaming] = useState(false)
  const [name, setName] = useState(shelf.name)

  // Renaming can change the shelves' A–Z order, so they're fetched again.
  const save = useMutation({
    mutationFn: (changes: { name?: string; isPublic?: boolean }) =>
      apiSend<CustomShelf>('PUT', `/shelves/${shelf.id}`, { name: shelf.name, isPublic: shelf.isPublic, ...changes }),
    onSuccess: (saved) => {
      storeCustomShelf(queryClient, saved)
      queryClient.invalidateQueries({ queryKey: CUSTOM_SHELVES_KEY })
      setRenaming(false)
    },
  })

  // Off to the profile first, so this page doesn't find its shelf gone.
  const remove = useMutation({
    mutationFn: () => apiSend<void>('DELETE', `/shelves/${shelf.id}`),
    onSuccess: () => {
      navigate(profilePath(username), { replace: true })
      queryClient.invalidateQueries({ queryKey: CUSTOM_SHELVES_KEY })
    },
  })

  const busy = save.isPending || remove.isPending

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate({ name })
  }

  return (
    <div className="custom-shelf-settings">
      {renaming ? (
        <form className="shelf-name-form" onSubmit={submit}>
          <label htmlFor="shelf-name" className="visually-hidden">
            Shelf name
          </label>
          <input id="shelf-name" value={name} maxLength={50} autoComplete="off" onChange={(e) => setName(e.target.value)} />
          <button type="submit" className="primary-button" disabled={busy}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
          <button
            type="button"
            className="link-button"
            onClick={() => {
              save.reset()
              setName(shelf.name)
              setRenaming(false)
            }}
          >
            Cancel
          </button>
        </form>
      ) : (
        <p className="custom-shelf-actions">
          <span className="muted">{shelf.isPublic ? 'Public: shows on your profile.' : 'Private: only you can see it.'}</span>
          <button type="button" className="link-button" disabled={busy} onClick={() => save.mutate({ isPublic: !shelf.isPublic })}>
            {shelf.isPublic ? 'Make private' : 'Make public'}
          </button>
          <button type="button" className="link-button" disabled={busy} onClick={() => setRenaming(true)}>
            Rename
          </button>
          <button
            type="button"
            className="link-button danger"
            disabled={busy}
            onClick={() => {
              if (window.confirm(`Delete “${shelf.name}”? Its books stay in your library and on your other shelves.`)) {
                remove.mutate()
              }
            }}
          >
            Delete shelf
          </button>
        </p>
      )}
      {(save.error ?? remove.error) && <p className="form-message error">{(save.error ?? remove.error)!.message}</p>}
    </div>
  )
}
