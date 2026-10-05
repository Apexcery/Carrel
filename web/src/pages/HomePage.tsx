import { useEffect, useRef, useState, type RefObject } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { apiGet } from '../api'
import { useSession } from '../auth'
import { Cover } from '../components/Cover'
import { ErrorNotice } from '../components/ErrorNotice'
import { ShelfRow } from '../components/ShelfRow'
import { SignInPrompt } from '../components/SignInPrompt'
import { StarDisplay } from '../components/StarRating'
import { SuggestionShelf, SuggestionShelfSkeleton, useOwnedHardcoverIds } from '../components/SuggestionShelf'
import { YearInBooks } from '../components/YearInBooks'
import { listNames, STATUS_LABELS } from '../format'
import { shelfItems, shelfPath } from '../shelves'
import type { BookSuggestion, DiscoverShelves, GenrePicks, LibraryItem, ReadingStatus, RelatedBooks } from '../types'

const SHELVES: ReadingStatus[] = ['want_to_read', 'read', 'did_not_finish']
/** Books shown on each home-page shelf; the shelf title links to the full shelf. */
const SHELF_BOOKS = 9
/** Books shown in each row of suggestions. */
const SUGGESTIONS_SHOWN = 12
/** The lowest rating that counts as liking a book. */
const FAVOURITE_RATING = 4
/** Ratings below this mean the reader didn't like the book, so it isn't used for suggestions. */
const DISLIKED_BELOW = 3
/** Authors tried in turn for "More by", while the reader has everything by the ones before. */
const FAVOURITE_AUTHORS = 3
/** Gap above the library column while it stays in view; matches .stays-in-view in styles.css. */
const STAYS_IN_VIEW_MARGIN = 24

const TABS = [
  { id: 'for-you', label: 'For you' },
  { id: 'discover', label: 'Discover' },
] as const
type Tab = (typeof TABS)[number]['id']

/**
 * The main column suggests books: signed in, in two tabs (For you, from the reader's library, and Discover, the same
 * for everyone); signed out, just Discover. The reader's library sits on the right.
 */
export function HomePage() {
  const signedIn = Boolean(useSession())
  const [params] = useSearchParams()
  const home = useHomeData(signedIn, params.get('tab'))
  const library = useRef<HTMLElement>(null)
  const fits = useFitsInWindow(library)
  return (
    <div className="home-layout">
      <section className="home-main" aria-busy={!home.ready}>
        <h1 className="home-title">What are you reading?</h1>
        <p className="home-lede">Search above by title, author, or ISBN to find a book, its editions, and the series it belongs to.</p>
        {signedIn && (
          <nav className="home-tabs" aria-label="Suggestions">
            {TABS.map(({ id, label }) => (
              <Link key={id} to={`?tab=${id}`} aria-current={home.tab === id ? 'page' : undefined}>
                {label}
              </Link>
            ))}
          </nav>
        )}
        {!home.ready ? (
          <div className="home-shelves">
            {Array.from({ length: 4 }, (_, i) => (
              <SuggestionShelfSkeleton key={i} />
            ))}
          </div>
        ) : home.tab === 'for-you' ? (
          <ForYou next={home.next} moreBy={home.moreBy} because={home.because} genre={home.genre} />
        ) : (
          home.discover && <Discover shelves={home.discover} />
        )}
      </section>
      {/* Stays in view while the suggestions scroll past, if it fits in the window (else its end would be unreachable). */}
      <aside ref={library} className={fits ? 'home-library stays-in-view' : 'home-library'} aria-label="Your library">
        {!signedIn ? (
          <SignInPrompt title="Your library">
            Sign in to keep track of what you’re reading, what you’ve read, and what you want to read next.
          </SignInPrompt>
        ) : home.ready ? (
          <Library />
        ) : (
          <LibrarySkeleton />
        )}
      </aside>
    </div>
  )
}

