import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { apiGetOrNull } from '../api'
import { ErrorNotice } from '../components/ErrorNotice'
import { GenreLinks } from '../components/GenreLinks'
import { SuggestionShelf, SuggestionShelfSkeleton } from '../components/SuggestionShelf'
import { usePageTitle } from '../pageTitle'
import type { GenreShelves } from '../types'
import { NOT_FOUND_TITLE, NotFoundPage } from './NotFoundPage'

/** Books shown on each of the genre's shelves, and once it's opened up (all the API sends). */
const SHELF_BOOKS = 12
const OPEN_SHELF_BOOKS = 24

/**
 * A genre at /genres/<slug>: its most read books, its best rated, its new releases, and what's coming soon, then the
 * genres that come up most on those books.
 */
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
  const empty =
    shelves &&
    [shelves.popular, shelves.topRated, shelves.newReleases, shelves.comingSoon].every((books) => books.length === 0)
  return (
    <section className="genre-page" aria-busy={!shelves}>
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
        <div className="genre-layout">
          <div className="genre-main">
            {/* The home page's shelf layout: six covers wide, two to a row when both fit. Keyed by genre, so opened
                shelves close on going to another. */}
            <div className="home-shelves" key={slug}>
              {!shelves ? (
                Array.from({ length: 4 }, (_, i) => <SuggestionShelfSkeleton key={i} />)
              ) : (
                <>
                  <SuggestionShelf title="Popular" books={shelves.popular} limit={SHELF_BOOKS} openLimit={OPEN_SHELF_BOOKS} />
                  <SuggestionShelf
                    title="Top rated"
                    books={shelves.topRated}
                    limit={SHELF_BOOKS}
                    openLimit={OPEN_SHELF_BOOKS}
                  />
                  <SuggestionShelf
                    title="New releases"
                    books={shelves.newReleases}
                    limit={SHELF_BOOKS}
                    openLimit={OPEN_SHELF_BOOKS}
                  />
                  <SuggestionShelf
                    title="Coming soon"
                    books={shelves.comingSoon}
                    limit={SHELF_BOOKS}
                    openLimit={OPEN_SHELF_BOOKS}
                    markUpcoming={false}
                  />
                </>
              )}
            </div>
          </div>
          {shelves && shelves.related.length > 0 && (
            <aside className="genre-related" aria-labelledby="related-genres">
              <h2 className="section-title" id="related-genres">
                Related genres
              </h2>
              <GenreLinks genres={shelves.related} />
            </aside>
          )}
        </div>
      )}
    </section>
  )
}
