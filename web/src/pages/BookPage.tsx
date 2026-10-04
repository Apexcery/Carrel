import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { apiGet } from '../api'
import { useSession } from '../auth'
import { Cover } from '../components/Cover'
import { ErrorNotice } from '../components/ErrorNotice'
import { HardcoverRating } from '../components/HardcoverRating'
import { LibraryPanel } from '../components/LibraryPanel'
import { SignInPrompt } from '../components/SignInPrompt'
import { displaySubtitle, formatDate, formatDuration, listNames, otherCredits, seriesPosition } from '../format'
import type { BookDetail, Edition, SeriesEntry } from '../types'

const EDITIONS_SHOWN = 6

export function BookPage() {
  const { id } = useParams()
  const book = useQuery({
    queryKey: ['book', id],
    queryFn: () => apiGet<BookDetail>(`/books/${id}`),
  })

  if (book.isError) {
    return <ErrorNotice error={book.error} onRetry={() => book.refetch()} />
  }
  return book.data ? <BookView book={book.data} /> : <BookSkeleton />
}

function BookView({ book }: { book: BookDetail }) {
  const signedIn = Boolean(useSession())
  const authors = book.authors.filter((a) => a.role === 'author').map((a) => a.name)
  const subtitle = displaySubtitle(book.title, book.subtitle)
  const mainSeries = book.series[0]
  const seriesHref = (s: SeriesEntry) =>
    s.hardcoverId ? `/series/hardcover/${s.hardcoverId}${book.hardcoverId ? `?book=${book.hardcoverId}` : ''}` : null

  return (
    <article className="book">
      <div className="book-cover">
        <Cover url={book.coverUrl} title={book.title} author={authors[0]} size="large" />
      </div>

      <div className="book-main">
        <header className="book-header">
          {mainSeries && (
            <p className="kicker">
              {seriesPosition(mainSeries.position) ? `Book ${seriesPosition(mainSeries.position)} · ` : ''}
              {seriesHref(mainSeries) ? (
                <Link to={seriesHref(mainSeries)!} className="series-link">
                  {mainSeries.name}
                </Link>
              ) : (
                mainSeries.name
              )}
            </p>
          )}
          <h1 className="book-title">{book.title}</h1>
          {subtitle && <p className="book-subtitle">{subtitle}</p>}
          {authors.length > 0 && <p className="book-byline">by {listNames(authors)}</p>}
          {otherCredits(book.authors).map((credit) => (
            <p key={credit} className="book-credit">
              {credit}
            </p>
          ))}
        </header>

        <div className="book-facts">
          <HardcoverRating rating={book.hardcoverRating} count={book.hardcoverRatingsCount} hardcoverId={book.hardcoverId} />
          {book.firstPublishedYear && <span className="mono">First published {book.firstPublishedYear}</span>}
        </div>

        {signedIn ? (
          <LibraryPanel book={book} />
        ) : (
          <SignInPrompt title="Add to your library" panel>Sign in to shelve this book, rate it, and track your progress.</SignInPrompt>
        )}

        {book.genres.length > 0 && (
          <ul className="genres" aria-label="Genres">
            {book.genres.map((genre) => (
              <li key={genre}>{genre}</li>
            ))}
          </ul>
        )}

        {book.description && <Description text={book.description} source={book.descriptionSource} />}

        {book.series.length > 0 && (
          <section className="book-section">
            <h2 className="section-title">{book.series.length === 1 ? 'Series' : 'Series it belongs to'}</h2>
            <ul className="series-list">
              {book.series.map((s) => (
                <li key={s.id}>
                  <span className="mono series-number">{seriesPosition(s.position) ?? '–'}</span>
                  {seriesHref(s) ? (
                    <Link to={seriesHref(s)!} className="series-list-link">
                      {s.name}
                      <span className="mono series-list-cta">See the whole series →</span>
                    </Link>
                  ) : (
                    s.name
                  )}
                </li>
              ))}
            </ul>
          </section>
        )}

        {book.editions.length > 0 && <Editions editions={book.editions} />}
      </div>
    </article>
  )
}

function Description({ text, source }: { text: string; source: BookDetail['descriptionSource'] }) {
  const paragraphs = text.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean)
  const sourceName = { hardcover: 'Hardcover', open_library: 'Open Library', google_books: 'Google Books' }

  return (
    <section className="description">
      {paragraphs.map((p, i) => (
        <p key={i}>{p}</p>
      ))}
      {source && <p className="source-note mono">Description from {sourceName[source]}</p>}
    </section>
  )
}

function Editions({ editions }: { editions: Edition[] }) {
  const [showAll, setShowAll] = useState(false)
  const shown = showAll ? editions : editions.slice(0, EDITIONS_SHOWN)

  return (
    <section className="book-section">
      <h2 className="section-title">Editions</h2>
      <ul className="catalogue">
        {shown.map((edition) => (
          <li key={edition.id} className="catalogue-card">
            <span className="catalogue-format">{formatLabel(edition.format)}</span>
            <span className="catalogue-publisher">{edition.publisher ?? 'Unknown publisher'}</span>
            <span className="catalogue-detail mono">
              {[
                formatDate(edition.releaseDate),
                edition.pageCount ? `${edition.pageCount} pp.` : null,
                edition.audioSeconds ? formatDuration(edition.audioSeconds) : null,
                edition.language?.toUpperCase(),
              ]
                .filter(Boolean)
                .join(' · ')}
            </span>
            {(edition.isbn13 ?? edition.isbn10) && (
              <span className="catalogue-isbn mono">ISBN {edition.isbn13 ?? edition.isbn10}</span>
            )}
          </li>
        ))}
      </ul>
      {editions.length > EDITIONS_SHOWN && (
        <button type="button" className="link-button" onClick={() => setShowAll(!showAll)}>
          {showAll ? 'Show fewer editions' : `Show all ${editions.length} editions`}
        </button>
      )}
    </section>
  )
}

function formatLabel(format: Edition['format']): string {
  switch (format) {
    case 'print':
      return 'Print'
    case 'ebook':
      return 'Ebook'
    case 'audio':
      return 'Audiobook'
    default:
      return 'Edition'
  }
}

export function BookSkeleton() {
  return (
    <article className="book skeleton" aria-busy="true" aria-label="Loading book">
      <div className="book-cover">
        <div className="cover cover-large" />
      </div>
      <div className="book-main">
        <div className="skeleton-line short" />
        <div className="skeleton-line title" />
        <div className="skeleton-line" />
        <div className="skeleton-line wide" />
        <div className="skeleton-line wide" />
      </div>
    </article>
  )
}