function ForYou({ next, moreBy, because, genre }: Pick<HomeData, 'next' | 'moreBy' | 'because' | 'genre'>) {
  if (!next?.length && !moreBy?.books.length && !because?.books.length && !genre?.books.length) {
    return (
      <p className="muted home-tab-empty">
        Shelve and finish a few books, and suggestions based on them will show up here. Until then, have a look in{' '}
        <Link to="?tab=discover">Discover</Link>.
      </p>
    )
  }
  return (
    <div className="home-shelves">
      {next && <SuggestionShelf title="Next in your series" books={next} limit={SUGGESTIONS_SHOWN} />}
      {moreBy && <SuggestionShelf title={`More by ${moreBy.author}`} books={moreBy.books} limit={SUGGESTIONS_SHOWN} />}
      {because && (
        <SuggestionShelf
          title={`Because you ${because.liked ? 'liked' : 'read'} ${because.title}`}
          books={because.books}
          limit={SUGGESTIONS_SHOWN}
        />
      )}
      {genre && <SuggestionShelf title={`Popular in ${genre.genre}`} books={genre.books} limit={SUGGESTIONS_SHOWN} />}
    </div>
  )
}

function Discover({ shelves }: { shelves: DiscoverShelves }) {
  return (
    <div className="home-shelves">
      <SuggestionShelf title="Top rated" books={shelves.topRated} limit={SUGGESTIONS_SHOWN} />
      <SuggestionShelf title="New releases" books={shelves.newReleases} limit={SUGGESTIONS_SHOWN} />
      <SuggestionShelf title="Popular this month" books={shelves.popular} limit={SUGGESTIONS_SHOWN} />
      <SuggestionShelf title="Coming soon" books={shelves.comingSoon} limit={SUGGESTIONS_SHOWN} markUpcoming={false} />
    </div>
  )
}

/** Whether the element is short enough to fit in the window with a margin, kept up to date as either changes size. */
function useFitsInWindow(ref: RefObject<HTMLElement | null>) {
  const [fits, setFits] = useState(false)
  useEffect(() => {
    const el = ref.current
    if (!el) {
      return
    }
    const measure = () => setFits(el.offsetHeight + 2 * STAYS_IN_VIEW_MARGIN <= window.innerHeight)
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(el)
    window.addEventListener('resize', measure)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', measure)
    }
  }, [ref])
  return fits
}

type HomeData = ReturnType<typeof useHomeData>

/**
 * Everything the home page loads: the reader's library and the open tab's suggestions (other tabs load when opened).
 * The page waits for all of it so the shelves appear together rather than one by one. A shelf that fails or has
 * nothing to suggest is left out.
 */
