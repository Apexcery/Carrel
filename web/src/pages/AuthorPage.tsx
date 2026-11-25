import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { apiGetOrNull } from '../api'
import { AuthorPhoto } from '../components/AuthorPhoto'
import { ErrorNotice } from '../components/ErrorNotice'
import { SuggestionShelf, SuggestionShelfSkeleton } from '../components/SuggestionShelf'
import { usePageTitle } from '../pageTitle'
import type { AuthorDetail } from '../types'
import { NOT_FOUND_TITLE, NotFoundPage } from './NotFoundPage'

/** Enough for any series; the shelf scrolls. */
const SHELF_BOOKS = 50
/** Characters of the bio's first paragraph shown before "Show more". */
const BIO_PREVIEW = 280
/** Series shown as shelves of covers, most read first; the rest are listed by name. */
const FULL_SHELVES = 6

/**
 * An author at /authors/hardcover/<id>: their details beside their bio, then their most read series as shelves, the
 * rest of their series as a list, and the books in no series.
 */
export function AuthorPage() {
  const { hardcoverId } = useParams()
  const author = useQuery({
    queryKey: ['author', hardcoverId],
    queryFn: () => apiGetOrNull<AuthorDetail>(`/authors/hardcover/${hardcoverId}`),
  })
  // Set here for a missing author too, since this runs after NotFoundPage's own.
  usePageTitle(author.data === null ? NOT_FOUND_TITLE : author.data?.name)

  if (author.data === null) {
    return <NotFoundPage />
  }
  if (author.isError) {
    return <ErrorNotice error={author.error} onRetry={() => author.refetch()} />
  }
  if (!author.data) {
    return <AuthorSkeleton />
  }

  const { name, photoUrl, bornYear, deathYear, location, bio, series, standalones } = author.data
  const moreSeries = series.slice(FULL_SHELVES)
  return (
    <section className="author-page">
      <header className="author-header">
        <AuthorPhoto url={photoUrl} size="large" />
        <div className="author-intro">
          <p className="kicker">Author</p>
          <h1 className="series-title">{name}</h1>
          {(bornYear || deathYear || location) && (
            <p className="series-meta mono">{[lifeYears(bornYear, deathYear), location].filter(Boolean).join(' · ')}</p>
          )}
        </div>
        {bio && <Bio text={bio} />}
      </header>

      {series.length === 0 && standalones.length === 0 ? (
        <p className="muted">Hardcover doesn’t list any books by {name} yet.</p>
      ) : (
        <div className="author-columns">
          {series.slice(0, FULL_SHELVES).map((s) => (
            <SuggestionShelf
              key={s.hardcoverId}
              title={s.name}
              titleHref={`/series/hardcover/${s.hardcoverId}`}
              books={s.books}
              limit={SHELF_BOOKS}
            />
          ))}
          {moreSeries.length > 0 && (
            <section className="book-section author-wide">
              <h2 className="section-title">More series</h2>
              <ul className="author-columns author-more-series">
                {moreSeries.map((s) => (
                  <li key={s.hardcoverId}>
                    <Link to={`/series/hardcover/${s.hardcoverId}`} className="author-series-link">
                      <span className="author-series-name">{s.name}</span>
                      <span className="author-series-count mono">
                        {s.books.length} {s.books.length === 1 ? 'book' : 'books'} →
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {standalones.length > 0 && (
            <div className="author-wide">
              <SuggestionShelf title={series.length > 0 ? 'Other books' : 'Books'} books={standalones} limit={SHELF_BOOKS} />
            </div>
          )}
        </div>
      )}
    </section>
  )
}

/** "1903–1950", "Born 1975", or "Died 1950". */
function lifeYears(born: number | null, died: number | null): string | null {
  if (born && died) {
    return `${born}–${died}`
  }
  return born ? `Born ${born}` : died ? `Died ${died}` : null
}

/** The bio's opening, up to BIO_PREVIEW characters of its first paragraph (cut at a word), then the rest on request. */
function Bio({ text }: { text: string }) {
  const [expanded, setExpanded] = useState(false)
  const paragraphs = text.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean)
  const first = paragraphs[0] ?? ''
  const preview = first.length > BIO_PREVIEW ? `${first.slice(0, first.lastIndexOf(' ', BIO_PREVIEW))}…` : first
  const collapsible = paragraphs.length > 1 || preview !== first
  const shown = expanded || !collapsible ? paragraphs : [preview]

  return (
    <section className="description author-bio">
      {shown.map((p, i) => (
        <p key={i}>{p}</p>
      ))}
      {collapsible && (
        <button type="button" className="link-button" onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Show less' : 'Show more'}
        </button>
      )}
      <p className="source-note mono">Biography from Hardcover</p>
    </section>
  )
}

function AuthorSkeleton() {
  return (
    <section className="author-page skeleton" aria-busy="true" aria-label="Loading author">
      <header className="author-header">
        <span className="avatar large" />
        <div className="author-intro">
          <div className="skeleton-line short" />
          <div className="skeleton-line title" />
        </div>
        <div className="author-bio">
          <div className="skeleton-line wide" />
          <div className="skeleton-line wide" />
        </div>
      </header>
      <div className="author-columns">
        {Array.from({ length: 2 }, (_, i) => (
          <SuggestionShelfSkeleton key={i} />
        ))}
      </div>
    </section>
  )
}
