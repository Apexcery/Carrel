import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { apiDownload, apiGet, apiGetOrNull, apiSend, apiUpload } from '../api'
import { Cover } from '../components/Cover'
import { ErrorNotice } from '../components/ErrorNotice'
import { listNames, seriesPosition } from '../format'
import { usePageTitle } from '../pageTitle'
import type { BookSearchResponse, BookSearchResult, ImportReviewItem, ImportSource, ImportStatus, LibraryItem } from '../types'

const SOURCE_NAMES: Record<ImportSource, string> = { goodreads: 'Goodreads', story_graph: 'StoryGraph', carrel: 'Carrel' }
const POLL_MS = 3000
const PICKER_RESULTS = 5

/**
 * The Import & Export section of Settings: import a Goodreads, StoryGraph, or Carrel export, follow its progress, and
 * sort out the books it couldn't match for sure; or download the library to take elsewhere.
 */
export function ImportExportSettings() {
  usePageTitle('Import & Export')
  const queryClient = useQueryClient()
  const latest = useQuery({
    queryKey: ['import-latest'],
    queryFn: () => apiGetOrNull<ImportStatus>('/imports/latest'),
    // Imports run in the background; poll while one is in progress.
    refetchInterval: (query) => (isActive(query.state.data) ? POLL_MS : false),
  })
  const library = useQuery({ queryKey: ['library'], queryFn: () => apiGet<LibraryItem[]>('/library') })

  // When an import finishes, everything built from the library (shelves, suggestions, stats) is out of date.
  const state = latest.data?.state
  const previousState = useRef(state)
  useEffect(() => {
    if (previousState.current && previousState.current !== state && state === 'done') {
      queryClient.invalidateQueries({ queryKey: ['library'] })
    }
    previousState.current = state
  }, [state, queryClient])

  // An import with nothing left to sort out is cleared away, but not while the reader watches: only one that was
  // already sorted out when the page opened is hidden.
  const [hiddenId, setHiddenId] = useState<number | null>()
  if (hiddenId === undefined && latest.data !== undefined) {
    setHiddenId(latest.data && isSettled(latest.data) ? latest.data.id : null)
  }

  if (latest.isError) {
    return <ErrorNotice error={latest.error} onRetry={() => latest.refetch()} />
  }
  if (latest.isPending || library.isPending) {
    return <section className="import-page" aria-busy="true" />
  }

  const status = latest.data?.id === hiddenId ? null : latest.data
  const libraryHasBooks = (library.data?.length ?? 0) > 0

  return (
    <section className="import-page">
      <p className="settings-note">
        Bring your shelves, ratings, and reading dates over from Goodreads or StoryGraph, or take them with you.
      </p>

      <ExportLibrary libraryHasBooks={libraryHasBooks} />
      {status && isActive(status) && <ImportProgress status={status} />}
      {!isActive(status) &&
        (status ? (
          <AnotherImport libraryHasBooks={libraryHasBooks} />
        ) : (
          <UploadForm libraryHasBooks={libraryHasBooks} heading />
        ))}
      {status?.state === 'failed' && <ImportFailed status={status} />}
      {status?.state === 'done' && <ImportResults status={status} />}
    </section>
  )
}

function isActive(status: ImportStatus | null | undefined): status is ImportStatus {
  return status?.state === 'matching' || status?.state === 'waiting'
}

/** Finished, with every book it flagged checked, chosen, or skipped. */
function isSettled(status: ImportStatus): boolean {
  return status.state === 'done' && status.toCheck === 0 && status.notFound === 0
}

/** After an import, the form for the next one stays folded away above the results until it's wanted. */
function AnotherImport({ libraryHasBooks }: { libraryHasBooks: boolean }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="another-import">
      <button
        type="button"
        className="secondary-button"
        aria-expanded={open}
        aria-controls="another-import"
        onClick={() => setOpen(!open)}
      >
        Import another export <span className="disclosure-arrow" aria-hidden="true">▾</span>
      </button>
      {open && (
        <div id="another-import">
          <UploadForm libraryHasBooks={libraryHasBooks} />
        </div>
      )}
    </div>
  )
}

