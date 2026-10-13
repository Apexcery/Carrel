import { readerPath, useBrowserBooks } from '../browserBooks'

/** On a book's page, when it's in this browser: a button to read it, and how far through it is. */
export function ReadInBrowser({ bookId }: { bookId: number }) {
  const book = useBrowserBooks().find((b) => b.bookId === bookId)
  if (!book) return null
  return (
    <div className="read-in-browser">
      {/* The reader is its own page, with its own security policy, so it's a full page load rather than a route. */}
      <a href={readerPath(book)} className="primary-button">
        Read
      </a>
      <span className="mono muted">
        In this browser{book.progression !== null ? ` · ${Math.round(book.progression * 100)}%` : ''}
      </span>
    </div>
  )
}
