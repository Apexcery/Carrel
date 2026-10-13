import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from '@headlessui/react'
import { addDownloadedBook, BrowserBookError, fromLibrary, readerPath, type BrowserBook } from '../browserBooks'
import { Cover } from '../components/Cover'
import { LinkBookDialog } from '../components/LinkBookDialog'
import { listNames } from '../format'
import {
  downloadBook,
  library as findLibrary,
  libraryPicture,
  LibraryError,
  loadFeed,
  password,
  passwordRemembered,
  removeLibrary,
  saveLibrary,
  searchUrl,
  setPassword,
  type Library,
  type OnlineBook,
  type OnlineFeed,
} from '../libraries'
import { supabase } from '../supabase'

/** An address on this page: a library's catalogue at `url` (its start without one), or its details form. */
function libraryPath(id: string, options: { url?: string; edit?: boolean } = {}) {
  const params = new URLSearchParams({ library: id })
  if (options.url) params.set('url', options.url)
  if (options.edit) params.set('edit', '1')
  return `/read/?${params}`
}

/**
 * An online library (/read/?library=…), on the eReader's page since only its security policy lets the page reach
 * other servers: its catalogue, or the form to add one (`new`) or change one (`edit`).
 */
export function LibraryPage() {
  const [params] = useState(() => new URLSearchParams(window.location.search))
  const id = params.get('library') ?? ''
  const [lib] = useState(() => (id === 'new' ? null : findLibrary(id)))

  useEffect(() => {
    document.title = id === 'new' ? 'Carrel | Add a library' : lib ? `Carrel | ${lib.name}` : 'Carrel'
  }, [id, lib])

  if (id !== 'new' && !lib) {
    return (
      <div className="reader-message">
        <p>That library isn’t in this browser.</p>
        <a href="/reading" className="link-button">
          Your books
        </a>
      </div>
    )
  }
  return (
    <div className="library-page">
      <p className="kicker">
        <a href="/reading" className="kicker-link">
          Reading
        </a>
      </p>
      {!lib || params.get('edit') ? <LibraryForm lib={lib ?? null} /> : <Catalogue lib={lib} url={params.get('url') ?? lib.url} />}
    </div>
  )
}

/**
 * Adding an online library (`lib` null) or changing or removing one. Saving checks the catalogue answers with these
 * details first.
 */
function LibraryForm({ lib }: { lib: Library | null }) {
  const [name, setName] = useState(lib?.name ?? '')
  const [url, setUrl] = useState(lib?.url ?? '')
  const [username, setUsername] = useState(lib?.username ?? '')
  const [pass, setPass] = useState(lib ? (password(lib.id) ?? '') : '')
  const [remember, setRemember] = useState(lib ? passwordRemembered(lib.id) : false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [removing, setRemoving] = useState(false)

  async function save(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const saved = await saveLibrary(lib?.id ?? null, { name, url, username: username || null, password: pass || null, remember })
      window.location.assign(libraryPath(saved.id))
    } catch (err) {
      setError(err instanceof LibraryError ? err.message : 'Something went wrong. Try again.')
      setBusy(false)
    }
  }

  return (
    <>
      <h1 className="search-title">{lib ? 'Change this library' : 'Add a library'}</h1>
      <p className="muted library-intro">
        An OPDS catalogue, such as Calibre’s content server. The website reaches it straight from this browser, so it
        needs an https:// address and has to allow Carrel’s website (CORS). Its details are kept in this browser.
      </p>
      <form className="settings-form library-form" onSubmit={save}>
        <label>
          <span>Name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="My books" autoComplete="off" />
        </label>
        <label>
          <span>Address</span>
          <input
            type="url"
            required
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://books.example.com/opds"
            autoComplete="off"
          />
        </label>
        <label>
          <span>Username (if it needs one)</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="off" />
        </label>
        <label>
          <span>Password</span>
          <input type="password" value={pass} onChange={(e) => setPass(e.target.value)} autoComplete="off" />
        </label>
        <RememberBox checked={remember} onChange={setRemember} />
        {error && (
          <p className="form-message error" role="alert">
            {error}
          </p>
        )}
        <div className="library-form-actions">
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Checking…' : lib ? 'Save' : 'Add library'}
          </button>
          <a href={lib ? libraryPath(lib.id) : '/reading'} className="link-button">
            Cancel
          </a>
        </div>
      </form>
      {lib && (
        <p className="library-remove">
          {removing ? (
            <>
              <span className="mono">Remove this library? Books downloaded from it stay in this browser.</span>{' '}
              <button
                type="button"
                className="link-button danger"
                onClick={() => {
                  removeLibrary(lib.id)
                  window.location.assign('/reading')
                }}
              >
                Remove
              </button>{' '}
              <button type="button" className="link-button" onClick={() => setRemoving(false)}>
                Keep
              </button>
            </>
          ) : (
            <button type="button" className="link-button" onClick={() => setRemoving(true)}>
              Remove this library
            </button>
          )}
        </p>
      )}
    </>
  )
}

