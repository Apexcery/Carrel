import { Fragment, useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from '@headlessui/react'
import { addDownloadedBook, BrowserBookError, fromLibrary, readerPath, type BrowserBook } from '../browserBooks'
import { Cover } from '../components/Cover'
import { LinkBookDialog } from '../components/LinkBookDialog'
import { ShelfRow } from '../components/ShelfRow'
import { listNames } from '../format'
import {
  downloadBook,
  library as findLibrary,
  libraryPicture,
  LibraryError,
  loadFeed,
  newestSection,
  password,
  passwordRemembered,
  removeLibrary,
  saveLibrary,
  searchUrl,
  setPassword,
  type Library,
  type OnlineBook,
  type Section,
} from '../libraries'
import { supabase } from '../supabase'

/** An address on this page: a library's catalogue at `url` (its start without one), or its details form. */
function libraryPath(id: string, options: { url?: string; edit?: boolean } = {}) {
  const params = new URLSearchParams({ library: id })
  if (options.url) params.set('url', options.url)
  if (options.edit) params.set('edit', '1')
  return `/read/?${params}`
}

/**
 * An online library (/read/?library=…), on the eReader's page since only its security policy lets the page reach
 * other servers: its catalogue, or the form to add one (`new`) or change one (`edit`).
 */
export function LibraryPage() {
  const [params] = useState(() => new URLSearchParams(window.location.search))
  const id = params.get('library') ?? ''
  const [lib] = useState(() => (id === 'new' ? null : findLibrary(id)))

  const editing = !lib || params.get('edit') !== null

  // The catalogue names its own place.
  useEffect(() => {
    if (editing) document.title = id === 'new' ? 'Carrel | Add a library' : lib ? `Carrel | ${lib.name}` : 'Carrel'
  }, [editing, id, lib])

  if (id !== 'new' && !lib) {
    return (
      <div className="reader-message">
        <p>That library isn’t in this browser.</p>
        <a href="/reading" className="link-button">
          Your books
        </a>
      </div>
    )
  }
  return (
    <div className="library-page">
      {/* The eReader's page has no site header (it's a page of its own), so this leads back to Carrel and the books here. */}
      <header className="library-masthead">
        <a href="/" className="wordmark" aria-label="Carrel home">
          Carrel
        </a>
        <a href="/reading" className="link-button">
          Your books
        </a>
      </header>
      {editing || !lib ? <LibraryForm lib={lib ?? null} /> : <Catalogue lib={lib} start={params.get('url') ?? lib.url} />}
    </div>
  )
}

/**
 * Adding an online library (`lib` null) or changing or removing one. Saving checks the catalogue answers with these
 * details first.
 */
function LibraryForm({ lib }: { lib: Library | null }) {
  const [name, setName] = useState(lib?.name ?? '')
  const [url, setUrl] = useState(lib?.url ?? '')
  const [username, setUsername] = useState(lib?.username ?? '')
  const [pass, setPass] = useState(lib ? (password(lib.id) ?? '') : '')
  const [remember, setRemember] = useState(lib ? passwordRemembered(lib.id) : false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [removing, setRemoving] = useState(false)

  async function save(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const saved = await saveLibrary(lib?.id ?? null, { name, url, username: username || null, password: pass || null, remember })
      window.location.assign(libraryPath(saved.id))
    } catch (err) {
      setError(err instanceof LibraryError ? err.message : 'Something went wrong. Try again.')
      setBusy(false)
    }
  }

  return (
    <>
      <h1 className="search-title">{lib ? 'Change this library' : 'Add a library'}</h1>
      <p className="muted library-intro">
        An OPDS catalogue, such as Calibre’s content server. The website reaches it straight from this browser, so it
        needs an https:// address and has to allow Carrel’s website (CORS). Its details are kept in this browser.
      </p>
      <form className="settings-form library-form" onSubmit={save}>
        <label>
          <span>Name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="My books" autoComplete="off" />
        </label>
        <label>
          <span>Address</span>
          <input
            type="url"
            required
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://books.example.com/opds"
            autoComplete="off"
          />
        </label>
        <label>
          <span>Username (if it needs one)</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="off" />
        </label>
        <label>
          <span>Password</span>
          <input type="password" value={pass} onChange={(e) => setPass(e.target.value)} autoComplete="off" />
        </label>
        <RememberBox checked={remember} onChange={setRemember} />
        {error && (
          <p className="form-message error" role="alert">
            {error}
          </p>
        )}
        <div className="library-form-actions">
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'Checking…' : lib ? 'Save' : 'Add library'}
          </button>
          <a href={lib ? libraryPath(lib.id) : '/reading'} className="link-button">
            Cancel
          </a>
        </div>
      </form>
      {lib && (
        <p className="library-remove">
          {removing ? (
            <>
              <span className="mono">Remove this library? Books downloaded from it stay in this browser.</span>{' '}
              <button
                type="button"
                className="link-button danger"
                onClick={() => {
                  removeLibrary(lib.id)
                  window.location.assign('/reading')
                }}
              >
                Remove
              </button>{' '}
              <button type="button" className="link-button" onClick={() => setRemoving(false)}>
                Keep
              </button>
            </>
          ) : (
            <button type="button" className="link-button" onClick={() => setRemoving(true)}>
              Remove this library
            </button>
          )}
        </p>
      )}
    </>
  )
}

