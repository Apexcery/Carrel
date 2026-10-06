import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router'
import { apiGet } from '../api'
import { Cover } from '../components/Cover'
import { ErrorNotice } from '../components/ErrorNotice'
import { HardcoverRating } from '../components/HardcoverRating'
import { listNames, seriesPosition } from '../format'
import { usePageTitle } from '../pageTitle'
import type { SeriesBook, SeriesDetail } from '../types'

const THIS_YEAR = new Date().getFullYear()

/** A series in reading order. ?book=<hardcoverId> marks the book the reader came from. */
export function SeriesPage() {
  const { hardcoverId } = useParams()
  const [params] = useSearchParams()
  const currentBook = Number(params.get('book')) || null

  const series = useQuery({
    queryKey: ['series', hardcoverId],
    queryFn: () => apiGet<SeriesDetail>(`/series/hardcover/${hardcoverId}`),
  })
  usePageTitle(series.data?.name)

  if (series.isError) {
    return <ErrorNotice error={series.error} onRetry={() => series.refetch()} />
  }
  if (!series.data) {
    return <SeriesSkeleton />
  }

  const { name, author, isCompleted, books, otherBooks } = series.data
  const count = books.length

  return (
    <section className="series">
      <header className="series-header">
        <p className="kicker">Series</p>
        <h1 className="series-title">{name}</h1>
        <p className="series-meta">
          {author && <>by {author}</>}
          <span className="mono">
            {author ? ' · ' : ''}
            {count} {count === 1 ? 'book' : 'books'}
            {isCompleted === true && ' · complete'}
          </span>
        </p>
      </header>

      {books.length === 0 && otherBooks.length === 0 && <p className="muted">Hardcover doesn’t list any books in this series yet.</p>}

      {books.length > 0 && (
        <ol className="series-books" aria-label="Reading order">
          {books.map((book, index) => (
            <SeriesItem key={book.hardcoverId} book={book} index={index} current={book.hardcoverId === currentBook} />
          ))}
        </ol>
      )}

      {otherBooks.length > 0 && (
        <section className="book-section">
          <h2 className="section-title">Also in this series</h2>
          <ol className="series-books series-books-other">
            {otherBooks.map((book, index) => (
              <SeriesItem key={book.hardcoverId} book={book} index={index} current={book.hardcoverId === currentBook} />
            ))}
          </ol>
        </section>
      )}
    </section>
  )
}

function SeriesItem({ book, index, current }: { book: SeriesBook; index: number; current: boolean }) {
  const upcoming = book.releaseYear !== null && book.releaseYear > THIS_YEAR

  return (
    <li className={current ? 'series-item current' : 'series-item'} style={{ animationDelay: `${index * 35}ms` }}>
      <span className="series-item-number" aria-hidden={book.position === null}>
        {seriesPosition(book.position) ?? '·'}
      </span>
      <Link to={`/books/hardcover/${book.hardcoverId}`} className="series-item-link">
        <Cover url={book.coverUrl} title={book.title} author={book.authors[0]} size="small" />
        <div className="series-item-body">
          <h3 className="result-title">{book.title}</h3>
          <p className="result-byline">
            {book.authors.length > 0 && <>by {listNames(book.authors)}</>}
            {book.releaseYear && <span className="mono"> · {book.releaseYear}</span>}
            {upcoming && <span className="badge">Upcoming</span>}
            {current && <span className="badge">You’re here</span>}
          </p>
          <HardcoverRating rating={book.hardcoverRating} count={book.hardcoverRatingsCount} />
        </div>
      </Link>
    </li>
  )
}

function SeriesSkeleton() {
  return (
    <section className="series skeleton" aria-busy="true" aria-label="Loading series">
      <div className="skeleton-line short" />
      <div className="skeleton-line title" />
      {Array.from({ length: 4 }, (_, i) => (
        <div key={i} className="result">
          <div className="cover cover-small" />
          <div className="result-body">
            <div className="skeleton-line wide" />
            <div className="skeleton-line" />
          </div>
        </div>
      ))}
    </section>
  )
}