function useHomeData(signedIn: boolean, requestedTab: string | null) {
  const library = useQuery({ queryKey: ['library'], queryFn: () => apiGet<LibraryItem[]>('/library'), enabled: signedIn })
  // Signed out there's only Discover. A reader with nothing on their shelves has nothing to base For you on, so they
  // start on Discover too (once the library has loaded and shown that).
  const tab: Tab = !signedIn
    ? 'discover'
    : (TABS.find((t) => t.id === requestedTab)?.id ?? (library.data?.length === 0 ? 'discover' : 'for-you'))

  // Under 'library', so it refreshes whenever the reader's shelves change.
  const next = useQuery({
    queryKey: ['library', 'next-in-series'],
    queryFn: () => apiGet<BookSuggestion[]>('/library/next-in-series'),
    enabled: signedIn && tab === 'for-you',
  })
  const basis = library.data && suggestionBasis(library.data)
  const related = useQuery({
    queryKey: ['related', basis?.item.book.id],
    queryFn: () => apiGet<RelatedBooks>(`/books/${basis!.item.book.id}/related`),
    enabled: Boolean(basis) && tab === 'for-you',
  })
  const owned = useOwnedHardcoverIds()
  // The reader's favourite author, or if they've shelved everything by them, the next favourite, up to three. Each is
  // only asked for once the one before has nothing left. Often the same book as above, so the same request.
  const authorBooks = library.data ? favouriteAuthorBooks(library.data) : []
  const firstAuthor = useMoreByAuthor(authorBooks[0], tab === 'for-you', owned)
  const secondAuthor = useMoreByAuthor(authorBooks[1], tab === 'for-you' && firstAuthor.exhausted, owned)
  const thirdAuthor = useMoreByAuthor(authorBooks[2], tab === 'for-you' && secondAuthor.exhausted, owned)
  // Under 'library', so it refreshes whenever the reader's shelves change.
  const genrePicks = useQuery({
    queryKey: ['library', 'genre-picks'],
    queryFn: () => apiGet<GenrePicks>('/library/genre-picks'),
    enabled: signedIn && tab === 'for-you',
  })
  const discover = useQuery({
    queryKey: ['discover'],
    queryFn: () => apiGet<DiscoverShelves>('/discover'),
    enabled: tab === 'discover',
  })
  const authors = [firstAuthor, secondAuthor, thirdAuthor]

  return {
    tab,
    ready: [library, ...(tab === 'for-you' ? [next, related, ...authors.map((a) => a.query), genrePicks] : [discover])].every(
      settled,
    ),
    next: next.data,
    moreBy: authors.find((a) => a.shelf)?.shelf,
    because:
      basis && related.data && owned
        ? {
            title: basis.item.book.title,
            liked: basis.liked,
            books: related.data.similar.filter((b) => !owned.has(b.hardcoverId)),
          }
        : undefined,
    genre:
      genrePicks.data?.genre && owned
        ? { genre: genrePicks.data.genre, books: genrePicks.data.books.filter((b) => !owned.has(b.hardcoverId)) }
        : undefined,
    discover: discover.data,
  }
}

/**
 * The book to base "Because you…" suggestions on: the most recently finished book the reader rated 4 stars or more,
 * else (for readers who don't rate) the most recently finished book they didn't rate below 3.
 */
function suggestionBasis(items: LibraryItem[]): { item: LibraryItem; liked: boolean } | undefined {
  // Reads come newest first; books without a finish date fall back to when the entry last changed.
  const finished = (item: LibraryItem) => item.entry.reads[0]?.finishedOn ?? item.entry.updatedAt
  const read = items
    .filter((item) => item.entry.status === 'read')
    .sort((a, b) => finished(b).localeCompare(finished(a)))
  const favourite = read.find((item) => (item.entry.rating ?? 0) >= FAVOURITE_RATING)
  if (favourite) {
    return { item: favourite, liked: true }
  }
  const latest = read.find((item) => item.entry.rating === null || item.entry.rating >= DISLIKED_BELOW)
  return latest && { item: latest, liked: false }
}

/** Finished, failed, or not needed (a query that isn't enabled sits pending but idle). */
function settled(query: { isPending: boolean; fetchStatus: string }) {
  return !query.isPending || query.fetchStatus === 'idle'
}

/**
 * More by the author of this library book, leaving out books the reader has. Exhausted when there's nothing to show:
 * no such book, the request failed, or the reader already has everything listed.
 */
function useMoreByAuthor(item: LibraryItem | undefined, enabled: boolean, owned: Set<number> | null) {
  const query = useQuery({
    queryKey: ['related', item?.book.id],
    queryFn: () => apiGet<RelatedBooks>(`/books/${item!.book.id}/related`),
    enabled: enabled && Boolean(item),
  })
  const books = query.data && owned ? query.data.byAuthor.filter((b) => !owned.has(b.hardcoverId)) : undefined
  const author = query.data?.author
  return {
    query,
    shelf: author && books && books.length > 0 ? { author, books } : undefined,
    exhausted: !item || query.isError || (query.isSuccess && (!author || books?.length === 0)),
  }
}

/**
 * Books by the reader's favourite authors, one each, best first, to find more by them: authors ranked by books read or
 * being read (ties go to the higher average rating), each with the book the reader finished or changed most recently.
 */
