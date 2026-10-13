import { useEffect, useState, type DragEvent } from 'react'
import { Link } from 'react-router'
import { useSession } from '../auth'
import {
  addBrowserBook,
  browserBookCover,
  BrowserBookError,
  byRecent,
  readerPath,
  removeBrowserBook,
  removeOldCopies,
  useBrowserBooks,
  type BrowserBook,
} from '../browserBooks'
import { Cover } from '../components/Cover'
import { LinkBookDialog } from '../components/LinkBookDialog'
import { listNames } from '../format'
import { libraries } from '../libraries'
import { usePageTitle } from '../pageTitle'

/**
 * The EPUBs kept in this browser, to read here: open or drag in a book from the computer, link it to its book in
 * Carrel (so reading it updates the library), and remove it.
 */
export function ReadingPage() {
  usePageTitle('Reading')
  const books = byRecent(useBrowserBooks())
  const signedIn = Boolean(useSession())
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const [linking, setLinking] = useState<BrowserBook | null>(null)
  const [onlineLibraries] = useState(libraries)

  // Copies that newer versions from a library replaced (the reader keeps its own copy of an open book in memory).
  useEffect(() => {
    void removeOldCopies().catch(() => {})
  }, [])

  async function add(files: File[]) {
    if (files.length === 0) return
    setAdding(true)
    setError(null)
    let added: BrowserBook | undefined
    try {
      for (const file of files) added = await addBrowserBook(file)
    } catch (e) {
      setError(e instanceof BrowserBookError ? e.message : 'That book couldn’t be added. Try again.')
    } finally {
      setAdding(false)
    }
    // One book at a time is found in Carrel straight away, as it's added.
    if (signedIn && files.length === 1 && added && added.bookId === null) setLinking(added)
  }

  function drop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    void add([...e.dataTransfer.files])
  }

  return (
    <section
      className={`reading-page${dragging ? ' dragging' : ''}`}
      onDragOver={(e) => {
        e.preventDefault()
        setDragging(true)
      }}
      onDragLeave={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false)
      }}
      onDrop={drop}
    >
      <header className="search-header">
        <p className="kicker">In this browser</p>
        <h1 className="search-title">Reading</h1>
        <p className="muted">
          Read EPUBs from your computer here. They’re kept in this browser, not uploaded to Carrel.
          {signedIn
            ? ' Link a book to Carrel to track your progress and carry on where you left off on your other devices.'
            : ' Sign in to track your progress in your library.'}
        </p>
      </header>

      <div className="file-choice">
        <label className="secondary-button">
          <input
            type="file"
            className="visually-hidden"
            accept=".epub,application/epub+zip"
            multiple
            disabled={adding}
            onChange={(e) => {
              void add([...(e.target.files ?? [])])
              e.target.value = ''
            }}
          />
          {adding ? 'Adding…' : 'Add a book'}
        </label>
        <span className="muted">or drag EPUBs onto this page</span>
      </div>
      {error && (
        <p className="form-message error" role="alert">
          {error}
        </p>
      )}

      {books.length === 0 ? (
        <p className="muted reading-empty">No books in this browser yet.</p>
      ) : (
        <ul className="shelf-grid reading-books">
          {books.map((book) => (
            <BrowserBookItem key={book.id} book={book} signedIn={signedIn} onLink={() => setLinking(book)} />
          ))}
        </ul>
      )}

      <section className="reading-libraries" aria-labelledby="online-libraries">
        <h2 id="online-libraries" className="section-title">
          Online libraries
        </h2>
        <p className="muted">
          Browse an OPDS catalogue, such as Calibre’s content server, and read its books here. It needs an https://
          address that allows Carrel’s website.
        </p>
        {onlineLibraries.length > 0 && (
          <ul className="library-sections">
            {onlineLibraries.map((lib) => (
              // The libraries are browsed on the eReader's page, whose security policy lets it reach them.
              <li key={lib.id}>
                <a href={`/read/?library=${encodeURIComponent(lib.id)}`}>{lib.name} →</a>
              </li>
            ))}
          </ul>
        )}
        <a href="/read/?library=new" className="secondary-button">
          Add a library
        </a>
      </section>

      <LinkBookDialog book={linking} onClose={() => setLinking(null)} />
    </section>
  )
}

/** A book in the list: its cover and title open it in the reader; below, how far through, its Carrel book, and removing it. */
function BrowserBookItem({ book, signedIn, onLink }: { book: BrowserBook; signedIn: boolean; onLink: () => void }) {
  const cover = useBrowserCover(book.id, book.hasCover)
  const [removing, setRemoving] = useState(false)

  return (
    <li className="reading-book">
      {/* The reader is its own page, with its own security policy, so it's a full page load rather than a route. */}
      <a href={readerPath(book)} className="shelf-grid-book">
        <Cover url={book.bookCover ?? cover} title={book.title} author={book.authors[0]} />
        <span className="shelf-grid-title">{book.title}</span>
        {book.authors.length > 0 && <span className="shelf-grid-author">{listNames(book.authors)}</span>}
      </a>
      <span className="mono muted">
        {book.progression === null ? 'Not started' : `${Math.round(book.progression * 100)}% read`}
      </span>
      {removing ? (
        <span className="reading-book-actions">
          <span className="mono">Remove from this browser?</span>
          <button type="button" className="link-button danger" onClick={() => void removeBrowserBook(book.id)}>
            Remove
          </button>
          <button type="button" className="link-button" onClick={() => setRemoving(false)}>
            Keep
          </button>
        </span>
      ) : (
        <span className="reading-book-actions">
          {book.bookId !== null ? (
            <>
              <Link to={`/books/${book.bookId}`} className="link-button">
                In Carrel
              </Link>
              {signedIn && (
                <button type="button" className="link-button" onClick={onLink}>
                  Change
                </button>
              )}
            </>
          ) : (
            signedIn && (
              <button type="button" className="link-button" onClick={onLink}>
                Find in Carrel
              </button>
            )
          )}
          <button type="button" className="link-button" onClick={() => setRemoving(true)}>
            Remove
          </button>
        </span>
      )}
    </li>
  )
}

/** A blob: URL for the book's own cover, while it's shown; null without one. */
function useBrowserCover(id: string, hasCover: boolean): string | null {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    let made: string | null = null
    let cancelled = false
    void browserBookCover({ id, hasCover }).then((file) => {
      if (cancelled || !file) return
      made = URL.createObjectURL(file)
      setUrl(made)
    })
    return () => {
      cancelled = true
      if (made) URL.revokeObjectURL(made)
    }
  }, [id, hasCover])
  return url
}