function RememberBox({ checked, onChange }: { checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <div className="remember-box">
      <label>
        <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
        <span>Remember the password on this browser</span>
      </label>
      <p className="muted">
        Otherwise it’s kept until you close this tab. Only remember it on a computer you trust.
      </p>
    </div>
  )
}

/** A place in a library's catalogue: its address, and the steps taken from the start to get there. */
interface Place {
  url: string
  trail: Step[]
}

interface Step {
  title: string
  url: string
}

/** A place as kept in its history entry, with how much of it was loaded and how far down it the reader was. */
interface SavedPlace extends Place {
  pages: number
  scrollY: number
}

/** Pages of a list of sections fetched straight away, so its A–Z and filter cover it all; past that, scrolling loads more. */
const WHOLE_LIST_PAGES = 20
/** How long a catalogue page fetched in this tab is used again (going back to it, say) rather than fetched afresh. */
const FRESH_MS = 5 * 60_000
/** A list of sections this long gets an A–Z and a filter. */
const LONG_LIST = 30
/** More sections than this at the start are listed there, rather than as tabs. */
const MAX_TABS = 12
const SHELF_BOOKS = 20

function savedPlace(state: unknown): SavedPlace | null {
  const saved = state as Partial<SavedPlace> | null
  return saved && typeof saved.url === 'string' && Array.isArray(saved.trail) && typeof saved.pages === 'number' && typeof saved.scrollY === 'number'
    ? (saved as SavedPlace)
    : null
}

/** A catalogue page and the pages after it, loaded as they're wanted. */
function useFeed(lib: Library, url: string | undefined) {
  return useInfiniteQuery({
    queryKey: ['opds', lib.id, url],
    queryFn: ({ pageParam }) => loadFeed(lib, pageParam),
    initialPageParam: url ?? '',
    getNextPageParam: (last, _pages, lastUrl) => (last.next && last.next !== lastUrl ? last.next : undefined),
    enabled: url !== undefined,
    staleTime: FRESH_MS,
    retry: false,
  })
}

/** A plain click, which the page handles; others (a new tab, say) are left to the browser. */
function plainClick(e: MouseEvent) {
  return e.button === 0 && !e.metaKey && !e.ctrlKey && !e.shiftKey && !e.altKey
}

/**
 * A library's catalogue: its top sections as tabs, the way back up from where the reader is, and the sections and
 * books there. Moving around stays on the page, with each place in the browser's history, so Back (in the page, or
 * from reading a book) returns to the same place, as far down it as before.
 */