function RememberBox({ checked, onChange }: { checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <div className="remember-box">
      <label>
        <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
        <span>Remember the password on this browser</span>
      </label>
      <p className="muted">
        Otherwise it’s kept until you close this tab. Only remember it on a computer you trust.
      </p>
    </div>
  )
}

/** A library's catalogue at `url`: its sections, its books, more of a list, and searching it. */
function Catalogue({ lib, url }: { lib: Library; url: string }) {
  const first = useQuery({ queryKey: ['opds', lib.id, url], queryFn: () => loadFeed(lib, url), retry: false })
  const [more, setMore] = useState<OnlineFeed[]>([])
  const [loadingMore, setLoadingMore] = useState(false)
  const [moreError, setMoreError] = useState<string | null>(null)
  const [words, setWords] = useState('')
  const [chosen, setChosen] = useState<OnlineBook | null>(null)
  const atStart = url === lib.url

  if (first.isPending) return <p className="muted mono">Loading…</p>
  if (first.isError) {
    const error = first.error
    if (error instanceof LibraryError && error.needsLogin) {
      return <LoginForm lib={lib} message={error.message} onDone={() => void first.refetch()} />
    }
    return (
      <div className="notice" role="alert">
        <p>{error instanceof LibraryError ? error.message : 'Something went wrong. Try again.'}</p>
        <button type="button" className="link-button" onClick={() => void first.refetch()}>
          Try again
        </button>
        <a href={libraryPath(lib.id, { edit: true })} className="link-button">
          Change this library’s details
        </a>
      </div>
    )
  }

  const feeds = [first.data, ...more]
  const sections = feeds.flatMap((f) => f.sections)
  const books = feeds.flatMap((f) => f.books)
  const next = feeds[feeds.length - 1].next

  async function loadMore() {
    if (!next) return
    setLoadingMore(true)
    setMoreError(null)
    try {
      const page = await loadFeed(lib, next)
      setMore((pages) => [...pages, page])
    } catch (e) {
      setMoreError(e instanceof LibraryError ? e.message : 'Something went wrong. Try again.')
    } finally {
      setLoadingMore(false)
    }
  }

  function search(e: FormEvent) {
    e.preventDefault()
    const target = words.trim() && searchUrl(first.data!, words)
    if (target) window.location.assign(libraryPath(lib.id, { url: target }))
  }

  return (
    <>
      <h1 className="search-title">{atStart ? lib.name : first.data.title}</h1>
      <p className="library-links">
        {!atStart && (
          <a href={libraryPath(lib.id)} className="link-button">
            {lib.name}
          </a>
        )}
        {atStart && (
          <a href={libraryPath(lib.id, { edit: true })} className="link-button">
            Change this library’s details
          </a>
        )}
      </p>

      {first.data.search && (
        <form className="link-search library-search" onSubmit={search}>
          <label htmlFor="library-search" className="visually-hidden">
            Search this library
          </label>
          <input
            id="library-search"
            type="search"
            placeholder="Search this library"
            autoComplete="off"
            value={words}
            onChange={(e) => setWords(e.target.value)}
          />
          <button type="submit" className="secondary-button">
            Search
          </button>
        </form>
      )}

      {sections.length > 0 && (
        <ul className="library-sections">
          {sections.map((section) => (
            <li key={section.url}>
              <a href={libraryPath(lib.id, { url: section.url })}>{section.title} →</a>
            </li>
          ))}
        </ul>
      )}

      {books.length > 0 && (
        <ul className="link-results library-books">
          {books.map((book) => (
            <li key={book.id}>
              <button type="button" className="link-result" onClick={() => setChosen(book)}>
                <LibraryCover lib={lib} url={book.thumbnail} title={book.title} author={book.authors[0]} size="small" />
                <span>
                  <span className="link-result-title">{book.title}</span>
                  {book.authors.length > 0 && <span className="muted">{listNames(book.authors)}</span>}
                  {fromLibrary(lib.id, book.id) && <span className="mono muted">In this browser</span>}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {sections.length === 0 && books.length === 0 && <p className="muted">No books here.</p>}
      {moreError && (
        <p className="form-message error" role="alert">
          {moreError}
        </p>
      )}
      {next && (
        <button type="button" className="secondary-button library-more" disabled={loadingMore} onClick={() => void loadMore()}>
          {loadingMore ? 'Loading…' : 'Load more'}
        </button>
      )}

      <BookDialog lib={lib} book={chosen} onClose={() => setChosen(null)} />
    </>
  )
}

/** Asks for the library's password, for a library that wants one and has none kept (or turned it down). */
function LoginForm({ lib, message, onDone }: { lib: Library; message: string; onDone: () => void }) {
  const [pass, setPass] = useState('')
  const [remember, setRemember] = useState(passwordRemembered(lib.id))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await loadFeed(lib, lib.url, pass)
      setPassword(lib.id, pass, remember)
      onDone()
    } catch (err) {
      setError(err instanceof LibraryError ? err.message : 'Something went wrong. Try again.')
      setBusy(false)
    }
  }

  return (
    <>
      <h1 className="search-title">{lib.name}</h1>
      <p className="muted library-intro">{message}</p>
      {lib.username ? (
        <form className="settings-form library-form" onSubmit={submit}>
          <label>
            <span>Password for {lib.username}</span>
            <input type="password" required autoFocus value={pass} onChange={(e) => setPass(e.target.value)} autoComplete="off" />
          </label>
          <RememberBox checked={remember} onChange={setRemember} />
          {error && (
            <p className="form-message error" role="alert">
              {error}
            </p>
          )}
          <div className="library-form-actions">
            <button type="submit" className="primary-button" disabled={busy}>
              {busy ? 'Checking…' : 'Open library'}
            </button>
          </div>
        </form>
      ) : (
        <a href={libraryPath(lib.id, { edit: true })} className="link-button">
          Add a username and password
        </a>
      )}
    </>
  )
}

/**
 * A cover from the library, fetched with its login (a picture on the page couldn't send it), once it's near the
 * screen: a library's server can be slow to make a thumbnail the first time, so the ones in view come first.
 */
function LibraryCover({ lib, url, ...props }: { lib: Library; url: string | null; title: string; author?: string; size?: 'small' | 'medium' }) {
  const box = useRef<HTMLSpanElement>(null)
  const [near, setNear] = useState(false)
  const [src, setSrc] = useState<string | null>(null)
  useEffect(() => {
    const el = box.current
    if (!el || near) return
    const observer = new IntersectionObserver((seen) => {
      if (seen.some((s) => s.isIntersecting)) setNear(true)
    }, { rootMargin: '300px' })
    observer.observe(el)
    return () => observer.disconnect()
  }, [near])
  useEffect(() => {
    if (!url || !near) return
    let made: string | null = null
    let cancelled = false
    libraryPicture(lib, url)
      .then((blob) => {
        if (cancelled) return
        made = URL.createObjectURL(blob)
        setSrc(made)
      })
      .catch(() => {})
    return () => {
      cancelled = true
      if (made) URL.revokeObjectURL(made)
    }
  }, [lib, url, near])
  return (
    <span ref={box} className="library-cover">
      <Cover url={src} {...props} />
    </span>
  )
}

/**
 * A book in the library: its cover, details, and summary, and reading it. The first time, it's downloaded into this
 * browser (then offered a link to its book in Carrel); after that, opening it fetches any newer version.
 */
function BookDialog({ lib, book, onClose }: { lib: Library; book: OnlineBook | null; onClose: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [linking, setLinking] = useState<BrowserBook | null>(null)
  const copy = book ? fromLibrary(lib.id, book.id) : undefined

  async function download() {
    if (!book?.epub) return
    setBusy(true)
    setError(null)
    try {
      const fetched = await downloadBook(lib, book.epub)
      if (fetched.unchanged) throw new LibraryError('The library didn’t send the book. Try again.')
      const added = await addDownloadedBook(fetched.file, {
        libraryId: lib.id,
        onlineId: book.id,
        downloadUrl: book.epub,
        etag: fetched.etag,
        lastModified: fetched.lastModified,
      })
      const { data } = await supabase.auth.getSession()
      // Found in Carrel straight away when signed in, as on Android, then read.
      if (data.session && added.bookId === null) setLinking(added)
      else window.location.assign(readerPath(added))
    } catch (e) {
      setError(e instanceof LibraryError || e instanceof BrowserBookError ? e.message : 'The book couldn’t be downloaded. Try again.')
      setBusy(false)
    }
  }

  return (
    <>
      <Dialog open={book !== null && linking === null} onClose={onClose} className="release-notes">
        <DialogBackdrop className="image-viewer-backdrop" />
        <div className="image-viewer-frame">
          <DialogPanel className="release-notes-panel">
            {book && (
              <>
                <div className="release-notes-header">
                  <span />
                  <button type="button" className="link-button" onClick={onClose}>
                    Close
                  </button>
                </div>
                <div className="link-match">
                  <LibraryCover lib={lib} url={book.cover} title={book.title} author={book.authors[0]} size="small" />
                  <div className="link-match-text">
                    <DialogTitle className="release-notes-title">{book.title}</DialogTitle>
                    {book.authors.length > 0 && <p className="muted">{listNames(book.authors)}</p>}
                  </div>
                </div>
                {copy ? (
                  <a href={readerPath(copy)} className="primary-button library-read">
                    Read
                  </a>
                ) : book.epub ? (
                  <button type="button" className="primary-button library-read" disabled={busy} onClick={() => void download()}>
                    {busy ? 'Downloading…' : 'Download and read'}
                  </button>
                ) : (
                  <p className="muted">This book isn’t available as an EPUB, so Carrel can’t read it.</p>
                )}
                {error && (
                  <p className="form-message error" role="alert">
                    {error}
                  </p>
                )}
                {book.summary?.split('\n\n').map((paragraph, i) => (
                  <p key={i} className="library-summary">
                    {paragraph}
                  </p>
                ))}
              </>
            )}
          </DialogPanel>
        </div>
      </Dialog>
      <LinkBookDialog
        book={linking}
        onClose={() => {
          if (linking) window.location.assign(readerPath(linking))
        }}
      />
    </>
  )
}
