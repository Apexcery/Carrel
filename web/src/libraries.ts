// Online libraries: OPDS catalogues such as Calibre's content server, browsed from the eReader's page (only its
// security policy lets the page connect to other servers). Like the books, they belong to the browser. Requests go
// straight from the browser to the library, never through Carrel, and a library's login is only sent to its own
// server.

export interface Library {
  id: string
  name: string
  /** The catalogue's address, e.g. https://books.example.com/opds. */
  url: string
  username: string | null
}

/** A catalogue page: its sections to browse, its books, the next page, and how to search it. */
export interface OnlineFeed {
  title: string
  sections: Section[]
  books: OnlineBook[]
  next: string | null
  /** The search address, with {searchTerms} where the words go. */
  search: string | null
}

export interface Section {
  title: string
  url: string
  /** Its description, such as Calibre's "12 books". */
  summary: string | null
  rels: string[]
}

/** A book in a catalogue. `epub` is where to download it, null when it isn't offered as an EPUB. */
export interface OnlineBook {
  id: string
  title: string
  authors: string[]
  summary: string | null
  cover: string | null
  thumbnail: string | null
  epub: string | null
}

/** A failed request to a library, with a message to show; `needsLogin` when it wants a username and password. */
export class LibraryError extends Error {
  readonly needsLogin: boolean

  constructor(message: string, needsLogin = false) {
    super(message)
    this.needsLogin = needsLogin
  }
}

const LIST_KEY = 'carrel-libraries'
const PASSWORDS_KEY = 'carrel-library-passwords'

export const CANT_REACH =
  'Can’t reach this library. It needs an https:// address, and its server has to allow Carrel’s website (CORS).'
const NOT_A_CATALOGUE = 'That address isn’t an OPDS catalogue. For Calibre’s server it usually ends in /opds.'
const SEARCH_TERMS = '{searchTerms}'

export function libraries(): Library[] {
  try {
    return (JSON.parse(localStorage.getItem(LIST_KEY) ?? '[]') as Library[]).sort((a, b) => a.name.localeCompare(b.name))
  } catch {
    return []
  }
}

export function library(id: string): Library | undefined {
  return libraries().find((l) => l.id === id)
}

function saveList(list: Library[]) {
  localStorage.setItem(LIST_KEY, JSON.stringify(list))
}

/**
 * The library's password: remembered in the browser if the reader chose to, otherwise kept for this tab only, so it's
 * asked again in a new tab. Null if there's none.
 */
export function password(id: string): string | null {
  for (const storage of [sessionStorage, localStorage]) {
    try {
      const saved = JSON.parse(storage.getItem(PASSWORDS_KEY) ?? '{}') as Record<string, string>
      if (saved[id]) return saved[id]
    } catch {
      // Unreadable storage counts as none.
    }
  }
  return null
}

/** Whether the library's password is remembered in the browser, rather than kept for this tab. */
export function passwordRemembered(id: string): boolean {
  try {
    return Boolean((JSON.parse(localStorage.getItem(PASSWORDS_KEY) ?? '{}') as Record<string, string>)[id])
  } catch {
    return false
  }
}

export function setPassword(id: string, value: string | null, remember: boolean) {
  for (const storage of [sessionStorage, localStorage]) {
    const saved = JSON.parse(storage.getItem(PASSWORDS_KEY) ?? '{}') as Record<string, string>
    delete saved[id]
    if (value && (storage === localStorage) === remember) saved[id] = value
    storage.setItem(PASSWORDS_KEY, JSON.stringify(saved))
  }
}

/** Adds a library, or changes one (`id`), once its catalogue has answered with these details. */
export async function saveLibrary(
  id: string | null,
  details: { name: string; url: string; username: string | null; password: string | null; remember: boolean },
): Promise<Library> {
  const url = details.url.trim()
  if (!/^https:\/\//i.test(url)) {
    throw new LibraryError(
      'The website can only reach libraries at an https:// address. For one on your home network, download the book there and add it to Carrel as a file.',
    )
  }
  const saved: Library = {
    id: id ?? crypto.randomUUID(),
    name: details.name.trim() || new URL(url).host,
    url,
    username: details.username?.trim() || null,
  }
  await loadFeed(saved, url, details.password)
  saveList([...libraries().filter((l) => l.id !== saved.id), saved])
  setPassword(saved.id, details.password, details.remember)
  return saved
}

export function removeLibrary(id: string) {
  saveList(libraries().filter((l) => l.id !== id))
  setPassword(id, null, false)
}

/**
 * Fetches from the library, sending its login (Basic) only to its own server. Catalogue pages and downloads skip the
 * browser's cache, so new books show and a check for a newer version sees the server's answer; pictures can use it.
 */
