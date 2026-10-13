// What reading a linked book does to the reader's library, as in the Android app (reader/ReadingProgress.kt).
import { ApiError, apiGetOrNull, apiSend } from '../api'
import { localToday } from '../format'
import type { LibraryEntry, SaveEntryRequest } from '../types'

/** Where the reader is in a book, as Carrel saves it: a Readium Locator (JSON), from any device. */
export interface ReadingPosition {
  locator: string
  progression: number
  updatedAt: string
}

/** How far through (Readium's totalProgression) counts as the end, since the last page rarely reaches 1. */
export const FINISHED_AT = 0.98

/** Progressions closer than this (about a page in a long book) are the same place. */
export const SAME_PLACE = 0.002

/** Percent read between saves while reading. */
const SAVE_EVERY = 5

/** A whole percentage read, from Readium's totalProgression (0 to 1). */
export const readPercent = (totalProgression: number) => Math.min(100, Math.max(0, Math.round(totalProgression * 100)))

/**
 * Opening the book moves it to Reading (adding it to the library if it isn't there). The percentage read saves every
 * few percent and on leaving, replacing any page count. It's never marked as read without asking: closing it at the
 * end asks first.
 */
export class ReadingProgress {
  private readonly bookId: number
  private entry: LibraryEntry | null = null
  /** Only books being read save progress; a finished or abandoned one being looked at again doesn't. */
  private tracking = false
  /** Where the book opened: nothing saves until the reader moves from it, so opening a new copy can't reset progress. */
  private openedAt: number | null = null
  private percent: number | null = null
  private savedPercent: number | null = null
  private progression: number | null = null
  /** One change to the library at a time, in order. */
  private queue: Promise<unknown> = Promise.resolve()

  constructor(bookId: number) {
    this.bookId = bookId
  }

  private run<T>(task: () => Promise<T>): Promise<T> {
    const next = this.queue.then(task, task)
    this.queue = next.catch(() => {})
    return next
  }

  private async saveEntry(changes: Partial<SaveEntryRequest>) {
    const entry = this.entry
    this.entry = await apiSend<LibraryEntry>('PUT', `/library/books/${this.bookId}`, {
      status: entry?.status ?? 'want_to_read',
      editionId: entry?.editionId ?? null,
      rating: entry?.rating ?? null,
      progressUnit: entry?.progressUnit ?? null,
      progressValue: entry?.progressValue ?? null,
      ...changes,
      today: localToday(),
    } satisfies SaveEntryRequest)
  }

  /** Moves the book to Reading, adding it to the library if it isn't there; it's read without being tracked if that fails. */
  start() {
    return this.run(async () => {
      try {
        this.entry = await apiGetOrNull<LibraryEntry>(`/library/books/${this.bookId}`)
        const status = this.entry?.status
        if (!status || status === 'want_to_read' || status === 'paused') await this.saveEntry({ status: 'reading' })
      } catch {
        // Most likely no connection: the book is read without being tracked.
      }
      this.tracking = this.entry?.status === 'reading'
      this.savedPercent = this.entry?.progressUnit === 'percent' && this.entry.progressValue !== null ? Math.round(this.entry.progressValue) : null
    })
  }

  /**
   * How far through the library says the book is, for a copy never opened before to start at (e.g. after reading it
   * on paper or in another app); null to start at the beginning. Call after start.
   */
  startingPercent(): Promise<number | null> {
    return this.run(async () => {
      const entry = this.entry
      const percent = entry && (entry.status === 'reading' || entry.status === 'paused') ? entry.progressPercent : null
      return percent !== null && percent > 0 && percent < 100 ? percent : null
    })
  }

  /** Where the reader has got to (Readium's totalProgression, 0 to 1); true when it's time to save. */
  moved(totalProgression: number | undefined): boolean {
    if (totalProgression === undefined) return false
    this.progression = totalProgression
    const now = readPercent(totalProgression)
    if (this.openedAt === null) this.openedAt = now
    if (this.percent === null && now === this.openedAt) return false
    this.percent = now
    const last = this.savedPercent
    return this.tracking && (last === null || Math.abs(now - last) >= SAVE_EVERY)
  }

  /** Saves the percentage, if it's changed since the last save. */
  save() {
    return this.run(async () => {
      const now = this.percent
      if (now === null || !this.tracking || now === this.savedPercent || this.entry?.status !== 'reading') return
      try {
        await this.saveEntry({ progressUnit: 'percent', progressValue: now })
        this.savedPercent = now
      } catch {
        // Tried again at the next save.
      }
    })
  }

  /** Whether closing should ask to mark the book as read: it's at the end, in the library, and not read yet. */
  atEnd(): Promise<boolean> {
    return this.run(async () => (this.progression ?? 0) >= FINISHED_AT && this.entry !== null && this.entry.status !== 'read')
  }

  markRead() {
    return this.run(async () => {
      await this.saveEntry({ status: 'read' })
      this.tracking = false
    })
  }

  /** Where the reader is in this book as Carrel has it, saved from any device; null if it isn't, or Carrel can't be reached. */
  async savedPosition(): Promise<ReadingPosition | null> {
    try {
      return await apiGetOrNull<ReadingPosition>(`/library/books/${this.bookId}/position`)
    } catch {
      return null
    }
  }

  /**
   * Saves where the reader is to Carrel, for another device to carry on from; `at` is when they were there. It's
   * turned away (409) if another device was read more recently, which is left as it is.
   */
  async savePosition(locator: string, progression: number, at: Date) {
    try {
      await apiSend('PUT', `/library/books/${this.bookId}/position`, {
        locator,
        progression: Math.min(1, Math.max(0, progression)),
        changedAt: at.toISOString(),
      })
    } catch (error) {
      if (!(error instanceof ApiError)) throw error
    }
  }
}

/**
 * Whether a place saved in Carrel is worth going to from where this copy is (`localAt`, null if it's never been read
 * here): it's more recent, by more than a moment, and somewhere else in the book.
 */
export function isNewerElsewhere(remote: ReadingPosition, localAt: number | null, localProgression: number | null): boolean {
  const remoteAt = Date.parse(remote.updatedAt)
  if (Number.isNaN(remoteAt)) return false
  if (localAt !== null && remoteAt <= localAt + 1000) return false
  return localProgression === null || Math.abs(remote.progression - localProgression) >= SAME_PLACE
}
