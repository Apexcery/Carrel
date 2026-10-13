import { useEffect, useRef, useState } from 'react'
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from '@headlessui/react'
import { EpubNavigator, EpubPreferences, type EpubNavigatorListeners } from '@readium/navigator'
import { Locator, LocatorLocations, type Publication } from '@readium/shared'
import { browserBook, browserBookFile, BrowserBookError, saveBrowserBookPlace, type BrowserBook } from '../browserBooks'
import { EpubError } from '../epub'
import { supabase } from '../supabase'
import { getAccent, getTheme, setAccent, setTheme } from '../theme'
import { isNewerElsewhere, readPercent, ReadingProgress, SAME_PLACE, type ReadingPosition } from './progress'
import { openPublication, type ContentsLink } from './publication'

/** How long after a page turn the place is sent to Carrel. */
const SEND_AFTER_MS = 2000
/** How long the first page is given to settle before going to a place saved on another device. */
const GO_SETTLE_MS = 500

const FONT_SIZE_KEY = 'carrel-reader-font-size'
const FONT_STEP = 0.1
const MIN_FONT_SIZE = 0.7
const MAX_FONT_SIZE = 2

/** The eReader (/read/?book=…): a book in this browser, full page. */
export function ReaderPage() {
  const [book] = useState(() => browserBook(new URLSearchParams(window.location.search).get('book') ?? ''))
  if (!book) {
    return (
      <div className="reader-message">
        <p>That book isn’t in this browser.</p>
        <a href="/reading" className="link-button">
          Your books
        </a>
      </div>
    )
  }
  return <Reader book={book} />
}

/** The book open in Readium, with what it takes to move around it. */
interface OpenBook {
  navigator: EpubNavigator
  publication: Publication
  positions: Locator[]
  contents: ContentsLink[]
}

/** What reading has done so far, kept outside React's state since Readium's listeners read it. */
interface ReadingState {
  progress: ReadingProgress | null
  /** Where the book opened, to tell whether the reader has started reading yet. */
  openedProgression: number | null
  startedAt: number | null
  /**
   * Whether the reader has moved from where the book opened. Only then is the place sent to Carrel, so opening a book
   * can't overwrite a more recent place from another device before it's been offered.
   */
  moved: boolean
  /** The place waiting to be sent, a moment after a page turn. */
  sending: { timer: number; send: () => Promise<void> } | null
  /**
   * While Readium is laying the book out (opening it, or after a change of text size or colours): it reports the top
   * of the chapter before it reaches the reader's page, which isn't where they are.
   */
  settling: boolean
}

/**
 * Reads an EPUB with Readium. Clicking the edges of the page, the arrows beside it, or the arrow keys turns the page.
 * A book linked to Carrel moves to Reading as it opens, saves its progress, and carries on from (and is carried on
 * from) the other devices the reader reads it on.
 */