function Catalogue({ lib, start }: { lib: Library; start: string }) {
  // Opened from the history (Back, or a reload), it goes back to where the reader was.
  const [opened] = useState(() => savedPlace(window.history.state))
  /** Where the reader was in the place they've come back to, until the page gets there. */
  const restoring = useRef(opened)
  const [place, setPlace] = useState<Place>(opened ?? { url: start, trail: start === lib.url ? [] : [{ title: '', url: start }] })
  const heading = useRef<HTMLHeadingElement>(null)
  const moved = useRef(false)
  const [words, setWords] = useState('')
  const [chosen, setChosen] = useState<OnlineBook | null>(null)

  const root = useFeed(lib, lib.url)
  const current = useFeed(lib, place.url)
  const atStart = place.url === lib.url
  const rootSections = (root.data?.pages[0].sections ?? []).filter(browsable)
  const tabs = rootSections.length <= MAX_TABS ? rootSections : []
  const newest = atStart ? newestSection(tabs) : undefined
  const shelf = useFeed(lib, newest?.url)
  const pages = current.data?.pages ?? []
  const pagesLoaded = pages.length
  const sections = pages.flatMap((p) => p.sections).filter(browsable)
  const books = pages.flatMap((p) => p.books)
  const here = place.trail[place.trail.length - 1]
  // A place opened by its address (in a new tab, say) has no steps to it, so it's named by its tab or its page.
  const title = atStart ? lib.name : here?.title || rootSections.find((s) => s.url === place.url)?.title || pages[0]?.title || lib.name
  // A reading list comes sorted by title, but its order can be in each book's details: put it back in that order.
  const order = atStart ? null : listOrder(books, title)
  const shownBooks = order
    ? books
        .map((book, i) => ({ book, at: order[i], i }))
        .toSorted((a, b) => a.at - b.at || a.i - b.i)
        .map(({ book }) => book)
    : books
  // The whole library's search, from its start.
  const searchFeed = root.data?.pages[0].search ? root.data.pages[0] : pages[0]?.search ? pages[0] : null
  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = current

  useEffect(() => {
    document.title = atStart ? `Carrel | ${lib.name}` : `Carrel | ${title} · ${lib.name}`
  }, [atStart, title, lib.name])

  // The page keeps its place itself: the browser's own would scroll before the books are back.
  useEffect(() => {
    window.history.scrollRestoration = 'manual'
    const popped = (event: PopStateEvent) => {
      const saved = savedPlace(event.state)
      restoring.current = saved
      moved.current = true
      setPlace(saved ?? { url: new URLSearchParams(window.location.search).get('url') ?? lib.url, trail: [] })
    }
    window.addEventListener('popstate', popped)
    return () => window.removeEventListener('popstate', popped)
  }, [lib.url])

  // Keeps this place's history entry up to date as the reader moves, scrolls, and loads more, and as the page is left.
  const latest = useRef({ place, pagesLoaded })
  const save = useCallback(() => {
    if (restoring.current) return
    const saved: SavedPlace = { ...latest.current.place, pages: Math.max(1, latest.current.pagesLoaded), scrollY: window.scrollY }
    window.history.replaceState(saved, '')
  }, [])
  useEffect(() => {
    latest.current = { place, pagesLoaded }
    save()
  }, [place, pagesLoaded, save])
  useEffect(() => {
    let timer = 0
    const scrolled = () => {
      window.clearTimeout(timer)
      timer = window.setTimeout(save, 200)
    }
    window.addEventListener('scroll', scrolled, { passive: true })
    window.addEventListener('pagehide', save)
    return () => {
      window.clearTimeout(timer)
      window.removeEventListener('scroll', scrolled)
      window.removeEventListener('pagehide', save)
    }
  }, [save])

  // Coming back: loads as many pages as before, then scrolls to where the reader was.
  useEffect(() => {
    const target = restoring.current
    if (!target || target.url !== place.url || !current.data) return
    if (current.data.pages.length < target.pages && hasNextPage && !isFetchNextPageError) {
      void fetchNextPage({ cancelRefetch: false })
      return
    }
    restoring.current = null
    window.scrollTo(0, target.scrollY)
  }, [place.url, current.data, hasNextPage, isFetchNextPageError, fetchNextPage])

  // After a move, the heading has focus (once it's there), as after a page load.
  useEffect(() => {
    if (!moved.current || !heading.current) return
    moved.current = false
    heading.current.focus({ preventScroll: true })
  }, [place, current.isPending])

  // A list of sections is loaded whole (up to a point), so its A–Z and filter see all of it, and so is a reading list,
  // so it can be put in order.
  const wholeList = current.data !== undefined && ((books.length === 0 && sections.length > 0) || order !== null)
  useEffect(() => {
    if (wholeList && hasNextPage && !isFetchingNextPage && !isFetchNextPageError && pages.length < WHOLE_LIST_PAGES) {
      void fetchNextPage({ cancelRefetch: false })
    }
  }, [wholeList, hasNextPage, isFetchingNextPage, isFetchNextPageError, pages.length, fetchNextPage])

  const loadMore = useCallback(() => void fetchNextPage({ cancelRefetch: false }), [fetchNextPage])

  function go(next: Place) {
    save()
    const saved: SavedPlace = { ...next, pages: 1, scrollY: 0 }
    window.history.pushState(saved, '', libraryPath(lib.id, { url: next.url === lib.url ? undefined : next.url }))
    restoring.current = null
    moved.current = true
    setPlace(next)
    window.scrollTo(0, 0)
  }

  /** A link to another place in the catalogue, opened in the page (or, by the browser, in a new tab). */
  function link(to: Place, children: ReactNode, props: { className?: string; current?: boolean | 'page'; id?: string; title?: string } = {}) {
    return (
      <a
        href={libraryPath(lib.id, { url: to.url === lib.url ? undefined : to.url })}
        className={props.className}
        id={props.id}
        title={props.title}
        aria-current={props.current || undefined}
        onClick={(e) => {
          if (!plainClick(e.nativeEvent)) return
          e.preventDefault()
          go(to)
        }}
      >
        {children}
      </a>
    )
  }

  /** A link into a section from here. */
  const into = (section: Section, children: ReactNode, props: { className?: string; id?: string; title?: string } = {}) =>
    link({ url: section.url, trail: [...place.trail, { title: section.title, url: section.url }] }, children, props)

  function submitSearch(e: FormEvent) {
    e.preventDefault()
    const target = words.trim() && searchFeed && searchUrl(searchFeed, words)
    if (target) go({ url: target, trail: [{ title: `Search: ${words.trim()}`, url: target }] })
  }

  // Without the start (for the tabs), the rest still works; the place itself has to load. A failure loading more
  // keeps what's there.
  const error = current.data ? null : current.error
  if (current.isPending && !error) return <p className="muted mono">Loading…</p>
  if (error) {
    if (error instanceof LibraryError && error.needsLogin) {
      return (
        <LoginForm
          lib={lib}
          message={error.message}
          onDone={() => {
            void root.refetch()
            void current.refetch()
          }}
        />
      )
    }
    return (
      <div className="notice" role="alert">
        <p>{error instanceof LibraryError ? error.message : 'Something went wrong. Try again.'}</p>
        <button
          type="button"
          className="link-button"
          onClick={() => {
            void root.refetch()
            void current.refetch()
          }}
        >
          Try again
        </button>
        <a href={libraryPath(lib.id, { edit: true })} className="link-button">
          Change this library’s details
        </a>
      </div>
    )
  }

  // At the start, the sections are the tabs above (unless there are too many for tabs).
  const listed = atStart && tabs.length > 0 ? [] : sections
  const moreError: unknown = current.error
  const letters = listed.length > 0 && listed.every((s) => [...s.title.trim()].length === 1)

  return (
    <>
      <div className="catalogue-head">
        <h1 ref={atStart ? heading : undefined} tabIndex={-1} className="search-title">
          {lib.name}
        </h1>
        <a href={libraryPath(lib.id, { edit: true })} className="link-button">
          Change this library’s details
        </a>
      </div>

      {searchFeed && (
        <form className="link-search library-search" onSubmit={submitSearch}>
          <label htmlFor="library-search" className="visually-hidden">
            Search this library
          </label>
          <input
            id="library-search"
            type="search"
            placeholder="Search this library"
            autoComplete="off"
            value={words}
            onChange={(e) => setWords(e.target.value)}
          />
          <button type="submit" className="secondary-button">
            Search
          </button>
        </form>
      )}

      {tabs.length > 0 && (
        <nav className="catalogue-tabs" aria-label="Sections">
          {tabs.map((tab) => {
            const active = place.trail[0]?.url === tab.url
            return (
              <Fragment key={tab.url}>
                {link({ url: tab.url, trail: [{ title: tab.title, url: tab.url }] }, tab.title, {
                  className: 'catalogue-tab',
                  current: active && (place.trail.length === 1 ? 'page' : true),
                })}
              </Fragment>
            )
          })}
        </nav>
      )}

      {!atStart && (
        <>
          <nav className="catalogue-trail" aria-label="The way back">
            <ol>
              <li>{link({ url: lib.url, trail: [] }, lib.name)}</li>
              {place.trail.slice(0, -1).map((step, i) => (
                <li key={step.url}>{link({ url: step.url, trail: place.trail.slice(0, i + 1) }, step.title)}</li>
              ))}
            </ol>
          </nav>
          <h2 ref={heading} tabIndex={-1} className="catalogue-title">
            {title}
          </h2>
        </>
      )}

      {atStart && newest && shelf.data && shelf.data.pages[0].books.length > 0 && (
        <section className="catalogue-shelf">
          <ShelfRow
            heading={
              <h3 className="section-title">
                {newest.title}{' '}
                {into(newest, 'See all →', { className: 'catalogue-see-all' })}
              </h3>
            }
          >
            {shelf.data.pages[0].books.slice(0, SHELF_BOOKS).map((book) => (
              <li key={book.id}>
                <button type="button" className="shelf-book catalogue-book" aria-label={book.title} onClick={() => setChosen(book)}>
                  <LibraryCover lib={lib} url={book.thumbnail} title={book.title} author={book.authors[0]} />
                </button>
              </li>
            ))}
          </ShelfRow>
        </section>
      )}
      {atStart && !newest && books.length === 0 && listed.length === 0 && <p className="muted">Choose a section to browse.</p>}

      {letters ? (
        <ul className="catalogue-letters">
          {listed.map((section) => (
            <li key={section.url}>
              {into(
                section,
                <>
                  <span className="catalogue-letter">{section.title}</span>
                  {section.summary && <span className="mono muted">{section.summary}</span>}
                </>,
              )}
            </li>
          ))}
        </ul>
      ) : (
        listed.length > 0 && <SectionList sections={listed} partial={hasNextPage} into={into} />
      )}

      {books.length > 0 && (
        <ul className="shelf-grid catalogue-books">
          {shownBooks.map((book) => (
            <li key={book.id}>
              <button type="button" className="shelf-grid-book catalogue-book" onClick={() => setChosen(book)}>
                <LibraryCover lib={lib} url={book.thumbnail} title={book.title} author={book.authors[0]} />
                <span className="shelf-grid-title">{book.title}</span>
                {book.authors.length > 0 && <span className="shelf-grid-author">{listNames(book.authors)}</span>}
                {fromLibrary(lib.id, book.id) && <span className="mono muted">In this browser</span>}
              </button>
            </li>
          ))}
        </ul>
      )}

      {!atStart && sections.length === 0 && books.length === 0 && <p className="muted">No books here.</p>}
      {isFetchNextPageError ? (
        <p className="form-message error" role="alert">
          {moreError instanceof LibraryError ? moreError.message : 'More couldn’t be loaded.'}{' '}
          <button type="button" className="link-button" onClick={() => void fetchNextPage({ cancelRefetch: false })}>
            Try again
          </button>
        </p>
      ) : (
        hasNextPage && <MoreOnScroll pages={pages.length} loading={isFetchingNextPage} onMore={loadMore} />
      )}

      <BookDialog lib={lib} book={chosen} onClose={() => setChosen(null)} />
    </>
  )
}

