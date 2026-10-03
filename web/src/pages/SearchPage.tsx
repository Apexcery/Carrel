import type { CSSProperties } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { apiGet } from '../api'
import { Cover } from '../components/Cover'
import { ErrorNotice } from '../components/ErrorNotice'
import { HardcoverRating } from '../components/HardcoverRating'
import { displaySubtitle, listNames, seriesPosition } from '../format'
import type { BookSearchResponse, BookSearchResult } from '../types'

const PAGE_SIZE = 20
const MAX_PAGE = 50

export function SearchPage() {
  const [params] = useSearchParams()
  const q = (params.get('q') ?? '').trim()

  const search = useInfiniteQuery({
    queryKey: ['search', q],
    queryFn: ({ pageParam }) =>
      apiGet<BookSearchResponse>(`/books/search?q=${encodeURIComponent(q)}&page=${pageParam}`),
    initialPageParam: 1,
    getNextPageParam: (last, pages, lastPage) =>
      last.results.length === PAGE_SIZE && pages.length * PAGE_SIZE < last.found && lastPage < MAX_PAGE
        ? lastPage + 1
        : undefined,
    enabled: q.length > 0,
  })

  if (!q) {
    return <p className="muted">Type something to search for.</p>
  }

  const results = search.data?.pages.flatMap((p) => p.results) ?? []
  const found = search.data?.pages[0]?.found ?? 0

  return (
    <section className="search">
      <header className="search-header">
        <p className="kicker">Search</p>
        <h1 className="search-title">“{q}”</h1>
        {search.isSuccess && (
          <p className="muted mono">
            {found === 0 ? 'No books found' : `${found.toLocaleString()} ${found === 1 ? 'book' : 'books'}`}
          </p>
        )}
      </header>

      {search.isPending && <ResultSkeleton />}
      {search.isError && <ErrorNotice error={search.error} onRetry={() => search.refetch()} />}

      <ol className="results">
        {results.map((result, index) => (
          <li key={result.hardcoverId ?? result.openLibraryWorkId} style={{ '--i': index % PAGE_SIZE } as CSSProperties}>
            <ResultCard result={result} />
          </li>
        ))}
      </ol>

      {search.hasNextPage && (
        <button
          type="button"
          className="more-button"
          onClick={() => search.fetchNextPage()}
          disabled={search.isFetchingNextPage}
        >
          {search.isFetchingNextPage ? 'Loading…' : 'More results'}
        </button>
      )}
      {search.isFetchNextPageError && <ErrorNotice error={search.error} />}
    </section>
  )
}

function ResultCard({ result }: { result: BookSearchResult }) {
  const href = result.hardcoverId
    ? `/books/hardcover/${result.hardcoverId}`
    : `/books/openlibrary/${result.openLibraryWorkId}`
  const subtitle = displaySubtitle(result.title, result.subtitle)
  const position = seriesPosition(result.seriesPosition)

  return (
    <Link to={href} className="result">
      <Cover url={result.coverUrl} title={result.title} author={result.authors[0]} size="small" />
      <div className="result-body">
        {result.seriesName && (
          <p className="result-series mono">
            {position ? `Book ${position} · ` : ''}
            {result.seriesName}
          </p>
        )}
        <h2 className="result-title">{result.title}</h2>
        {subtitle && <p className="result-subtitle">{subtitle}</p>}
        <p className="result-byline">
          {result.authors.length > 0 && <>by {listNames(result.authors.slice(0, 3))}</>}
          {result.releaseYear && <span className="mono"> · {result.releaseYear}</span>}
        </p>
        <HardcoverRating rating={result.hardcoverRating} count={result.hardcoverRatingsCount} />
      </div>
    </Link>
  )
}

function ResultSkeleton() {
  return (
    <ol className="results" aria-busy="true" aria-label="Loading results">
      {Array.from({ length: 4 }, (_, i) => (
        <li key={i} className="result skeleton">
          <div className="cover cover-small" />
          <div className="result-body">
            <div className="skeleton-line wide" />
            <div className="skeleton-line" />
          </div>
        </li>
      ))}
    </ol>
  )
}
