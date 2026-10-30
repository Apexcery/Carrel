import { Fragment, useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle, Popover, PopoverButton, PopoverPanel } from '@headlessui/react'
import { EpubNavigator, EpubPreferences, FrameManager, type EpubNavigatorListeners } from '@readium/navigator'
import { Locator, LocatorLocations, ReadingProgression, type Publication } from '@readium/shared'
import {
  browserBook,
  browserBookFile,
  BrowserBookError,
  replaceBrowserBook,
  saveBrowserBookPlace,
  type BrowserBook,
} from '../browserBooks'
import { downloadBook, library } from '../libraries'
import { EpubError } from '../epub'
import { supabase } from '../supabase'
import { getAccent, getTheme, setAccent, setTheme } from '../theme'
import {
  applyTheme,
  bookStyle,
  FONT_STEP,
  fontInjectables,
  FONTS,
  framesStyle,
  loadFont,
  MARGIN_LABELS,
  MARGINS,
  MAX_FONT_SIZE,
  MIN_FONT_SIZE,
  PAGES_LABELS,
  readerPreferences,
  SPACING_LABELS,
  storedLook,
  storeLook,
  THEME_LABELS,
  type Look,
  type Margin,
  type ReaderFont,
} from './look'
import {
  durationText,
  FOOTER_ITEMS,
  pagesLeftText,
  ReadingSpeed,
  SLOT_LABELS,
  storedFooter,
  storeFooter,
  type FooterItem,
  type FooterSettings,
  type FooterSlot,
} from './footer'
import { chapterPlace, firstWordsShown, measureFile, pagesText, screensOf, type ChapterPlace, type ContentsEntry, type FileLayout } from './pages'
import { isNewerElsewhere, readPercent, ReadingProgress, SAME_PLACE, type ReadingPosition } from './progress'
import { openPublication, type ContentsLink } from './publication'

/** How long after a page turn the place is sent to Carrel. */
const SEND_AFTER_MS = 2000
/** How long the first page is given to settle before going to a place saved on another device. */
const GO_SETTLE_MS = 500

/** How long the text is given to flow again at a new size before going back to where the reader was. */
const REFLOW_MS = 300

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
   * While Readium is laying the book out (opening it, or after a change to how it looks): it reports the top
   * of the chapter before it reaches the reader's page, which isn't where they are.
   */
  settling: boolean
  /**
   * Where the reader was before changing how the book looks, to go back to once the text has flowed
   * again: its first words, which Readium finds however the text flows. Kept until they turn the page, so several
   * changes in a row all go back to the same place rather than drifting.
   */
  keep: Locator | null
}