function UploadForm({ libraryHasBooks, heading = false }: { libraryHasBooks: boolean; heading?: boolean }) {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [existing, setExisting] = useState<'skip' | 'overwrite'>('skip')
  const upload = useMutation({
    mutationFn: (chosen: File) => apiUpload<ImportStatus>(`/imports?existing=${existing}`, chosen),
    onSuccess: (status) => queryClient.setQueryData(['import-latest'], status),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (file) {
      upload.mutate(file)
    }
  }

  return (
    <form className="import-form" onSubmit={submit}>
      {heading && <h2 className="section-title">Import your library</h2>}
      <div className="import-sources">
        <div>
          {/* Recommended: Goodreads exports carry Goodreads ids, which match almost every book exactly. */}
          <h3 className="import-source-name">
            Goodreads <span className="import-recommended">(Recommended)</span>
          </h3>
          <p>
            Go to <strong>My Books</strong>, choose <strong>Import and export</strong>, then <strong>Export Library</strong>.
            Download the file once it’s ready.
          </p>
        </div>
        <div>
          <h3 className="import-source-name">StoryGraph</h3>
          <p>
            Open <strong>Manage Account</strong>, choose <strong>Export StoryGraph Library</strong>, then download the
            file once it’s ready.
          </p>
        </div>
        <div>
          <h3 className="import-source-name">Carrel</h3>
          <p>
            A <strong>Full Carrel export</strong> from above brings back everything, including every read’s dates and
            your progress.
          </p>
        </div>
      </div>

      <div className="import-file">
        <span className="shelf-label">Export file (CSV)</span>
        <div className="file-choice">
          {/* The browser's own file button doesn't match the site, so the input is hidden behind a styled label. */}
          <label className="secondary-button">
            <input
              type="file"
              className="visually-hidden"
              accept=".csv,text/csv"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            {file ? 'Choose another file' : 'Choose file'}
          </label>
          <span className={file ? 'file-name' : 'muted'}>{file?.name ?? 'No file chosen'}</span>
        </div>
      </div>

      {libraryHasBooks && (
        <div className="import-existing">
          <span className="shelf-label" id="import-existing-label">
            Books already in your library
          </span>
          <div className="status-picker" role="radiogroup" aria-labelledby="import-existing-label">
            {(['skip', 'overwrite'] as const).map((choice) => (
              <button
                key={choice}
                type="button"
                role="radio"
                aria-checked={existing === choice}
                onClick={() => setExisting(choice)}
              >
                {choice === 'skip' ? 'Skip existing' : 'Overwrite existing'}
              </button>
            ))}
          </div>
          <p className="setting-hint">
            {existing === 'skip'
              ? 'Books you already have stay as they are.'
              : 'The export replaces the status, rating, and reading dates of books you already have.'}
          </p>
        </div>
      )}

      {upload.isError && (
        <p className="form-message error" role="alert">
          {upload.error.message}
        </p>
      )}
      <div>
        <button type="submit" className="primary-button" disabled={!file || upload.isPending}>
          {upload.isPending ? 'Uploading…' : 'Start import'}
        </button>
      </div>
    </form>
  )
}

const EXPORTS = [
  {
    format: 'goodreads',
    name: 'For Goodreads or StoryGraph',
    file: 'carrel-library-goodreads',
    description:
      'In Goodreads’ format, which Goodreads and StoryGraph can both import. It keeps your shelves, ratings (rounded down to whole stars), latest finish dates, and how many times you’ve read each book.',
  },
  {
    format: 'carrel',
    name: 'Full Carrel export',
    file: 'carrel-library',
    description:
      'Everything, including every read’s start and finish dates, half stars, and your progress. Keep it as a backup, or import it back into Carrel.',
  },
] as const

/** Downloads of the reader's library, in Goodreads' format or Carrel's own. */
function ExportLibrary({ libraryHasBooks }: { libraryHasBooks: boolean }) {
  const download = useMutation({
    mutationFn: async ({ format, file }: (typeof EXPORTS)[number]) => {
      const blob = await apiDownload(`/library/export?format=${format}`)
      // Saved through a temporary link, since the request needs the reader's token and a plain link can't send it.
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${file}-${new Date().toLocaleDateString('en-CA')}.csv`
      link.click()
      URL.revokeObjectURL(url)
    },
  })

  return (
    <section className="import-export">
      <h2 className="section-title">Export your library</h2>
      {!libraryHasBooks ? (
        <p className="muted">Nothing to export yet. Books you add to your library can be downloaded here.</p>
      ) : (
        <>
          <div className="import-sources">
            {EXPORTS.map((option) => (
              <div key={option.format} className="export-option">
                <h3 className="import-source-name">{option.name}</h3>
                <p>{option.description}</p>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={download.isPending}
                  onClick={() => download.mutate(option)}
                >
                  {download.isPending && download.variables?.format === option.format ? 'Preparing…' : 'Download CSV'}
                </button>
              </div>
            ))}
          </div>
          {download.isError && <ErrorNotice error={download.error} />}
        </>
      )}
    </section>
  )
}

function ImportProgress({ status }: { status: ImportStatus }) {
  const done = status.total - status.remaining
  const percent = status.total > 0 ? Math.round((done / status.total) * 100) : 0
  return (
    <div className="import-progress" role="status">
      <p className="kicker">Importing from {SOURCE_NAMES[status.source]}</p>
      <div className="progress-bar" aria-hidden="true">
        <span style={{ width: `${percent}%` }} />
      </div>
      <p className="progress-summary mono">
        {done.toLocaleString()} of {status.total.toLocaleString()} books imported
      </p>
      <p className="muted">
        {status.state === 'waiting'
          ? 'Paused until Hardcover’s daily limit resets. It carries on by itself, so there’s nothing you need to do.'
          : 'This carries on in the background, so you can leave this page. Bigger libraries take a few minutes.'}
      </p>
    </div>
  )
}

function ImportFailed({ status }: { status: ImportStatus }) {
  const queryClient = useQueryClient()
  const resume = useMutation({
    mutationFn: () => apiSend<ImportStatus>('POST', `/imports/${status.id}/resume`),
    onSuccess: (updated) => queryClient.setQueryData(['import-latest'], updated),
  })
  return (
    <div className="import-progress" role="alert">
      <p className="kicker">Import stopped</p>
      <p>
        Your {SOURCE_NAMES[status.source]} import ran into trouble after looking up{' '}
        {(status.total - status.remaining).toLocaleString()} of {status.total.toLocaleString()} books. Trying again carries on
        from there.
      </p>
      <div>
        <button type="button" className="primary-button" disabled={resume.isPending} onClick={() => resume.mutate()}>
          Try again
        </button>
      </div>
      {resume.isError && <ErrorNotice error={resume.error} />}
    </div>
  )
}

function ImportResults({ status }: { status: ImportStatus }) {
  const review = useQuery({
    queryKey: ['import-review', status.id],
    queryFn: () => apiGet<ImportReviewItem[]>(`/imports/${status.id}/review`),
  })
  const toCheck = review.data?.filter((i) => i.match === 'by_title') ?? []
  const notFound = review.data?.filter((i) => i.match === 'not_found') ?? []

  return (
    <div className="import-results">
      <div className="import-progress">
        <p className="kicker">Imported from {SOURCE_NAMES[status.source]}</p>
        <p>
          Found {status.matched.toLocaleString()} of the {status.total.toLocaleString()} books in your export.
        </p>
        {isSettled(status) && <p className="muted">Everything’s sorted, so there’s nothing left to check.</p>}
      </div>

      {review.isError && <ErrorNotice error={review.error} onRetry={() => review.refetch()} />}

      {/* Missing books first: they aren't in the library at all. */}
      {notFound.length > 0 && (
        <section className="import-review">
          <h2 className="section-title">Couldn’t find ({notFound.length})</h2>
          <p className="muted">Search for each one to add it, or skip it.</p>
          <ul>
            {notFound.map((item) => (
              <ReviewRow key={item.id} importId={status.id} item={item} />
            ))}
          </ul>
        </section>
      )}
      {toCheck.length > 0 && (
        <section className="import-review">
          <h2 className="section-title">Check these ({toCheck.length})</h2>
          <p className="muted">
            These were matched by title and author rather than an ISBN, so they may be a different book. They’re in your
            library already.
          </p>
          <ul>
            {toCheck.map((item) => (
              <ReviewRow key={item.id} importId={status.id} item={item} />
            ))}
          </ul>
        </section>
      )}

    </div>
  )
}

function ReviewRow({ importId, item }: { importId: number; item: ImportReviewItem }) {
  const exported = splitSeries(item.title)
  const queryClient = useQueryClient()
  const [picking, setPicking] = useState(false)
  const act = useMutation({
    mutationFn: ({ action, body }: { action: 'confirm' | 'dismiss' | 'choose'; body?: unknown }) =>
      apiSend<void>('POST', `/imports/${importId}/items/${item.id}/${action}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['import-review', importId] })
      queryClient.invalidateQueries({ queryKey: ['import-latest'] })
      queryClient.invalidateQueries({ queryKey: ['library'] })
    },
  })

  return (
    <li className="review-row">
      <div className="review-pair">
        <div className="review-source">
          <p className="review-title">{exported.title}</p>
          <SeriesLine name={exported.series} position={exported.position} />
          {item.authors.length > 0 && <p className="muted">{listNames(item.authors.slice(0, 3))}</p>}
        </div>
        {item.book && (
          <Link to={`/books/${item.book.id}`} target="_blank" rel="noopener" className="review-match">
            <Cover url={item.book.coverUrl} title={item.book.title} author={item.book.authors[0]} size="small" />
            <div>
              <p className="review-title">{item.book.title}</p>
              <SeriesLine name={item.book.seriesName} position={seriesPosition(item.book.seriesPosition)} />
              <p className="muted">
                {listNames(item.book.authors.slice(0, 3))}
                {item.book.firstPublishedYear && <span className="mono"> · {item.book.firstPublishedYear}</span>}
              </p>
            </div>
          </Link>
        )}
      </div>

      <div className="review-actions">
        {item.match === 'by_title' && (
          <button type="button" className="link-button" disabled={act.isPending} onClick={() => act.mutate({ action: 'confirm' })}>
            Looks right
          </button>
        )}
        <button type="button" className="link-button" disabled={act.isPending} onClick={() => setPicking(!picking)}>
          {item.match === 'by_title' ? 'Wrong book' : 'Find it'}
        </button>
        {item.match === 'not_found' && (
          <button type="button" className="link-button" disabled={act.isPending} onClick={() => act.mutate({ action: 'dismiss' })}>
            Skip
          </button>
        )}
      </div>

      {picking && (
        <BookPicker
          initialQuery={`${item.title.replace(/\s*[([][^)\]]*[)\]]/g, '')} ${item.authors[0] ?? ''}`.trim()}
          busy={act.isPending}
          onPick={(result) =>
            act.mutate({ action: 'choose', body: { hardcoverId: result.hardcoverId, openLibraryId: result.openLibraryWorkId } })
          }
        />
      )}
      {act.isError && <ErrorNotice error={act.error} />}
    </li>
  )
}