async function libraryFetch(
  lib: Library,
  url: string,
  init: { headers?: Record<string, string>; login?: string | null; cache?: RequestCache } = {},
) {
  const headers: Record<string, string> = { ...init.headers }
  const pass = init.login !== undefined ? init.login : password(lib.id)
  if (lib.username && pass !== null && new URL(url).origin === new URL(lib.url).origin) {
    headers.Authorization = 'Basic ' + toBase64(`${lib.username}:${pass}`)
  }
  let response: Response
  try {
    response = await fetch(url, { headers, cache: init.cache ?? 'no-store', credentials: 'omit', referrerPolicy: 'no-referrer' })
  } catch {
    throw new LibraryError(CANT_REACH)
  }
  if (response.status === 401 || response.status === 403) {
    throw new LibraryError(
      lib.username && pass ? 'The library turned down that username and password.' : 'This library needs a username and password.',
      true,
    )
  }
  if (!response.ok && response.status !== 304) {
    throw new LibraryError(response.status === 404 ? 'The library couldn’t find that.' : 'The library had a problem. Try again later.')
  }
  return response
}

/** Base64 of text as UTF-8, as Basic logins are sent. */
function toBase64(text: string) {
  return btoa(String.fromCharCode(...new TextEncoder().encode(text)))
}

/** A catalogue page; `login` overrides the saved password (to check new details). */
export async function loadFeed(lib: Library, url: string = lib.url, login?: string | null): Promise<OnlineFeed> {
  const response = await libraryFetch(lib, url, { headers: { Accept: 'application/atom+xml, application/xml;q=0.9, */*;q=0.8' }, login })
  const doc = new DOMParser().parseFromString(await response.text(), 'application/xml')
  const feed = doc.documentElement
  if (doc.querySelector('parsererror') || feed.localName !== 'feed') throw new LibraryError(NOT_A_CATALOGUE)
  const base = response.url || url
  return {
    title: text(child(feed, 'title')) || lib.name,
    sections: entries(feed).flatMap((entry) => {
      if (acquisition(entry)) return []
      const link = links(entry).find((l) => (l.getAttribute('type') ?? '').includes('atom+xml'))
      const href = link && resolve(link.getAttribute('href'), base)
      return href
        ? [{ title: text(child(entry, 'title')) || 'Untitled', url: href, summary: plainText(child(entry, 'content') ?? child(entry, 'summary')), rels: rel(link) }]
        : []
    }),
    books: entries(feed).flatMap((entry) => {
      if (!acquisition(entry)) return []
      const epub = links(entry).find((l) => rel(l).some((r) => r.startsWith(ACQUISITION)) && (l.getAttribute('type') ?? '').startsWith('application/epub+zip'))
      const image = (thumb: boolean) => {
        const link = links(entry).find((l) => rel(l).some((r) => (thumb ? THUMBNAILS : COVERS).includes(r)))
        return link ? resolve(link.getAttribute('href'), base) : null
      }
      const epubUrl = epub ? resolve(epub.getAttribute('href'), base) : null
      const id = text(child(entry, 'id')) || epubUrl
      if (!id) return []
      const thumbnail = sharperThumbnail(image(true))
      return [{
        id,
        title: text(child(entry, 'title')) || 'Untitled',
        authors: [...entry.children].filter((el) => el.localName === 'author').map((el) => text(child(el, 'name'))).filter(Boolean),
        summary: plainText(child(entry, 'content') ?? child(entry, 'summary')),
        cover: image(false) ?? thumbnail,
        thumbnail: thumbnail ?? image(false),
        epub: epubUrl,
      }]
    }),
    next: resolve(links(feed).find((l) => rel(l).includes('next'))?.getAttribute('href') ?? null, base),
    search: await searchTemplate(lib, links(feed).find((l) => rel(l).includes('search')), base, login),
  }
}

const ACQUISITION = 'http://opds-spec.org/acquisition'
// OPDS 1.1's names for pictures, and the older ones some servers still send.
const COVERS = ['http://opds-spec.org/image', 'http://opds-spec.org/cover']
const THUMBNAILS = ['http://opds-spec.org/image/thumbnail', 'http://opds-spec.org/thumbnail']

const child = (el: Element, name: string) => [...el.children].find((c) => c.localName === name)
const entries = (feed: Element) => [...feed.children].filter((el) => el.localName === 'entry')
const links = (el: Element) => [...el.children].filter((c) => c.localName === 'link')
const rel = (link: Element) => (link.getAttribute('rel') ?? '').split(/\s+/)
const text = (el: Element | undefined) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
const acquisition = (entry: Element) => links(entry).some((l) => rel(l).some((r) => r.startsWith(ACQUISITION)))

/** A link's address made absolute against the page it's on; null if it isn't an http(s) address. */
function resolve(href: string | null, base: string): string | null {
  if (!href) return null
  try {
    const url = new URL(href, base)
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null
  } catch {
    return null
  }
}