/**
 * Reads an EPUB with Readium. Clicking the edges of the page, the arrows beside it, or the arrow keys turns the page;
 * clicking its middle hides the bars around it.
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
  const [look, setLook] = useState(storedLook)
  /** The look the book is shown in, which follows `look` once the book is open. */
  const shown = useRef(look)
  /** The chapter on the left (or only) page and the pages of it showing, for the footer. */
  const [footer, setFooter] = useState<ChapterPlace | null>(null)
  /** How many of Readium's positions make a page of the file on screen, for the time left in the chapter. */
  const [pagePositions, setPagePositions] = useState<number | null>(null)
  /** Seconds a position, once the reader's been timed (see ReadingSpeed). */
  const [speed, setSpeed] = useState<number | null>(null)
  const [footerSettings, setFooterSettings] = useState(storedFooter)
  const [contents, setContents] = useState(false)
  const [elsewhere, setElsewhere] = useState<ReadingPosition | null>(null)
  const [askingRead, setAskingRead] = useState(false)
  /** A newer version of the book, downloaded from its library while it's read, to reload into. */
  const [newer, setNewer] = useState(false)
  /** The bar of controls and the footer, hidden by clicking the middle of the page (and shown again the same way). */
  const [barsHidden, setBarsHidden] = useState(false)
  const state = useRef<ReadingState>({ progress: null, openedProgression: null, startedAt: null, moved: false, sending: null, settling: true, keep: null })
  /** Goes to a place saved on another device. */
  const goTo = useRef<(position: ReadingPosition) => void>(() => {})
  /** Takes in where the reader is (see placeChanged); `restyled` when it's from restyling, not a page turn. */
  const report = useRef<(current: Locator, restyled?: boolean) => void>(() => {})

  useEffect(() => {
    let cancelled = false
    let navigator: EpubNavigator | undefined
    let positions: Locator[] = []
    let close: (() => Promise<void>) | undefined
    let entries: ContentsEntry[] = []
    /** How many positions each file in the reading order has. */
    let filePositions: number[] = []
    /** The file on screen, measured; measured again when it, or how it's laid out, changes. */
    let layout: { key: string; measured: FileLayout } | null = null
    const speed = new ReadingSpeed()
    /** The screen the reader was last on, to tell a page turned forward from a jump. */
    let lastScreen: { file: number; screen: number } | null = null

    function placeChanged(current: Locator, restyled = false) {
      const s = state.current
      if (s.settling) return
      if (!restyled) s.keep = null
      setLocator(current)
      setSeeking(null)
      // The last stays while a file can't be measured yet, rather than flickering.
      const measured = navigator && footerPlace(navigator, current)
      if (measured) {
        setFooter(measured.place)
        setPagePositions(measured.pagePositions)
      }
      const json = JSON.stringify(current.serialize())
      const progression = current.locations?.totalProgression
      const screen = measured ? { file: measured.file, screen: measured.screen } : null
      const forward =
        !restyled &&
        screen !== null &&
        lastScreen !== null &&
        ((screen.file === lastScreen.file && screen.screen === lastScreen.screen + 1) || (screen.file === lastScreen.file + 1 && screen.screen === 0))
      lastScreen = screen
      if (progression !== undefined) {
        speed.arrived(progression, positions.length, forward)
        setSpeed(speed.estimate)
      }
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
      // Closed already (in development, React opens it twice): the library mustn't be changed twice at once.
      if (cancelled) return
      const progress = data.session && book.bookId !== null ? new ReadingProgress(book.bookId) : null
      state.current.progress = progress
      // Where a copy never opened here starts depends on the library, so that waits; otherwise it carries on alongside.
      const starting = progress?.start()
      if (book.locator === null) await starting

      // Read into memory whole, so the copy can be replaced (and its old file deleted) while it's open.
      const file = await browserBookFile(book)
      const opened = await openPublication(new Blob([await file.arrayBuffer()], { type: file.type }))
      close = opened.close
      if (cancelled) {
        void opened.close()
        return
      }
      const { publication } = opened
      positions = opened.positions
      entries = contentsEntries(opened.contents, publication)
      filePositions = publication.readingOrder.items.map(() => 0)
      for (const position of positions) {
        const file = publication.readingOrder.findIndexWithHref(position.href.split('#')[0])
        if (file >= 0) filePositions[file]++
      }
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
      await loadFont(shown.current)
      if (cancelled) return

      navigator = new EpubNavigator(holder.current!, publication, listeners({
        tap: pressed,
        click: pressed,
        frameLoaded: (wnd) => watchFrame(wnd, () => navigator),
        positionChanged: placeChanged,
        timelineItemChanged: (item) => setChapter(item?.title?.replace(/\s+/g, ' ').trim() || null),
      }), positions, start, { preferences: readerPreferences(shown.current), defaults: {}, injectables: fontInjectables() })
      await navigator.load()
      if (cancelled) return
      state.current.settling = false
      placeChanged(navigator.currentLocator)
      setOpen({ navigator, publication, positions, contents: opened.contents })
      if (progress) void syncPosition(progress)
      void checkForNewer()
    })().catch((e: unknown) => {
      if (cancelled) return
      console.error(e)
      setError(e instanceof BrowserBookError || e instanceof EpubError ? e.message : 'This book couldn’t be opened.')
    })

    /** A click or tap on the page: its outer thirds turn the page, and its middle shows or hides the bars around it. */
    function pressed(event: Parameters<EpubNavigatorListeners['tap']>[0]) {
      const wnd = navigator && frameWindow(navigator)
      if (!navigator || !wnd) return false
      const third = (wnd.innerWidth * wnd.devicePixelRatio) / 3
      if (event.x < third) navigator.goLeft(false, () => {})
      else if (event.x > third * 2) turnRight(navigator)
      else setBarsHidden((hidden) => !hidden)
      return true
    }

    /**
     * Downloads a newer version of a book from its online library while it's read, and offers it to reload into (the
     * next time it opens uses it anyway). Nothing happens if it's unchanged, or the library can't be reached, or wants
     * a password this tab doesn't have.
     */
    async function checkForNewer() {
      const source = book.source
      const lib = source && library(source.libraryId)
      if (!source || !lib) return
      try {
        const fetched = await downloadBook(lib, source.downloadUrl, source.etag, source.lastModified)
        if (fetched.unchanged || cancelled) return
        const updated = await replaceBrowserBook(book, fetched.file, fetched.etag, fetched.lastModified)
        if (!cancelled && updated.fingerprint !== book.fingerprint) setNewer(true)
      } catch {
        // Carries on with the version that's open.
      }
    }

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

    /**
     * The chapter and pages showing, measured in the frame on screen (`place` is null before the first contents entry,
     * a cover, say), with the screen and how many positions make a page; undefined if it can't be measured (a frame
     * still loading).
     */
    function footerPlace(reader: EpubNavigator, current: Locator) {
      const wnd = frameWindow(reader)
      const file = reader.publication.readingOrder.findIndexWithHref(current.href.split('#')[0])
      const screens = wnd && screensOf(wnd)
      if (!wnd || file < 0 || !screens) return undefined
      const key = [file, screens.screens, screens.perScreen, wnd.document.scrollingElement?.scrollWidth, wnd.innerWidth].join(':')
      if (layout?.key !== key) layout = { key, measured: measureFile(wnd, entries, file, screens.screens, screens.perScreen) }
      return {
        place: chapterPlace(entries, layout.measured, screens.screen * screens.perScreen, screens.perScreen),
        file,
        screen: screens.screen,
        pagePositions: filePositions[file] / layout.measured.columns,
      }
    }

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

  // Only a change of look restyles the book.
  useEffect(() => {
    if (!open || shown.current === look) return
    shown.current = look
    applyTheme(look.theme)
    void restyle(open.navigator, look, state.current, report.current)
  }, [open, look])

  // Colours follow the site's theme, which can change in another tab or with the system's.
  useEffect(() => {
    if (!open) return
    const apply = () => void restyle(open.navigator, shown.current, state.current, report.current)
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

  function changeLook(change: Partial<Look> | ((look: Look) => Partial<Look>)) {
    setLook((last) => {
      const next = { ...last, ...(typeof change === 'function' ? change(last) : change) }
      storeLook(next)
      return next
    })
  }

  function changeSize(by: number) {
    changeLook((last) => ({ fontSize: Math.round(Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, last.fontSize + by)) * 10) / 10 }))
  }

  function changeFooter(item: FooterItem, change: Partial<FooterSettings[FooterItem]>) {
    setFooterSettings((last) => {
      const next = { ...last, [item]: { ...last[item], ...change } }
      storeFooter(next)
      return next
    })
  }

  function seekTo(progression: number) {
    if (!open) return
    const { positions, navigator } = open
    navigator.go(positions[Math.min(positions.length - 1, Math.floor(progression * positions.length))], false, () => {})
  }

  const progression = locator?.locations?.totalProgression
  // While dragging the seek bar, where letting go will go, without the chapter it's leaving.
  const shownProgression = seeking ?? progression
  const texts: Record<FooterItem, ReactNode> = {
    chapter: seeking === null && (footer?.title ?? chapter),
    pages: seeking === null && footer && pagesText(footer),
    percent: shownProgression !== undefined && `${readPercent(shownProgression)}%`,
    pagesLeft: seeking === null && footer && pagesLeftText(footer),
    chapterTime:
      seeking === null && footer && speed !== null && pagePositions !== null && `${durationText((footer.pages - footer.first + 1) * pagePositions * speed)} left in chapter`,
    bookTime: open && speed !== null && shownProgression !== undefined && `${durationText((1 - shownProgression) * open.positions.length * speed)} left in book`,
    clock: <Clock />,
  }

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
    <div className={barsHidden ? 'reader bars-hidden' : 'reader'}>
      <header className="reader-bar">
        <button type="button" className="link-button" onClick={() => void close()}>
          Close
        </button>
        <h1 className="reader-title">{book.title}</h1>
        <div className="reader-tools">
          <button type="button" className="link-button" disabled={!open} onClick={() => setContents(true)}>
            Contents
          </button>
          <Popover className="reader-look-menu">
            <PopoverButton className="link-button" aria-label="How the book looks">
              Aa
            </PopoverButton>
            {/* Beside the book rather than over a dimmed one, so each change shows as it's made. */}
            <PopoverPanel anchor="bottom end" className="reader-look">
              <LookPanel look={look} changeLook={changeLook} changeSize={changeSize} footer={footerSettings} changeFooter={changeFooter} />
            </PopoverPanel>
          </Popover>
        </div>
      </header>

      {newer && (
        <div className="reader-notice" role="status">
          <span>A newer version of this book is ready.</span>
          <button
            type="button"
            className="link-button accent"
            onClick={() => {
              void flush().then(() => window.location.reload())
            }}
          >
            Reload
          </button>
          <button type="button" className="link-button" onClick={() => setNewer(false)}>
            Later
          </button>
        </div>
      )}

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
        {/*
          Readium sizes its own box (holder) for a comfortable line length; the space around it works like the page,
          by thirds.
        */}
        <div
          className="reader-book"
          style={bookStyle(look)}
          aria-busy={!open}
          onClick={(e) => {
            if (!open || e.target !== e.currentTarget) return
            const box = e.currentTarget.getBoundingClientRect()
            if (e.clientX < box.left + box.width / 3) open.navigator.goLeft(false, () => {})
            else if (e.clientX > box.left + (box.width * 2) / 3) turnRight(open.navigator)
            else setBarsHidden((hidden) => !hidden)
          }}
        >
          <div className="reader-frames" style={framesStyle(look)} ref={holder} />
        </div>
        <button type="button" className="reader-turn" aria-label="Next page" disabled={!open} onClick={() => open && turnRight(open.navigator)}>
          <span aria-hidden="true">›</span>
        </button>
      </div>

      {/* The footer with the controls, and in its place while they're hidden, the one always under the page. */}
      <footer className="reader-footer">
        <div className="reader-footer-controls">
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
          {/* The percentage shows while dragging even if it's switched off, as it's where letting go will go. */}
          <FooterRow texts={texts} shows={(item) => footerSettings[item].controls || (item === 'percent' && seeking !== null)} settings={footerSettings} />
        </div>
        <FooterRow texts={texts} shows={(item) => footerSettings[item].always} settings={footerSettings} className="reader-footer-always" />
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

/** A footer's left, centre, and right, each with its items in order, e.g. "Chapter 3 (Page 2 / 9) · 12%". */
function FooterRow({
  texts,
  shows,
  settings,
  className,
}: {
  texts: Record<FooterItem, ReactNode>
  shows: (item: FooterItem) => boolean
  settings: FooterSettings
  className?: string
}) {
  return (
    <div className={className ? `reader-footer-row ${className}` : 'reader-footer-row'}>
      {(Object.keys(SLOT_LABELS) as FooterSlot[]).map((slot) => {
        const items = (Object.keys(FOOTER_ITEMS) as FooterItem[]).filter((item) => settings[item].slot === slot && shows(item) && texts[item])
        return (
          <span key={slot} className={`reader-footer-${slot}`}>
            {items.map((item, i) => (
              <span key={item} className={item === 'chapter' ? 'reader-footer-chapter' : undefined}>
                {/* The pages go straight after the chapter's title, as "Chapter 3 (Page 2 / 9)". */}
                {i > 0 && (item === 'pages' && items[i - 1] === 'chapter' ? ' ' : ' · ')}
                {texts[item]}
              </span>
            ))}
          </span>
        )
      })}
    </div>
  )
}

/** The time, to the minute, in the browser's 12- or 24-hour style. */
function Clock() {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = window.setTimeout(() => setNow(new Date()), 60_000 - (Date.now() % 60_000))
    return () => window.clearTimeout(timer)
  }, [now])
  return now.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