function BookPicker({ initialQuery, busy, onPick }: { initialQuery: string; busy: boolean; onPick: (result: BookSearchResult) => void }) {
  const [text, setText] = useState(initialQuery)
  const [query, setQuery] = useState(initialQuery)
  const search = useQuery({
    // Not ['search', …]: the search page keeps paged results under that key.
    queryKey: ['import-search', query],
    queryFn: () => apiGet<BookSearchResponse>(`/books/search?q=${encodeURIComponent(query)}&page=1`),
    enabled: query.length > 0,
  })

  return (
    <div className="book-picker">
      <form
        className="filter-box"
        role="search"
        onSubmit={(e) => {
          e.preventDefault()
          setQuery(text.trim())
        }}
      >
        <label className="visually-hidden" htmlFor={`picker-${initialQuery}`}>
          Search for the book
        </label>
        <input id={`picker-${initialQuery}`} type="search" value={text} onChange={(e) => setText(e.target.value)} />
      </form>
      {search.isPending && query && <p className="muted">Searching…</p>}
      {search.isError && <ErrorNotice error={search.error} onRetry={() => search.refetch()} />}
      {search.data && search.data.results.length === 0 && <p className="muted">No books found. Try different words.</p>}
      <ul>
        {search.data?.results.slice(0, PICKER_RESULTS).map((result) => (
          <li key={result.hardcoverId ?? result.openLibraryWorkId} className="picker-result">
            {/* Opens in a new tab, so the book can be checked without losing the review list. */}
            <Link
              to={result.hardcoverId ? `/books/hardcover/${result.hardcoverId}` : `/books/openlibrary/${result.openLibraryWorkId}`}
              target="_blank"
              rel="noopener"
              className="review-match"
            >
              <Cover url={result.coverUrl} title={result.title} author={result.authors[0]} size="small" />
              <div>
                <p className="review-title">{result.title}</p>
                <SeriesLine name={result.seriesName} position={seriesPosition(result.seriesPosition)} />
                <p className="muted">
                  {listNames(result.authors.slice(0, 3))}
                  {result.releaseYear && <span className="mono"> · {result.releaseYear}</span>}
                </p>
              </div>
            </Link>
            <button type="button" className="link-button" disabled={busy} onClick={() => onPick(result)}>
              Choose
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** A book's series and number on its own line under the title, so long titles can't push it out of sight. */
function SeriesLine({ name, position }: { name: string | null; position: string | null }) {
  return name ? (
    <p className="review-series mono">
      {position ? `Book ${position} · ` : ''}
      {name}
    </p>
  ) : null
}

/**
 * Goodreads titles end with the series, as in "Going to Ground (The Shapeshifter, #3)"; this splits it off. Titles
 * without one, including every StoryGraph title, come back whole.
 */
function splitSeries(title: string): { title: string; series: string | null; position: string | null } {
  const match = /^(.+?)\s*\(([^()]+?),?\s*#(\d+(?:\.\d+)?)\)\s*$/.exec(title)
  return match ? { title: match[1], series: match[2], position: match[3] } : { title, series: null, position: null }
}