function Reader({ book }: { book: BrowserBook }) {
  const holder = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState<OpenBook | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [locator, setLocator] = useState<Locator | null>(null)
  /** The contents entry the reader is in, as Readium follows it (including chapters partway through a file). */
  const [chapter, setChapter] = useState<string | null>(null)
  /** Where the seek bar is being dragged to (0 to 1), until the book gets there. */
  const [seeking, setSeeking] = useState<number | null>(null)
  const [fontSize, setFontSize] = useState(storedFontSize)
  const [contents, setContents] = useState(false)
  const [elsewhere, setElsewhere] = useState<ReadingPosition | null>(null)
  const [askingRead, setAskingRead] = useState(false)
  const state = useRef<ReadingState>({ progress: null, openedProgression: null, startedAt: null, moved: false, sending: null, settling: true })
  /** Goes to a place saved on another device. */
  const goTo = useRef<(position: ReadingPosition) => void>(() => {})
  /** Takes in where the reader is (see placeChanged). */
  const report = useRef<(current: Locator) => void>(() => {})

  useEffect(() => {
    let cancelled = false
    let navigator: EpubNavigator | undefined
    let positions: Locator[] = []
    let close: (() => Promise<void>) | undefined

    function placeChanged(current: Locator) {
      if (state.current.settling) return
      setLocator(current)
      setSeeking(null)
      const s = state.current
      const json = JSON.stringify(current.serialize())
      const progression = current.locations?.totalProgression
      saveBrowserBookPlace(book.id, json, progression ?? null)
      if (s.progress?.moved(progression)) void s.progress.save()
      // A copy never read here starts from its first page shown.
      if (s.startedAt === null && progression !== undefined) s.startedAt = progression
      if (progression !== undefined && s.startedAt !== null && Math.abs(progression - s.startedAt) >= SAME_PLACE) s.moved = true
      // Sent shortly after each page turn, so closing the tab loses nothing; flicking through pages, or dragging the
      // seek bar, sends just the last.
      const progress = s.progress
      if (s.moved && progress && progression !== undefined) {
        const at = new Date()
        if (s.sending) window.clearTimeout(s.sending.timer)
        const send = () => {
          s.sending = null
          return progress.savePosition(json, progression, at)
        }
        s.sending = { timer: window.setTimeout(send, SEND_AFTER_MS), send }
      }
    }

    ;(async () => {
      const { data } = await supabase.auth.getSession()
      const progress = data.session && book.bookId !== null ? new ReadingProgress(book.bookId) : null
      state.current.progress = progress
      // Where a copy never opened here starts depends on the library, so that waits; otherwise it carries on alongside.
      const starting = progress?.start()
      if (book.locator === null) await starting

      const opened = await openPublication(await browserBookFile(book))
      close = opened.close
      if (cancelled) {
        void opened.close()
        return
      }
      const { publication } = opened
      positions = opened.positions
      const saved = book.locator ? Locator.deserialize(JSON.parse(book.locator)) : undefined
      let start: Locator | undefined
      if (!saved) {
        const percent = progress ? await progress.startingPercent() : null
        start = percent !== null ? locate(percent / 100) : undefined
      } else if (publication.readingOrder.findWithHref(saved.href)) {
        start = saved
      } else if (book.progression !== null) {
        // A newer version without the part the reader was in: as far through as before.
        start = locate(book.progression)
      }
      state.current.openedProgression = start?.locations?.totalProgression ?? null
      state.current.startedAt = state.current.openedProgression

      navigator = new EpubNavigator(holder.current!, publication, listeners({
        frameLoaded: (wnd) => watchFrame(wnd, () => navigator),
        positionChanged: placeChanged,
        timelineItemChanged: (item) => setChapter(item?.title?.replace(/\s+/g, ' ').trim() || null),
      }), positions, start, { preferences: readerPreferences(storedFontSize()), defaults: {} })
      await navigator.load()
      if (cancelled) return
      state.current.settling = false
      placeChanged(navigator.currentLocator)
      setOpen({ navigator, publication, positions, contents: opened.contents })
      if (progress) void syncPosition(progress)
    })().catch((e: unknown) => {
      if (cancelled) return
      console.error(e)
      setError(e instanceof BrowserBookError || e instanceof EpubError ? e.message : 'This book couldn’t be opened.')
    })

    /**
     * Compares the place in the book saved in Carrel (from any device) with this browser's: a more recent one
     * elsewhere is gone to, or offered once the reader has started reading here, and a more recent one here is sent.
     */
    async function syncPosition(progress: ReadingProgress) {
      const remote = await progress.savedPosition()
      if (cancelled) return
      const localAt = book.locator ? book.openedAt : null
      if (remote && isNewerElsewhere(remote, localAt, book.progression)) {
        await new Promise((done) => window.setTimeout(done, GO_SETTLE_MS))
        if (cancelled) return
        const s = state.current
        if (s.moved) setElsewhere(remote)
        else goToPosition(remote)
      } else if (book.locator && book.openedAt !== null && (!remote || book.openedAt > Date.parse(remote.updatedAt))) {
        await progress.savePosition(book.locator, book.progression ?? 0, new Date(book.openedAt))
      }
    }

    function goToPosition(position: ReadingPosition) {
      if (!navigator) return
      let saved: Locator | undefined
      try {
        saved = Locator.deserialize(JSON.parse(position.locator))
      } catch {
        saved = undefined
      }
      const target = saved && navigator.publication.readingOrder.findWithHref(saved.href) ? saved : locate(position.progression)
      if (target) navigator.go(target, false, () => {})
    }
    goTo.current = goToPosition
    report.current = placeChanged

    /** The position as far through the book as `progression` (0 to 1). */
    function locate(progression: number) {
      return positions[Math.min(positions.length - 1, Math.floor(progression * positions.length))]
    }

    return () => {
      cancelled = true
      void navigator?.destroy()
      void close?.()
    }
  }, [book])

  // The text size it opened with, so only a change restyles the book.
  const sizeShown = useRef(fontSize)
  useEffect(() => {
    if (!open || sizeShown.current === fontSize) return
    sizeShown.current = fontSize
    void restyle(open.navigator, fontSize, state.current, report.current)
  }, [open, fontSize])

  // Colours follow the site's theme, which can change in another tab or with the system's.
  useEffect(() => {
    if (!open) return
    const apply = () => void restyle(open.navigator, sizeShown.current, state.current, report.current)
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const storageChanged = (event: StorageEvent) => {
      if (event.key !== 'carrel-theme' && event.key !== 'carrel-accent') return
      setTheme(getTheme())
      setAccent(getAccent())
      apply()
    }
    media.addEventListener('change', apply)
    window.addEventListener('storage', storageChanged)
    return () => {
      media.removeEventListener('change', apply)
      window.removeEventListener('storage', storageChanged)
    }
  }, [open])

  // The arrow keys turn the page while the page around the book has focus (the book's frames handle their own).
  useEffect(() => {
    if (!open) return
    const keydown = (event: KeyboardEvent) => turnByKey(event, open.navigator)
    window.addEventListener('keydown', keydown)
    return () => window.removeEventListener('keydown', keydown)
  }, [open])

  // Leaving the tab (or closing it) saves progress and sends the place straight away.
  useEffect(() => {
    const hidden = () => {
      if (document.visibilityState === 'hidden') void flush()
    }
    document.addEventListener('visibilitychange', hidden)
    return () => document.removeEventListener('visibilitychange', hidden)
  }, [])

  async function flush() {
    const s = state.current
    const sending = s.sending
    if (sending) {
      window.clearTimeout(sending.timer)
      await sending.send()
    }
    await s.progress?.save()
  }

  async function close() {
    await flush()
    if (await state.current.progress?.atEnd()) setAskingRead(true)
    else leave()
  }

  /** Back to where the reader came from in Carrel, or their books in this browser; a finished book opens its page. */
  function leave(finished = false) {
    if (finished && book.bookId !== null) {
      window.location.assign(`/books/${book.bookId}`)
    } else if (document.referrer && new URL(document.referrer).origin === window.location.origin && window.history.length > 1) {
      window.history.back()
    } else {
      window.location.assign('/reading')
    }
  }

  function changeSize(by: number) {
    const next = Math.round(Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, fontSize + by)) * 10) / 10
    setFontSize(next)
    try {
      localStorage.setItem(FONT_SIZE_KEY, String(next))
    } catch {
      // Kept for this visit only.
    }
  }

  function seekTo(progression: number) {
    if (!open) return
    const { positions, navigator } = open
    navigator.go(positions[Math.min(positions.length - 1, Math.floor(progression * positions.length))], false, () => {})
  }

  const progression = locator?.locations?.totalProgression

  if (error) {
    return (
      <div className="reader-message">
        <p>{error}</p>
        <a href="/reading" className="link-button">
          Your books
        </a>
      </div>
    )
  }

  return (
    <div className="reader">
      <header className="reader-bar">
        <button type="button" className="link-button" onClick={() => void close()}>
          Close
        </button>
        <h1 className="reader-title">{book.title}</h1>
        <div className="reader-tools">
          <button type="button" className="link-button" disabled={!open} onClick={() => setContents(true)}>
            Contents
          </button>
          <button type="button" className="link-button" aria-label="Smaller text" disabled={fontSize <= MIN_FONT_SIZE} onClick={() => changeSize(-FONT_STEP)}>
            A−
          </button>
          <button type="button" className="link-button" aria-label="Larger text" disabled={fontSize >= MAX_FONT_SIZE} onClick={() => changeSize(FONT_STEP)}>
            A+
          </button>
        </div>
      </header>

      {elsewhere && (
        <div className="reader-notice" role="status">
          <span>You were at {readPercent(elsewhere.progression)}% on another device.</span>
          <button
            type="button"
            className="link-button accent"
            onClick={() => {
              setElsewhere(null)
              goTo.current(elsewhere)
            }}
          >
            Go there
          </button>
          <button type="button" className="link-button" onClick={() => setElsewhere(null)}>
            Stay
          </button>
        </div>
      )}

      <div className="reader-main">
        <button type="button" className="reader-turn" aria-label="Previous page" disabled={!open} onClick={() => open?.navigator.goLeft(false, () => {})}>
          <span aria-hidden="true">‹</span>
        </button>
        {/* Readium sizes its own box (holder) for a comfortable line length; the space either side turns the page too. */}
        <div
          className="reader-book"
          aria-busy={!open}
          onClick={(e) => {
            if (!open || e.target !== e.currentTarget) return
            const box = e.currentTarget.getBoundingClientRect()
            if (e.clientX < box.left + box.width / 2) open.navigator.goLeft(false, () => {})
            else open.navigator.goRight(false, () => {})
          }}
        >
          <div className="reader-frames" ref={holder} />
        </div>
        <button type="button" className="reader-turn" aria-label="Next page" disabled={!open} onClick={() => open?.navigator.goRight(false, () => {})}>
          <span aria-hidden="true">›</span>
        </button>
      </div>

      <footer className="reader-footer">
        <input
          type="range"
          className="reader-seek"
          min={0}
          max={1000}
          value={Math.round((seeking ?? progression ?? 0) * 1000)}
          disabled={!open}
          aria-label="Where you are in the book"
          onChange={(e) => setSeeking(Number(e.target.value) / 1000)}
          onPointerUp={(e) => {
            if (seeking !== null) seekTo(seeking)
            // Let go of it, so the arrow keys turn the page again rather than move the bar.
            e.currentTarget.blur()
          }}
          onKeyUp={() => seeking !== null && seekTo(seeking)}
        />
        <span className="reader-place">
          {/* While dragging, where letting go will go, without the chapter it's leaving. */}
          {seeking !== null
            ? `${readPercent(seeking)}%`
            : [progression !== undefined ? `${readPercent(progression)}%` : null, chapter].filter(Boolean).join(' · ')}
        </span>
      </footer>

      <Dialog open={contents} onClose={() => setContents(false)} className="release-notes">
        <DialogBackdrop className="image-viewer-backdrop" />
        <div className="image-viewer-frame">
          <DialogPanel className="release-notes-panel">
            <div className="release-notes-header">
              <DialogTitle className="release-notes-title">Contents</DialogTitle>
              <button type="button" className="link-button" onClick={() => setContents(false)}>
                Close
              </button>
            </div>
            {open && open.contents.length === 0 && <p className="muted">This book has no contents list.</p>}
            {open && open.contents.length > 0 && (
              <ol className="reader-contents">
                {flatten(open.contents, 0).map(({ link, depth }, i) => (
                  <li key={i} style={{ paddingLeft: `${depth * 1.2}em` }}>
                    <button
                      type="button"
                      onClick={() => {
                        setContents(false)
                        open.navigator.go(contentsLocator(open, link), false, () => {})
                      }}
                    >
                      {link.title || 'Untitled'}
                    </button>
                  </li>
                ))}
              </ol>
            )}
          </DialogPanel>
        </div>
      </Dialog>

      <MarkReadDialog
        open={askingRead}
        progress={state.current.progress}
        onClose={() => setAskingRead(false)}
        onDone={(finished) => leave(finished)}
      />
    </div>
  )
}