/**
 * A list of sections (authors, series, and so on) in columns. A long one has an A–Z to jump along it and a filter;
 * `partial` while more of it is still to load.
 */
function SectionList({
  sections,
  partial,
  into,
}: {
  sections: Section[]
  partial: boolean
  into: (section: Section, children: ReactNode, props?: { className?: string; id?: string; title?: string }) => ReactNode
}) {
  const [filter, setFilter] = useState('')
  const long = sections.length >= LONG_LIST
  const words = filter.trim().toLowerCase()
  const shown = words ? sections.filter((s) => s.title.toLowerCase().includes(words)) : sections
  // The first section under each letter, for the A–Z to jump to.
  const firsts = new Map<string, string>()
  for (const s of shown) if (!firsts.has(letterOf(s.title))) firsts.set(letterOf(s.title), s.url)

  function jump(letter: string) {
    const target = document.getElementById(`section-${letter}`)
    target?.scrollIntoView({ block: 'start', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
    target?.focus({ preventScroll: true })
  }

  return (
    <>
      {long && (
        <div className="catalogue-tools">
          <label htmlFor="section-filter" className="visually-hidden">
            Filter this list
          </label>
          <input
            id="section-filter"
            type="search"
            className="catalogue-filter"
            placeholder="Filter this list"
            autoComplete="off"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
          <nav className="catalogue-az" aria-label="Jump to a letter">
            {ALPHABET.map((letter) => (
              <button key={letter} type="button" disabled={!firsts.has(letter)} onClick={() => jump(letter)}>
                {letter}
              </button>
            ))}
          </nav>
        </div>
      )}
      {words && partial && <p className="muted mono">Only the part of this list loaded so far is filtered. Search the library for the rest.</p>}
      {shown.length === 0 && <p className="muted">Nothing here matches.</p>}
      <ul className="catalogue-sections">
        {shown.map((section) => (
          <li key={section.url}>
            {into(
              section,
              <>
                <span>{section.title}</span>
                {section.summary && section.summary.length <= 24 && <span className="mono muted">{section.summary}</span>}
              </>,
              {
                id: long && firsts.get(letterOf(section.title)) === section.url ? `section-${letterOf(section.title)}` : undefined,
                // Each is one line, so a long name is cut short; the whole of it shows on hover.
                title: section.title,
              },
            )}
          </li>
        ))}
      </ul>
    </>
  )
}

/**
 * Where each book comes in the list called `name`, from a line in its details such as "Reading List Order: Cosmere [6]":
 * a series-like column, which Calibre's Reading List plugin can keep a list's order in. Null unless every book has one.
 */
function listOrder(books: OnlineBook[], name: string): number[] | null {
  if (books.length === 0) return null
  const wanted = name.trim().toLowerCase()
  const order: number[] = []
  for (const book of books) {
    const place = (book.summary ?? '')
      .split('\n')
      .map((line) => /^[^:]+:\s*(.+?)\s*\[(\d+(?:\.\d+)?)\]$/.exec(line.trim()))
      .find((match) => match?.[1].toLowerCase() === wanted)
    if (!place) return null
    order.push(Number(place[2]))
  }
  return order
}

/** False for the Reading List plugin's order column, which only keeps each list's order, so isn't worth browsing by. */
function browsable(section: { title: string }) {
  return !/reading list order/i.test(section.title)
}

const ALPHABET = [...'ABCDEFGHIJKLMNOPQRSTUVWXYZ', '#']

/**
 * The letter a title is listed under: its first after any "The", "A", or "An" (as Calibre sorts series), without
 * accents, or # for anything else.
 */
function letterOf(title: string) {
  const first = title.trim().replace(/^(the|an?)\s+/i, '').normalize('NFD').charAt(0).toUpperCase()
  return first >= 'A' && first <= 'Z' ? first : '#'
}

/** Asks for more of the list as the end of it comes near the screen. */
function MoreOnScroll({ pages, loading, onMore }: { pages: number; loading: boolean; onMore: () => void }) {
  const marker = useRef<HTMLDivElement>(null)
  // Watched afresh after each page arrives, in case the end is still in sight.
  useEffect(() => {
    const el = marker.current
    if (!el) return
    const observer = new IntersectionObserver((seen) => {
      if (seen.some((s) => s.isIntersecting)) onMore()
    }, { rootMargin: '800px' })
    observer.observe(el)
    return () => observer.disconnect()
  }, [pages, onMore])
  return (
    <div ref={marker} className="catalogue-more">
      {loading && <span className="muted mono">Loading…</span>}
    </div>
  )
}

/** Asks for the library's password, for a library that wants one and has none kept (or turned it down). */
function LoginForm({ lib, message, onDone }: { lib: Library; message: string; onDone: () => void }) {
  const [pass, setPass] = useState('')
  const [remember, setRemember] = useState(passwordRemembered(lib.id))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await loadFeed(lib, lib.url, pass)
      setPassword(lib.id, pass, remember)
      onDone()
    } catch (err) {
      setError(err instanceof LibraryError ? err.message : 'Something went wrong. Try again.')
      setBusy(false)
    }
  }

  return (
    <>
      <h1 className="search-title">{lib.name}</h1>
      <p className="muted library-intro">{message}</p>
      {lib.username ? (
        <form className="settings-form library-form" onSubmit={submit}>
          <label>
            <span>Password for {lib.username}</span>
            <input type="password" required autoFocus value={pass} onChange={(e) => setPass(e.target.value)} autoComplete="off" />
          </label>
          <RememberBox checked={remember} onChange={setRemember} />
          {error && (
            <p className="form-message error" role="alert">
              {error}
            </p>
          )}
          <div className="library-form-actions">
            <button type="submit" className="primary-button" disabled={busy}>
              {busy ? 'Checking…' : 'Open library'}
            </button>
          </div>
        </form>
      ) : (
        <a href={libraryPath(lib.id, { edit: true })} className="link-button">
          Add a username and password
        </a>
      )}
    </>
  )
}