type Section = 'text' | 'layout' | 'footer'

/**
 * The reader's settings: the theme, then the text, the layout, and the footer, each folded away until it's wanted, one
 * at a time (all folded each time the panel opens).
 */
function LookPanel({
  look,
  changeLook,
  changeSize,
  footer,
  changeFooter,
}: {
  look: Look
  changeLook: (change: Partial<Look>) => void
  changeSize: (by: number) => void
  footer: FooterSettings
  changeFooter: (item: FooterItem, change: Partial<FooterSettings[FooterItem]>) => void
}) {
  const [section, setSection] = useState<Section | null>(null)
  const toggle = (which: Section) => setSection(section === which ? null : which)

  return (
    <>
      <LookChoice label="Theme" labels={THEME_LABELS} value={look.theme} onChange={(theme) => changeLook({ theme })} />
      <LookSection title="Text" open={section === 'text'} onToggle={() => toggle('text')}>
        <div className="reader-look-row">
          <span className="reader-look-label" id="reader-font-label">
            Font
          </span>
          <div className="reader-fonts" role="radiogroup" aria-labelledby="reader-font-label">
            {(Object.keys(FONTS) as ReaderFont[]).map((font) => (
              <button
                key={font}
                type="button"
                role="radio"
                aria-checked={look.font === font}
                style={{ fontFamily: FONTS[font].family ?? undefined }}
                onClick={() => changeLook({ font })}
              >
                {FONTS[font].label}
              </button>
            ))}
          </div>
        </div>
        <div className="reader-look-row">
          <span className="reader-look-label">Size</span>
          <div className="reader-size">
            <button type="button" className="link-button" aria-label="Smaller text" disabled={look.fontSize <= MIN_FONT_SIZE} onClick={() => changeSize(-FONT_STEP)}>
              A−
            </button>
            <span>{Math.round(look.fontSize * 100)}%</span>
            <button type="button" className="link-button" aria-label="Larger text" disabled={look.fontSize >= MAX_FONT_SIZE} onClick={() => changeSize(FONT_STEP)}>
              A+
            </button>
          </div>
        </div>
      </LookSection>
      <LookSection title="Layout" open={section === 'layout'} onToggle={() => toggle('layout')}>
        <LookChoice label="Line spacing" labels={SPACING_LABELS} value={look.spacing} onChange={(spacing) => changeLook({ spacing })} />
        <div className="reader-look-row">
          <span className="reader-look-label">Margins</span>
          <div className="reader-margins">
            {(Object.keys(MARGINS) as Margin[]).map((margin) => (
              <Fragment key={margin}>
                <span>{MARGINS[margin]}</span>
                <OptionSwitch
                  label={`${MARGINS[margin]} margin`}
                  labels={MARGIN_LABELS}
                  value={look[margin]}
                  onChange={(step) => changeLook({ [margin]: step })}
                />
              </Fragment>
            ))}
          </div>
        </div>
        <LookChoice label="Pages side by side" labels={PAGES_LABELS} value={look.pages} onChange={(pages) => changeLook({ pages })} className="reader-look-pages" />
      </LookSection>
      <LookSection title="Footer" open={section === 'footer'} onToggle={() => toggle('footer')}>
        {/* Each item's name and footers, with where it goes under them. */}
        <div className="reader-footer-settings">
          <span />
          <span className="reader-look-label">With controls</span>
          <span className="reader-look-label">Always</span>
          {(Object.keys(FOOTER_ITEMS) as FooterItem[]).map((item) => (
            <Fragment key={item}>
              <span>{FOOTER_ITEMS[item]}</span>
              <input
                type="checkbox"
                aria-label={`${FOOTER_ITEMS[item]} with controls`}
                checked={footer[item].controls}
                onChange={(e) => changeFooter(item, { controls: e.target.checked })}
              />
              <input
                type="checkbox"
                aria-label={`${FOOTER_ITEMS[item]} always`}
                checked={footer[item].always}
                onChange={(e) => changeFooter(item, { always: e.target.checked })}
              />
              <OptionSwitch label={`Where ${FOOTER_ITEMS[item].toLowerCase()} goes`} labels={SLOT_LABELS} value={footer[item].slot} onChange={(slot) => changeFooter(item, { slot })} />
            </Fragment>
          ))}
        </div>
      </LookSection>
    </>
  )
}

