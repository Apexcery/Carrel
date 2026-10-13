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
  /** The copy's name in the books folder, when it isn't `{id}.epub` (a newer version from a library). */
  file?: string
  /** The file's identifiers, lowercased, to tell another version of the same book (see sameBook). */
  identifiers?: string[]
  /** Where a book downloaded from an online library came from, and the version this browser has. */
  source?: BookSource | null
}

export interface BookSource {
  libraryId: string
  /** The book's id in the library's catalogue. */
  onlineId: string
  downloadUrl: string
  etag: string | null
  lastModified: string | null
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
export async function addBrowserBook(file: Blob): Promise<BrowserBook> {
  const fingerprint = await fingerprintOf(file)
  const existing = load().find((book) => book.fingerprint === fingerprint)
  if (existing) return existing

  const details = await readDetails(file)
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
      identifiers: details.identifiers,
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
    throw new BrowserBookError(COULDNT_KEEP)
  }
}

const COULDNT_KEEP = 'This browser couldn’t keep the book. It may be out of space, or in a private window.'

/** The file's SHA-256, as hex. */
async function fingerprintOf(file: Blob) {
  return [...new Uint8Array(await crypto.subtle.digest('SHA-256', await file.arrayBuffer()))]
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/** What a copy says about itself; it must open as an EPUB. */
async function readDetails(file: Blob) {
  const { Epub, EpubError, detailsOf } = await import('./epub')
  try {
    return await detailsOf(await Epub.open(file))
  } catch (error) {
    throw new BrowserBookError(error instanceof EpubError ? error.message : 'That isn’t an EPUB Carrel can open.')
  }
}

/** The copy of a library's book in this browser, if it's been downloaded. */
export function fromLibrary(libraryId: string, onlineId: string): BrowserBook | undefined {
  return load().find((book) => book.source?.libraryId === libraryId && book.source.onlineId === onlineId)
}

/**
 * Adds a book downloaded from an online library, remembering where it came from to fetch newer versions. A file the
 * browser already has becomes that library's copy rather than a second one, and another version of a book added by
 * hand takes that copy's place, keeping the reader's place and link.
 */
export async function addDownloadedBook(file: Blob, source: BookSource): Promise<BrowserBook> {
  const fingerprint = await fingerprintOf(file)
  let book = load().find((b) => b.fingerprint === fingerprint)
  if (!book) {
    const details = await readDetails(file)
    const byHand = load().find((b) => !b.source && sameBook(details, b))
    book = byHand ? await replaceBrowserBook(byHand, file, null, null) : await addBrowserBook(file)
  }
  update(book.id, { source })
  return load().find((b) => b.id === book.id)!
}

/**
 * Whether two copies are versions of the same book: an identifier in common (Calibre keeps its own in every copy it
 * exports), or else the same title and first author, ignoring case and punctuation.
 */
function sameBook(a: { identifiers: string[]; title: string; authors: string[] }, b: BrowserBook) {
  if (a.identifiers.some((id) => b.identifiers?.includes(id))) return true
  const plain = (text: string | undefined) => (text ?? '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '')
  const title = plain(a.title)
  const author = plain(a.authors[0])
  return title !== '' && title === plain(b.title) && author !== '' && author === plain(b.authors[0])
}

/**
 * Puts a newer version of a book in place of the old, keeping the reader's place and link. It's read before anything
 * changes, so a broken download leaves the old copy as it was. The old file stays until the books are next listed
 * (see removeOldCopies), since a reader may still have it open.
 */
export async function replaceBrowserBook(
  book: BrowserBook,
  file: Blob,
  etag: string | null,
  lastModified: string | null,
): Promise<BrowserBook> {
  const fingerprint = await fingerprintOf(file)
  const version = book.source ? { source: { ...book.source, etag, lastModified } } : {}
  if (fingerprint === book.fingerprint) {
    update(book.id, version)
    return load().find((b) => b.id === book.id)!
  }
  const details = await readDetails(file)
  const name = `${book.id}-${Date.now().toString(36)}.epub`
  try {
    await write('books', name, file)
    const cover = details.cover && (await smallCover(details.cover))
    if (cover) await write('covers', book.id, cover)
    update(book.id, {
      file: name,
      fingerprint,
      title: details.title,
      authors: details.authors,
      series: details.series,
      seriesPosition: details.seriesPosition,
      isbns: details.isbns,
      identifiers: details.identifiers,
      hasCover: Boolean(cover) || book.hasCover,
      ...version,
    })
  } catch {
    await (await folder('books')).removeEntry(name).catch(() => {})
    throw new BrowserBookError(COULDNT_KEEP)
  }
  return load().find((b) => b.id === book.id)!
}

/**
 * Deletes copies that newer versions have replaced: files no book in the list is using, and not written in the last
 * minute (one being added in another tab is written before it's listed).
 */
export async function removeOldCopies() {
  const used = new Set(load().map(fileName))
  const dir = (await folder('books')) as FileSystemDirectoryHandle & { keys(): AsyncIterable<string> }
  for await (const name of dir.keys()) {
    if (used.has(name)) continue
    const written = (await (await dir.getFileHandle(name)).getFile()).lastModified
    if (Date.now() - written > 60_000) await dir.removeEntry(name).catch(() => {})
  }
}

const fileName = (book: BrowserBook) => book.file ?? `${book.id}.epub`

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
    return await (await (await folder('books')).getFileHandle(fileName(book))).getFile()
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
  const book = load().find((b) => b.id === id)
  save(load().filter((b) => b.id !== id))
  await removeFiles(id, book && fileName(book))
}

async function removeFiles(id: string, file = `${id}.epub`) {
  await (await folder('books')).removeEntry(file).catch(() => {})
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