/**
 * A cover from the library, fetched with its login (a picture on the page couldn't send it), once it's near the
 * screen: a library's server can be slow to make a thumbnail the first time, so the ones in view come first.
 */
function LibraryCover({ lib, url, ...props }: { lib: Library; url: string | null; title: string; author?: string; size?: 'small' | 'medium' }) {
  const box = useRef<HTMLSpanElement>(null)
  const [near, setNear] = useState(false)
  const [src, setSrc] = useState<string | null>(null)
  useEffect(() => {
    const el = box.current
    if (!el || near) return
    const observer = new IntersectionObserver((seen) => {
      if (seen.some((s) => s.isIntersecting)) setNear(true)
    }, { rootMargin: '300px' })
    observer.observe(el)
    return () => observer.disconnect()
  }, [near])
  useEffect(() => {
    if (!url || !near) return
    let made: string | null = null
    let cancelled = false
    libraryPicture(lib, url)
      .then((blob) => {
        if (cancelled) return
        made = URL.createObjectURL(blob)
        setSrc(made)
      })
      .catch(() => {})
    return () => {
      cancelled = true
      if (made) URL.revokeObjectURL(made)
    }
  }, [lib, url, near])
  return (
    <span ref={box} className="library-cover">
      <Cover url={src} {...props} />
    </span>
  )
}