function favouriteAuthorBooks(items: LibraryItem[]): LibraryItem[] {
  const byAuthor = new Map<string, LibraryItem[]>()
  for (const item of items) {
    const author = item.book.authors[0]
    if (author && (item.entry.status === 'read' || item.entry.status === 'reading')) {
      byAuthor.set(author, [...(byAuthor.get(author) ?? []), item])
    }
  }
  const averageRating = (books: LibraryItem[]) => {
    const ratings = books.flatMap((b) => b.entry.rating ?? [])
    return ratings.length > 0 ? ratings.reduce((sum, r) => sum + r, 0) / ratings.length : 0
  }
  const latest = (item: LibraryItem) => item.entry.reads[0]?.finishedOn ?? item.entry.updatedAt
  return [...byAuthor.values()]
    .sort((a, b) => b.length - a.length || averageRating(b) - averageRating(a))
    .slice(0, FAVOURITE_AUTHORS)
    .map((books) => books.sort((a, b) => latest(b).localeCompare(latest(a)))[0])
}

function LibrarySkeleton() {
  return (
    <div className="skeleton" aria-hidden="true">
      <div className="skeleton-line title" />
      <div className="skeleton-line short" />
      <SuggestionShelfSkeleton />
    </div>
  )
}

function Library() {
  const library = useQuery({ queryKey: ['library'], queryFn: () => apiGet<LibraryItem[]>('/library') })

  if (library.isError) {
    return <ErrorNotice error={library.error} onRetry={() => library.refetch()} />
  }
  if (!library.data) {
    return <div aria-busy="true" />
  }

  const reading = shelfItems(library.data, 'reading')

  return (
    <>
      <header className="library-header">
        <h2 className="library-title">Your library</h2>
        <p className="muted mono">
          {library.data.length} {library.data.length === 1 ? 'book' : 'books'}
        </p>
      </header>

      {library.data.length === 0 && (
        <p className="muted">Nothing on your shelves yet. Books you add to your library will appear here.</p>
      )}

      {reading.length > 0 && (
        <section className="shelf">
          <ShelfTitle status="reading" count={reading.length} />
          <ul className="reading-now">
            {reading.map((item) => (
              <li key={item.entry.id}>
                <Link to={`/books/${item.book.id}`} className="reading-card">
                  <Cover url={item.book.coverUrl} title={item.book.title} author={item.book.authors[0]} size="small" />
                  <div>
                    <p className="reading-card-title">{item.book.title}</p>
                    {item.book.authors.length > 0 && <p className="reading-card-byline">{listNames(item.book.authors)}</p>}
                    <div className="progress-bar" aria-hidden="true">
                      <span style={{ width: `${item.entry.progressPercent ?? 0}%` }} />
                    </div>
                    <p className="progress-summary mono">
                      {item.entry.progressPercent === null ? 'Just started' : `${Math.round(item.entry.progressPercent)}%`}
                    </p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {SHELVES.map((status) => {
        const items = shelfItems(library.data, status)
        return (
          items.length > 0 && (
            <section key={status} className="shelf">
              <ShelfRow heading={<ShelfTitle status={status} count={items.length} />}>
                {items.slice(0, SHELF_BOOKS).map((item) => (
                  <li key={item.entry.id}>
                    <Link to={`/books/${item.book.id}`} className="shelf-book" title={item.book.title}>
                      <Cover url={item.book.coverUrl} title={item.book.title} author={item.book.authors[0]} />
                      {item.entry.rating !== null && <StarDisplay value={item.entry.rating} />}
                    </Link>
                  </li>
                ))}
              </ShelfRow>
            </section>
          )
        )
      })}

      {library.data.length > 0 && <YearInBooks items={library.data} />}
    </>
  )
}

function ShelfTitle({ status, count }: { status: ReadingStatus; count: number }) {
  return (
    <h3 className="section-title">
      <Link to={shelfPath(status)} className="shelf-title-link">
        {STATUS_LABELS[status]} ({count})
      </Link>
    </h3>
  )
}
