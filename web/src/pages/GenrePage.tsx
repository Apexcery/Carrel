import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { apiGetOrNull } from '../api'
import { ErrorNotice } from '../components/ErrorNotice'
import { SuggestionShelf, SuggestionShelfSkeleton } from '../components/SuggestionShelf'
import { usePageTitle } from '../pageTitle'
import type { GenreShelves } from '../types'
import { NOT_FOUND_TITLE, NotFoundPage } from './NotFoundPage'

/** Books shown on each of the genre's shelves. */
const SHELF_BOOKS = 12

/** A genre at /genres/<slug>: its most read books, its best rated, and its new releases. */
export function GenrePage() {
  const { slug } = useParams()
  const genre = useQuery({
    queryKey: ['genre', slug],
    queryFn: () => apiGetOrNull<GenreShelves>(`/genres/${slug}`),
  })
  // Set here for a missing genre too, since this runs after NotFoundPage's own.
  usePageTitle(genre.data === null ? NOT_FOUND_TITLE : genre.data?.name)

  if (genre.data === null) {
    return <NotFoundPage />
  }
  if (genre.isError) {
    return <ErrorNotice error={genre.error} onRetry={() => genre.refetch()} />
  }

  const shelves = genre.data
  const empty = shelves && [shelves.popular, shelves.topRated, shelves.newReleases].every((books) => books.length === 0)
  return (
    <section aria-busy={!shelves}>
      <header className="series-header">
        <p className="kicker">
          <Link to="/genres" className="kicker-link">
            Genres
          </Link>
        </p>
        {shelves ? <h1 className="series-title">{shelves.name}</h1> : <div className="skeleton-line title" />}
      </header>

      {empty ? (
        <p className="muted">Hardcover doesn’t have enough books in this genre to show yet.</p>
      ) : (
        // The home page's shelf layout: six covers wide.
        <div className="home-shelves">
          {!shelves ? (
            Array.from({ length: 3 }, (_, i) => <SuggestionShelfSkeleton key={i} />)
          ) : (
            <>
              <SuggestionShelf title="Popular" books={shelves.popular} limit={SHELF_BOOKS} />
              <SuggestionShelf title="Top rated" books={shelves.topRated} limit={SHELF_BOOKS} />
              <SuggestionShelf title="New releases" books={shelves.newReleases} limit={SHELF_BOOKS} />
            </>
          )}
        </div>
      )}
    </section>
  )
}