/**
 * A book in the library: its cover, details, and summary, and reading it. The first time, it's downloaded into this
 * browser (then offered a link to its book in Carrel); after that, opening it fetches any newer version.
 */
function BookDialog({ lib, book, onClose }: { lib: Library; book: OnlineBook | null; onClose: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [linking, setLinking] = useState<BrowserBook | null>(null)
  const copy = book ? fromLibrary(lib.id, book.id) : undefined

  async function download() {
    if (!book?.epub) return
    setBusy(true)
    setError(null)
    try {
      const fetched = await downloadBook(lib, book.epub)
      if (fetched.unchanged) throw new LibraryError('The library didn’t send the book. Try again.')
      const added = await addDownloadedBook(fetched.file, {
        libraryId: lib.id,
        onlineId: book.id,
        downloadUrl: book.epub,
        etag: fetched.etag,
        lastModified: fetched.lastModified,
      })
      const { data } = await supabase.auth.getSession()
      // Found in Carrel straight away when signed in, as on Android, then read.
      if (data.session && added.bookId === null) setLinking(added)
      else window.location.assign(readerPath(added))
    } catch (e) {
      setError(e instanceof LibraryError || e instanceof BrowserBookError ? e.message : 'The book couldn’t be downloaded. Try again.')
      setBusy(false)
    }
  }

  return (
    <>
      <Dialog open={book !== null && linking === null} onClose={onClose} className="release-notes">
        <DialogBackdrop className="image-viewer-backdrop" />
        <div className="image-viewer-frame">
          <DialogPanel className="release-notes-panel">
            {book && (
              <>
                <div className="release-notes-header">
                  <span />
                  <button type="button" className="link-button" onClick={onClose}>
                    Close
                  </button>
                </div>
                <div className="link-match">
                  <LibraryCover lib={lib} url={book.cover} title={book.title} author={book.authors[0]} size="small" />
                  <div className="link-match-text">
                    <DialogTitle className="release-notes-title">{book.title}</DialogTitle>
                    {book.authors.length > 0 && <p className="muted">{listNames(book.authors)}</p>}
                  </div>
                </div>
                {copy ? (
                  <a href={readerPath(copy)} className="primary-button library-read">
                    Read
                  </a>
                ) : book.epub ? (
                  <button type="button" className="primary-button library-read" disabled={busy} onClick={() => void download()}>
                    {busy ? 'Downloading…' : 'Download and read'}
                  </button>
                ) : (
                  <p className="muted">This book isn’t available as an EPUB, so Carrel can’t read it.</p>
                )}
                {error && (
                  <p className="form-message error" role="alert">
                    {error}
                  </p>
                )}
                {book.summary?.split('\n\n').map((paragraph, i) => (
                  <p key={i} className="library-summary">
                    {paragraph}
                  </p>
                ))}
              </>
            )}
          </DialogPanel>
        </div>
      </Dialog>
      <LinkBookDialog
        book={linking}
        onClose={() => {
          if (linking) window.location.assign(readerPath(linking))
        }}
      />
    </>
  )
}