/**
 * Calibre's thumbnails (/get/thumb/…) are 60 by 80 unless asked for more, too small for the catalogue's covers, so a
 * larger one is asked for (Calibre makes it once and keeps it).
 */
function sharperThumbnail(url: string | null): string | null {
  if (!url) return null
  const address = new URL(url)
  if (!address.pathname.includes('/get/thumb/') || address.searchParams.has('sz')) return url
  address.searchParams.set('sz', '300x400')
  return address.href
}

/** A book's summary as plain paragraphs; HTML in it is read as text, never put on the page. */
function plainText(el: Element | undefined): string | null {
  if (!el) return null
  const html = el.getAttribute('type') === 'xhtml' || el.getAttribute('type') === 'html'
  const source = el.getAttribute('type') === 'html' ? (el.textContent ?? '') : html ? new XMLSerializer().serializeToString(el) : null
  const body = source !== null ? new DOMParser().parseFromString(source, 'text/html').body : null
  if (body) {
    for (const hidden of body.querySelectorAll('script, style')) hidden.remove()
    for (const br of body.querySelectorAll('br')) br.replaceWith('\n')
    for (const block of body.querySelectorAll('p, div, li')) block.append('\n\n')
  }
  const result = (body ? body.textContent ?? '' : el.textContent ?? '').replace(/[ \t]+/g, ' ').replace(/\n\s*\n\s*/g, '\n\n').trim()
  return result || null
}

/** The search address: an OpenSearch description (Calibre's servers) is fetched for the Atom template inside it. */
async function searchTemplate(lib: Library, link: Element | undefined, base: string, login?: string | null): Promise<string | null> {
  const href = link?.getAttribute('href') ?? null
  if (!link || !href) return null
  if (!(link.getAttribute('type') ?? '').includes('opensearchdescription')) return href.includes(SEARCH_TERMS) ? resolveTemplate(href, base) : null
  try {
    const url = resolve(href, base)
    if (!url) return null
    const description = new DOMParser().parseFromString(await (await libraryFetch(lib, url, { login })).text(), 'application/xml')
    const template = [...description.getElementsByTagNameNS('*', 'Url')]
      .find((u) => (u.getAttribute('type') ?? '').includes('atom'))
      ?.getAttribute('template')
    return template ? resolveTemplate(template, url) : null
  } catch {
    return null
  }
}

/** A search template made absolute, keeping {searchTerms} for the words and dropping optional parameters. */
function resolveTemplate(template: string, base: string): string | null {
  const plain = template.replace(/\{[^}]*\?\}/g, '').replace(SEARCH_TERMS, 'SEARCHTERMS')
  const url = resolve(plain, base)
  return url ? url.replace('SEARCHTERMS', SEARCH_TERMS) : null
}

/** The section of the newest books, as on Android: OPDS's sort/new, Calibre's "Newest", or one called something like it. */
export function newestSection(sections: Section[]): Section | undefined {
  return (
    sections.find((s) => s.rels.includes('http://opds-spec.org/sort/new')) ??
    sections.find((s) => s.url.includes('/navcatalog/4f6e6577657374')) ??
    sections.find((s) => /\b(newest|recently added|new books|latest)\b/i.test(s.title))
  )
}

/** Where a catalogue search for `words` is. */
export function searchUrl(feed: OnlineFeed, words: string): string | null {
  return feed.search ? feed.search.replace(SEARCH_TERMS, encodeURIComponent(words.trim())) : null
}

/**
 * A picture from the library (covers can need its login), as a blob. From the browser's cache when it has it, since
 * each catalogue page is a fresh page load.
 */
export async function libraryPicture(lib: Library, url: string): Promise<Blob> {
  return (await libraryFetch(lib, url, { cache: 'default' })).blob()
}

/** The result of downloading a book: unchanged since the version the browser has, or the file, with its version. */
export type Download = { unchanged: true } | { unchanged: false; file: Blob; etag: string | null; lastModified: string | null }

/** Downloads a book, unless it's unchanged since the version with `etag` or `lastModified`. */
export async function downloadBook(lib: Library, url: string, etag: string | null = null, lastModified: string | null = null): Promise<Download> {
  const headers: Record<string, string> = {}
  if (etag) headers['If-None-Match'] = etag
  if (lastModified) headers['If-Modified-Since'] = lastModified
  const response = await libraryFetch(lib, url, { headers })
  if (response.status === 304) return { unchanged: true }
  let file: Blob
  try {
    file = await response.blob()
  } catch {
    throw new LibraryError(CANT_REACH)
  }
  return { unchanged: false, file, etag: response.headers.get('ETag'), lastModified: response.headers.get('Last-Modified') }
}
