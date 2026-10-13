// EPUBs added to this browser: the files in its private storage (OPFS), and the list of them in localStorage. They
// belong to the browser, not an account, as books on the Android app belong to the phone: they stay when someone signs
// out, and reading one tracks progress in the library of whoever is signed in.
import { useSyncExternalStore } from 'react'

export interface BrowserBook {
  id: string
  /** The file's SHA-256, so adding the same file again finds the copy already here. */
  fingerprint: string
  title: string
  authors: string[]
  series: string | null
  seriesPosition: number | null
  /** ISBNs in the file's own details, to find its Carrel book. */
  isbns: string[]
  hasCover: boolean
  /** The Carrel book whose library entry reading this updates, and its cover, shown in place of the file's. */
  bookId: number | null
  bookCover: string | null
  /** Where the reader is, as a Readium Locator (JSON), and how far through the book, from 0 to 1. */
  locator: string | null
  progression: number | null
  addedAt: number
  openedAt: number | null
}

/** Adding or opening a book failed, with a message to show the reader. */
export class BrowserBookError extends Error {}

const LIST_KEY = 'carrel-browser-books'

function load(): BrowserBook[] {
  try {
    return JSON.parse(localStorage.getItem(LIST_KEY) ?? '[]') as BrowserBook[]
  } catch {
    return []
  }
}

let books = load()
const listeners = new Set<() => void>()

function save(next: BrowserBook[]) {
  books = next
  localStorage.setItem(LIST_KEY, JSON.stringify(next))
  for (const listener of listeners) listener()
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  // The reader is another page (and maybe another tab): its saves arrive as storage events.
  const changed = (event: StorageEvent) => {
    if (event.key !== LIST_KEY) return
    books = load()
    listener()
  }
  window.addEventListener('storage', changed)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', changed)
  }
}

/** The books in this browser, the most recently read (or added) first. */
export function useBrowserBooks(): BrowserBook[] {
  return useSyncExternalStore(subscribe, () => books)
}

export function browserBook(id: string): BrowserBook | undefined {
  return load().find((book) => book.id === id)
}

function update(id: string, changes: Partial<BrowserBook>) {
  save(load().map((book) => (book.id === id ? { ...book, ...changes } : book)))
}

/** The books in the order they're listed: most recently read (or added) first. */
export function byRecent(list: BrowserBook[]): BrowserBook[] {
  return [...list].sort((a, b) => (b.openedAt ?? b.addedAt) - (a.openedAt ?? a.addedAt))
}

async function folder(name: 'books' | 'covers') {
  const root = await navigator.storage.getDirectory()
  return root.getDirectoryHandle(name, { create: true })
}

async function write(name: 'books' | 'covers', file: string, data: Blob) {
  const writable = await (await (await folder(name)).getFileHandle(file, { create: true })).createWritable()
  await writable.write(data)
  await writable.close()
}

/**
 * Copies an EPUB into the browser and reads its title, authors, ISBNs, and cover. The same file added again gives the
 * copy already here.
 */
export async function addBrowserBook(file: File): Promise<BrowserBook> {
  const bytes = await file.arrayBuffer()
  const fingerprint = [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))]
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
  const existing = load().find((book) => book.fingerprint === fingerprint)
  if (existing) return existing

  const { Epub, EpubError, detailsOf } = await import('./epub')
  let details
  try {
    details = await detailsOf(await Epub.open(file))
  } catch (error) {
    throw new BrowserBookError(error instanceof EpubError ? error.message : 'That isn’t an EPUB Carrel can open.')
  }
  const id = crypto.randomUUID()
  try {
    // Asked as the first book is added, not on every visit: Firefox shows a prompt for it. Without it the browser may
    // clear the books when it runs short of space.
    void navigator.storage.persist?.()
    await write('books', `${id}.epub`, file)
    const cover = details.cover && (await smallCover(details.cover))
    if (cover) await write('covers', id, cover)
    const book: BrowserBook = {
      id,
      fingerprint,
      title: details.title,
      authors: details.authors,
      series: details.series,
      seriesPosition: details.seriesPosition,
      isbns: details.isbns,
      hasCover: Boolean(cover),
      bookId: null,
      bookCover: null,
      locator: null,
      progression: null,
      addedAt: Date.now(),
      openedAt: null,
    }
    save([...load(), book])
    return book
  } catch {
    await removeFiles(id)
    throw new BrowserBookError('This browser couldn’t keep the book. It may be out of space, or in a private window.')
  }
}

/** A cover small enough to list, as a JPEG; null if the picture can't be read. */
async function smallCover(picture: Blob): Promise<Blob | null> {
  try {
    const bitmap = await createImageBitmap(picture)
    const width = Math.min(COVER_WIDTH, bitmap.width)
    const canvas = new OffscreenCanvas(width, Math.round((bitmap.height * width) / bitmap.width))
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()
    return await canvas.convertToBlob({ type: 'image/jpeg', quality: 0.85 })
  } catch {
    return null
  }
}

const COVER_WIDTH = 400

/** The book's file, to read. */
export async function browserBookFile(book: BrowserBook): Promise<File> {
  try {
    return await (await (await folder('books')).getFileHandle(`${book.id}.epub`)).getFile()
  } catch {
    throw new BrowserBookError('This book’s file is no longer in this browser. Remove it and add it again.')
  }
}

/** The file's own cover, if it has one. */
export async function browserBookCover(book: Pick<BrowserBook, 'id' | 'hasCover'>): Promise<File | null> {
  if (!book.hasCover) return null
  try {
    return await (await (await folder('covers')).getFileHandle(book.id)).getFile()
  } catch {
    return null
  }
}

/** Deletes the book's copy and cover from the browser. Its library entry in Carrel stays. */
export async function removeBrowserBook(id: string) {
  save(load().filter((book) => book.id !== id))
  await removeFiles(id)
}

async function removeFiles(id: string) {
  await (await folder('books')).removeEntry(`${id}.epub`).catch(() => {})
  await (await folder('covers')).removeEntry(id).catch(() => {})
}

export function linkBrowserBook(id: string, bookId: number, cover: string | null) {
  update(id, { bookId, bookCover: cover })
}

/** Where the reader is in the book, saved as they read. */
export function saveBrowserBookPlace(id: string, locator: string, progression: number | null) {
  update(id, { locator, progression, openedAt: Date.now() })
}

/** The reader page for a book in this browser: its own page, with its own security policy, so always a full load. */
export function readerPath(book: BrowserBook) {
  return `/read/?book=${encodeURIComponent(book.id)}`
}
