import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from '@headlessui/react'
import { apiGet, apiGetOrNull } from '../api'
import { linkBrowserBook, type BrowserBook } from '../browserBooks'
import { listNames } from '../format'
import type { BookDetail, BookSearchResponse, BookSearchResult } from '../types'
import { Cover } from './Cover'
import { ErrorNotice } from './ErrorNotice'

/**
 * Links a book in this browser to its book in Carrel, so reading it updates the library: first the book with one of
 * the file's ISBNs, if Carrel has one, to confirm; otherwise (or if that's not it) a search by its title and author.
 */
export function LinkBookDialog({ book, onClose }: { book: BrowserBook | null; onClose: () => void }) {
  return (
    <Dialog open={book !== null} onClose={onClose} className="release-notes">
      <DialogBackdrop className="image-viewer-backdrop" />
      <div className="image-viewer-frame">
        <DialogPanel className="release-notes-panel">
          <div className="release-notes-header">
            <DialogTitle className="release-notes-title">Find this book in Carrel</DialogTitle>
            <button type="button" className="link-button" onClick={onClose}>
              Close
            </button>
          </div>
          {/* Keyed so each book starts afresh. */}
          {book && <LinkSteps key={book.id} book={book} onLinked={onClose} />}
        </DialogPanel>
      </div>
    </Dialog>
  )
}

function LinkSteps({ book, onLinked }: { book: BrowserBook; onLinked: () => void }) {
  const [searching, setSearching] = useState(book.isbns.length === 0)
  const [linking, setLinking] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function link(path: string) {
    setLinking(true)
    setError(null)
    try {
      const detail = await apiGet<BookDetail>(path)
      linkBrowserBook(book.id, detail.id, detail.coverUrl)
      onLinked()
    } catch (e) {
      setError(e)
    } finally {
      setLinking(false)
    }
  }

  return (
    <>
      <p className="muted">
        Reading <em>{book.title}</em> then moves it to Reading in your library, keeps your progress, and carries on where
        you left off on your other devices.
      </p>
      {error !== null && <ErrorNotice error={error} />}
      {searching ? (
        <LinkSearch book={book} linking={linking} onPick={link} />
      ) : (
        <IsbnMatch book={book} linking={linking} onLink={link} onSearch={() => setSearching(true)} />
      )}
    </>
  )
}

/** The book in Carrel with one of the file's ISBNs, to confirm; straight on to searching if there isn't one. */
function IsbnMatch({
  book,
  linking,
  onLink,
  onSearch,
}: {
  book: BrowserBook
  linking: boolean
  onLink: (path: string) => void
  onSearch: () => void
}) {
  const match = useQuery({
    queryKey: ['isbn-match', book.isbns],
    queryFn: async () => {
      for (const isbn of book.isbns) {
        const found = await apiGetOrNull<BookDetail>(`/books/isbn/${isbn}`)
        if (found) return found
      }
      return null
    },
  })

  if (match.isPending) return <p className="muted mono">Looking for its ISBN…</p>
  if (match.isError) return <ErrorNotice error={match.error} onRetry={() => match.refetch()} />
  const found = match.data
  if (!found) {
    return <LinkSearch book={book} linking={linking} onPick={onLink} />
  }
  const authors = found.authors.filter((a) => a.role === 'author').map((a) => a.name)
  return (
    <div className="link-match">
      <Cover url={found.coverUrl} title={found.title} author={authors[0]} size="small" />
      <div className="link-match-text">
        <p>Is this the book?</p>
        <p className="link-result-title">{found.title}</p>
        {authors.length > 0 && <p className="muted">{listNames(authors)}</p>}
        <div className="link-match-actions">
          <button type="button" className="primary-button" disabled={linking} onClick={() => onLink(`/books/${found.id}`)}>
            {linking ? 'Linking…' : 'Link this book'}
          </button>
          <button type="button" className="link-button" disabled={linking} onClick={onSearch}>
            Not this one
          </button>
        </div>
      </div>
    </div>
  )
}

/** A search for the book, starting with its title and first author; choosing a result links it. */
function LinkSearch({ book, linking, onPick }: { book: BrowserBook; linking: boolean; onPick: (path: string) => void }) {
  const start = [book.title, book.authors[0]].filter(Boolean).join(' ')
  const [text, setText] = useState(start)
  const [query, setQuery] = useState(start)
  const results = useQuery({
    queryKey: ['search', query, 1],
    queryFn: () => apiGet<BookSearchResponse>(`/books/search?q=${encodeURIComponent(query)}&page=1`),
    enabled: query.trim().length > 0,
  })

  function search(e: FormEvent) {
    e.preventDefault()
    setQuery(text.trim())
  }

  const path = (result: BookSearchResult) =>
    result.hardcoverId !== null ? `/books/hardcover/${result.hardcoverId}` : `/books/openlibrary/${result.openLibraryWorkId}`

  return (
    <>
      <form className="link-search" onSubmit={search}>
        <label htmlFor="link-search" className="visually-hidden">
          Search for the book
        </label>
        <input id="link-search" type="search" autoComplete="off" value={text} onChange={(e) => setText(e.target.value)} />
        <button type="submit" className="secondary-button">
          Search
        </button>
      </form>
      {results.isPending && query && <p className="muted mono">Searching…</p>}
      {results.isError && <ErrorNotice error={results.error} onRetry={() => results.refetch()} />}
      {results.data?.results.length === 0 && <p className="muted">Nothing found. Try other words.</p>}
      {results.data && results.data.results.length > 0 && (
        <ul className="link-results">
          {results.data.results.slice(0, 10).map((result) => (
            <li key={`${result.hardcoverId}:${result.openLibraryWorkId}`}>
              <button type="button" className="link-result" disabled={linking} onClick={() => onPick(path(result))}>
                <Cover url={result.coverUrl} title={result.title} author={result.authors[0]} size="small" />
                <span>
                  <span className="link-result-title">{result.title}</span>
                  <span className="muted">
                    {[listNames(result.authors), result.releaseYear].filter(Boolean).join(' · ')}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