/** At the end of the book, closing it asks whether to mark it as read. */
function MarkReadDialog({
  open,
  progress,
  onClose,
  onDone,
}: {
  open: boolean
  progress: ReadingProgress | null
  onClose: () => void
  onDone: (finished: boolean) => void
}) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function markRead() {
    setSaving(true)
    setError(null)
    try {
      await progress?.markRead()
      onDone(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong. Try again.')
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} className="release-notes">
      <DialogBackdrop className="image-viewer-backdrop" />
      <div className="image-viewer-frame">
        <DialogPanel className="release-notes-panel">
          {/* Not the file's own title, which can be untidy ("Frankenstein; or, the modern prometheus"). */}
          <DialogTitle className="release-notes-title">You’ve reached the end of this book</DialogTitle>
          <p>Mark it as read?</p>
          {error && (
            <p className="form-message error" role="alert">
              {error}
            </p>
          )}
          <div className="reader-dialog-actions">
            <button type="button" className="primary-button" disabled={saving} onClick={() => void markRead()}>
              {saving ? 'Saving…' : 'Mark as read'}
            </button>
            <button type="button" className="link-button" disabled={saving} onClick={() => onDone(false)}>
              Not now
            </button>
          </div>
        </DialogPanel>
      </div>
    </Dialog>
  )
}