/** A heading in the reader's settings that shows or hides the settings under it. */
function LookSection({ title, open, onToggle, children }: { title: string; open: boolean; onToggle: () => void; children: ReactNode }) {
  const id = useId()
  return (
    <section className="reader-look-section">
      <button type="button" className="reader-look-heading" aria-expanded={open} aria-controls={id} onClick={onToggle}>
        {title}
        <span className="disclosure-arrow" aria-hidden="true">
          ▾
        </span>
      </button>
      {open && (
        <div id={id} className="reader-look-body">
          {children}
        </div>
      )}
    </section>
  )
}

/** One of the reader's settings, as a row of choices. */
function LookChoice<T extends string>({
  label,
  labels,
  value,
  onChange,
  className,
}: {
  label: string
  labels: Record<T, string>
  value: T
  onChange: (value: T) => void
  className?: string
}) {
  return (
    <div className={className ? `reader-look-row ${className}` : 'reader-look-row'}>
      <span className="reader-look-label">{label}</span>
      <OptionSwitch label={label} labels={labels} value={value} onChange={onChange} />
    </div>
  )
}

function OptionSwitch<T extends string>({
  label,
  labels,
  value,
  onChange,
}: {
  label: string
  labels: Record<T, string>
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div className="option-switch" role="radiogroup" aria-label={label}>
      {(Object.keys(labels) as T[]).map((option) => (
        <button key={option} type="button" role="radio" aria-checked={value === option} onClick={() => onChange(option)}>
          {labels[option]}
        </button>
      ))}
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
function listeners(
  given: Pick<EpubNavigatorListeners, 'tap' | 'click' | 'frameLoaded' | 'positionChanged' | 'timelineItemChanged'>,
): EpubNavigatorListeners {
  const noop = () => {}
  return {
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
    if (event.detail > 1 && (event.clientX < width / 3 || event.clientX > (width * 2) / 3)) event.preventDefault()
  })
}

function turnByKey(event: KeyboardEvent, navigator: EpubNavigator) {
  if (event.altKey || event.ctrlKey || event.metaKey || event.defaultPrevented) return
  const target = event.target as HTMLElement | null
  if (target?.closest('input, textarea, select, [contenteditable], [role="dialog"], [role="listbox"], .reader-look')) return
  if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') navigator.goLeft(false, () => {})
  else if (event.key === 'ArrowRight' || event.key === 'ArrowDown') turnRight(navigator)
  else if (event.key === 'PageUp') navigator.goBackward(false, () => {})
  else if (event.key === 'PageDown' || (event.key === ' ' && !event.shiftKey)) nextPage(navigator)
  else return
  event.preventDefault()
}

/** Turns the page to the right: the next page in a book read left to right, the one before in one read right to left. */
function turnRight(navigator: EpubNavigator) {
  if (navigator.readingProgression === ReadingProgression.ltr) nextPage(navigator)
  else navigator.goRight(false, () => {})
}

/**
 * Turns to the next page, unless this is the book's last: there Readium shakes the page instead, and in Firefox the
 * shake leaves it at the top of the chapter.
 */
function nextPage(navigator: EpubNavigator) {
  const { readingOrder } = navigator.publication
  const wnd = frameWindow(navigator)
  const scroller = wnd?.document.scrollingElement
  const lastFile = readingOrder.findIndexWithHref(navigator.currentLocator.href.split('#')[0]) === readingOrder.items.length - 1
  if (lastFile && wnd && scroller && wnd.scrollX + wnd.innerWidth >= scroller.scrollWidth - 1) return
  navigator.goForward(false, () => {})
}

/**
 * Applies the look (with the theme's colours), then goes back to where the reader was: Readium starts the chapter again
 * when its text flows anew.
 */
async function restyle(
  navigator: EpubNavigator,
  look: Look,
  state: ReadingState,
  report: (current: Locator, restyled: boolean) => void,
) {
  if (!state.keep) state.keep = firstWordsLocator(navigator)
  const place = state.keep
  state.settling = true
  try {
    await loadFont(look)
    await navigator.submitPreferences(new EpubPreferences(readerPreferences(look)))
    // A font not shown yet arrives a moment later, and the text flows again when it does.
    const family = FONTS[look.font].family
    if (family) await frameWindow(navigator)?.document.fonts.load(`1em ${family}`)
    // The text flows again after Readium has taken the change; going sooner lands where it was laid out before.
    await new Promise((done) => window.setTimeout(done, REFLOW_MS))
  } finally {
    navigator.go(place, false, () => {
      state.settling = false
      report(navigator.currentLocator, true)
    })
  }
}

/** Where the reader is, by the first words on the page if it has any (see firstWordsShown). */
function firstWordsLocator(navigator: EpubNavigator): Locator {
  const current = navigator.currentLocator
  const wnd = frameWindow(navigator)
  const text = wnd && firstWordsShown(wnd)
  return text ? new Locator({ href: current.href, type: current.type, title: current.title, locations: current.locations, text }) : current
}

/** The window of the frame on screen. */
function frameWindow(navigator: EpubNavigator): Window | null {
  for (const frame of navigator._cframes) {
    if (frame instanceof FrameManager) return frame.window
  }
  return null
}

/** The book's contents entries, in contents order, leaving out any without a title or outside the book. */
function contentsEntries(contents: ContentsLink[], publication: Publication): ContentsEntry[] {
  return flatten(contents, 0).flatMap(({ link }) => {
    const title = link.title.replace(/\s+/g, ' ').trim()
    const [href, id] = link.href.split('#')
    const file = publication.readingOrder.findIndexWithHref(href)
    return title && file >= 0 ? [{ title, file, id: id || null }] : []
  })
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
