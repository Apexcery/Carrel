// What the eReader's footers show, and how fast the reader reads, kept in this browser apart from the look (changing
// them doesn't lay the book out again). As in the Android app (reader/ReaderFooter.kt).
import type { ChapterPlace } from './pages'

/** The footers' items, in the order they go in a slot. */
export type FooterItem = 'chapter' | 'pages' | 'percent' | 'pagesLeft' | 'chapterTime' | 'bookTime' | 'clock'

export type FooterSlot = 'left' | 'centre' | 'right'

/** The footers: the one with the controls (under the seek bar), and the one always under the page while they're hidden. */
export type Footer = 'controls' | 'always'

/** Where an item goes in a footer, or off to leave it out. */
export type FooterPlace = FooterSlot | 'off'

export type FooterPlaces = Record<FooterItem, FooterPlace>

export type FooterSettings = Record<Footer, FooterPlaces>

export const FOOTER_ITEMS: Record<FooterItem, string> = {
  chapter: 'Chapter',
  pages: 'Chapter pages',
  percent: 'Percentage read',
  pagesLeft: 'Pages left in chapter',
  chapterTime: 'Time left in chapter',
  bookTime: 'Time left in book',
  clock: 'Current time',
}

export const SLOT_LABELS: Record<FooterSlot, string> = { left: 'Left', centre: 'Centre', right: 'Right' }

export const PLACE_LABELS: Record<FooterPlace, string> = { off: 'Off', ...SLOT_LABELS }

export const FOOTERS: Record<Footer, string> = { controls: 'With controls', always: 'While reading' }

const DEFAULT_FOOTER: FooterSettings = {
  controls: { chapter: 'left', pages: 'left', percent: 'right', pagesLeft: 'off', chapterTime: 'off', bookTime: 'off', clock: 'centre' },
  always: { chapter: 'left', pages: 'left', percent: 'off', pagesLeft: 'off', chapterTime: 'off', bookTime: 'off', clock: 'centre' },
}

const FOOTER_KEY = 'carrel-reader-footer'
const SPEED_KEY = 'carrel-reader-speed'

export function storedFooter(): FooterSettings {
  let saved: Record<string, Record<string, unknown> | undefined> = {}
  try {
    saved = JSON.parse(localStorage.getItem(FOOTER_KEY) ?? '{}') ?? {}
  } catch {
    // The defaults.
  }
  // Saved before each footer had its own places, as each item's slot and a switch for each footer.
  const old = !('controls' in saved) && !('always' in saved)
  const settings = { controls: {}, always: {} } as FooterSettings
  for (const footer of Object.keys(FOOTERS) as Footer[]) {
    for (const item of Object.keys(FOOTER_ITEMS) as FooterItem[]) {
      let place: unknown = saved[footer]?.[item]
      if (old) {
        const { slot, [footer]: shown } = saved[item] ?? {}
        place = typeof shown === 'boolean' && isSlot(slot) ? (shown ? slot : 'off') : undefined
      }
      settings[footer][item] = typeof place === 'string' && Object.hasOwn(PLACE_LABELS, place) ? (place as FooterPlace) : DEFAULT_FOOTER[footer][item]
    }
  }
  return settings
}

function isSlot(value: unknown): value is FooterSlot {
  return typeof value === 'string' && Object.hasOwn(SLOT_LABELS, value)
}

export function storeFooter(settings: FooterSettings) {
  try {
    localStorage.setItem(FOOTER_KEY, JSON.stringify(settings))
  } catch {
    // Kept for this visit only.
  }
}

/** Page turns quicker than this are flicking through, and slower ones a pause, so neither counts towards the speed. */
const QUICKEST_TURN_MS = 4_000
const SLOWEST_TURN_MS = 5 * 60_000
/** Turns timed before the speed is shown, unless it's been timed before. */
const TURNS_BEFORE_SHOWN = 3
/** Each turn counts for at least this much of the speed, so it follows the reader as they settle in. */
const LEAST_WEIGHT = 0.1

/**
 * How fast the reader reads, in seconds a Readium position (about the same amount of text, however it's laid out), from
 * the time between turning one page forward and the next.
 */
export class ReadingSpeed {
  private secondsPerPosition: number | null = null
  private turns = 0
  private last: { progression: number; at: number } | null = null

  constructor() {
    try {
      const saved = JSON.parse(localStorage.getItem(SPEED_KEY) ?? 'null')
      if (typeof saved?.secondsPerPosition === 'number' && saved.secondsPerPosition > 0 && typeof saved.turns === 'number') {
        this.secondsPerPosition = saved.secondsPerPosition
        this.turns = saved.turns
      }
    } catch {
      // Timed afresh.
    }
  }

  /** Seconds a position, once there's enough to go on. */
  get estimate(): number | null {
    return this.turns >= TURNS_BEFORE_SHOWN ? this.secondsPerPosition : null
  }

  /**
   * Takes in where the reader is: `forward` when they've turned one page on from the last place, which times how long
   * it took them; anywhere else (opening the book, a jump) starts timing afresh from there.
   */
  arrived(progression: number, positions: number, forward: boolean, at = Date.now()) {
    const last = this.last
    this.last = { progression, at }
    if (!forward || !last) return
    const took = at - last.at
    const read = (progression - last.progression) * positions
    if (took < QUICKEST_TURN_MS || took > SLOWEST_TURN_MS || read <= 0) return
    const seconds = took / 1000 / read
    this.turns++
    const weight = Math.max(LEAST_WEIGHT, 1 / this.turns)
    this.secondsPerPosition = this.secondsPerPosition === null ? seconds : this.secondsPerPosition * (1 - weight) + seconds * weight
    try {
      localStorage.setItem(SPEED_KEY, JSON.stringify({ secondsPerPosition: this.secondsPerPosition, turns: this.turns }))
    } catch {
      // Kept for this visit only.
    }
  }
}

/** The pages of the chapter after those showing, e.g. "4 pages left in chapter". */
export function pagesLeftText(place: ChapterPlace) {
  const left = place.pages - place.last
  if (left === 0) return 'Last page in chapter'
  return left === 1 ? '1 page left in chapter' : `${left} pages left in chapter`
}

/** A time left, e.g. "12 min" or "2 h 5 min", to the minute. */
export function durationText(seconds: number) {
  const minutes = Math.round(seconds / 60)
  if (minutes < 1) return 'Less than a minute'
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  return minutes % 60 ? `${hours} h ${minutes % 60} min` : `${hours} h`
}