/** Readium's listeners, doing nothing but what's given. */
function listeners(given: Pick<EpubNavigatorListeners, 'frameLoaded' | 'positionChanged' | 'timelineItemChanged'>): EpubNavigatorListeners {
  const noop = () => {}
  return {
    tap: () => false,
    click: () => false,
    zoom: noop,
    miscPointer: noop,
    scroll: noop,
    customEvent: noop,
    // Links out of the book open in a new tab.
    handleLocator: (locator) => {
      if (/^https?:\/\//.test(locator.href)) window.open(locator.href, '_blank', 'noopener,noreferrer')
      return true
    },
    textSelected: noop,
    contentProtection: noop,
    contextMenu: noop,
    peripheral: noop,
    ...given,
  }
}

const watched = new WeakSet<Document>()

/**
 * Adds to each chapter's frame (once: Readium reports a frame as loaded each time it's shown) the arrow keys, and
 * stops quick clicks on the page's edges selecting a word: Readium ignores a click while text is selected, so the
 * page would stop turning until the selection cleared.
 */
function watchFrame(wnd: Window, navigator: () => EpubNavigator | undefined) {
  const doc = wnd.document
  if (watched.has(doc)) return
  watched.add(doc)
  doc.addEventListener('keydown', (event) => {
    const reader = navigator()
    if (reader) turnByKey(event, reader)
  })
  doc.addEventListener('mousedown', (event) => {
    const width = wnd.innerWidth
    if (event.detail > 1 && (event.clientX < width / 4 || event.clientX > (width * 3) / 4)) event.preventDefault()
  })
}

function turnByKey(event: KeyboardEvent, navigator: EpubNavigator) {
  if (event.altKey || event.ctrlKey || event.metaKey || event.defaultPrevented) return
  const target = event.target as HTMLElement | null
  if (target && (target.closest('input, textarea, select, [contenteditable]') || target.closest('[role="dialog"]'))) return
  if (event.key === 'ArrowLeft') navigator.goLeft(false, () => {})
  else if (event.key === 'ArrowRight') navigator.goRight(false, () => {})
  else if (event.key === 'PageUp') navigator.goBackward(false, () => {})
  else if (event.key === 'PageDown' || (event.key === ' ' && !event.shiftKey)) navigator.goForward(false, () => {})
  else return
  event.preventDefault()
}

/**
 * Applies the theme's colours and the text size, then goes back to where the reader was: Readium starts the chapter
 * again when its text flows anew.
 */
async function restyle(navigator: EpubNavigator, fontSize: number, state: ReadingState, report: (current: Locator) => void) {
  const place = navigator.currentLocator
  state.settling = true
  try {
    await navigator.submitPreferences(new EpubPreferences(readerPreferences(fontSize)))
  } finally {
    navigator.go(place, false, () => {
      state.settling = false
      report(navigator.currentLocator)
    })
  }
}

/** The book in Carrel's paper and ink, at the reader's text size (1 is the book's own). */
function readerPreferences(fontSize: number) {
  const style = getComputedStyle(document.documentElement)
  return {
    backgroundColor: style.getPropertyValue('--paper').trim(),
    textColor: style.getPropertyValue('--ink').trim(),
    fontSize,
  }
}

function storedFontSize() {
  try {
    const size = Number(localStorage.getItem(FONT_SIZE_KEY))
    return size >= MIN_FONT_SIZE && size <= MAX_FONT_SIZE ? size : 1
  } catch {
    return 1
  }
}

/** The contents list's entries in reading order, each with how deeply it's nested. */
function flatten(links: ContentsLink[], depth: number): { link: ContentsLink; depth: number }[] {
  return links.flatMap((link) => [{ link, depth }, ...flatten(link.children, depth + 1)])
}

function contentsLocator(open: OpenBook, link: ContentsLink): Locator {
  const [href, fragment] = link.href.split('#')
  const type = open.publication.readingOrder.findWithHref(href)?.type ?? 'application/xhtml+xml'
  return new Locator({ href, type, title: link.title, locations: new LocatorLocations(fragment ? { fragments: [fragment] } : {}) })
}
