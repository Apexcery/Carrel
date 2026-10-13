// Reading an EPUB in the browser: its files, and what its package document (the .opf) says about the book.
import { BlobReader, TextWriter, Uint8ArrayWriter, ZipReader, configure, type FileEntry } from '@zip.js/zip.js'

// zip.js would start its workers from blob: URLs, which the site's script policy blocks. Books are small enough to
// unzip on the page.
configure({ useWebWorkers: false })

const DC = 'http://purl.org/dc/elements/1.1/'
const OPF = 'http://www.idpf.org/2007/opf'

/** An EPUB that couldn't be read, with a message to show the reader. */
export class EpubError extends Error {}

const NOT_EPUB = 'That isn’t an EPUB Carrel can open.'

/** The files in an EPUB, by their path from the top of the zip. */
export class Epub {
  private readonly entries = new Map<string, FileEntry>()

  static async open(file: Blob): Promise<Epub> {
    const epub = new Epub()
    try {
      for (const entry of await new ZipReader(new BlobReader(file)).getEntries()) {
        if (!entry.directory) epub.entries.set(entry.filename, entry)
      }
    } catch {
      throw new EpubError(NOT_EPUB)
    }
    return epub
  }

  has(path: string) {
    return this.entries.has(path)
  }

  /** The file's size as stored in the zip, which Readium's positions are counted from. */
  compressedSize(path: string) {
    return this.entries.get(path)?.compressedSize ?? 0
  }

  size(path: string) {
    return this.entries.get(path)?.uncompressedSize ?? 0
  }

  bytes(path: string): Promise<Uint8Array> {
    return this.entry(path).getData(new Uint8ArrayWriter())
  }

  text(path: string): Promise<string> {
    return this.entry(path).getData(new TextWriter())
  }

  private entry(path: string) {
    const entry = this.entries.get(path)
    if (!entry) throw new EpubError(NOT_EPUB)
    return entry
  }

  /** The package document: where it is, and its contents. */
  async packageDocument(): Promise<{ path: string; opf: Document }> {
    const container = parseXml(await this.text('META-INF/container.xml'))
    const path = container.querySelector('rootfile')?.getAttribute('full-path')
    if (!path || !this.has(path)) throw new EpubError(NOT_EPUB)
    return { path, opf: parseXml(await this.text(path)) }
  }
}

function parseXml(text: string) {
  const doc = new DOMParser().parseFromString(text, 'application/xml')
  if (doc.querySelector('parsererror')) throw new EpubError(NOT_EPUB)
  return doc
}

/** A path in the zip from a reference in the file at `from`; undefined for anything outside the book. */
export function resolvePath(ref: string, from: string): string | undefined {
  if (!ref || /^(?:[a-z][a-z0-9+.-]*:|#|\/\/)/i.test(ref)) return undefined
  const url = new URL(ref, 'https://book.invalid/' + from)
  return decodeURIComponent(url.pathname.slice(1))
}

/** A file in the package's manifest: its path in the zip, media type, and properties (e.g. cover-image). */
export interface ManifestItem {
  id: string
  href: string
  type: string
  properties: string[]
}

export function manifestItems(opf: Document, opfPath: string): ManifestItem[] {
  return [...opf.getElementsByTagNameNS(OPF, 'item')].flatMap((item) => {
    const href = resolvePath(item.getAttribute('href') ?? '', opfPath)
    return href
      ? [{
        id: item.getAttribute('id') ?? '',
        href,
        type: item.getAttribute('media-type') ?? 'application/octet-stream',
        properties: (item.getAttribute('properties') ?? '').split(/\s+/).filter(Boolean),
      }]
      : []
  })
}

/** What an EPUB says about itself. */
export interface EpubDetails {
  title: string
  authors: string[]
  /** The series it says it's in (Calibre's, or EPUB 3's), and its number in it. */
  series: string | null
  seriesPosition: number | null
  /** ISBNs among its identifiers, to find its Carrel book. */
  isbns: string[]
  cover: Blob | null
}

export async function detailsOf(epub: Epub): Promise<EpubDetails> {
  const { path, opf } = await epub.packageDocument()
  const text = (el: Element | undefined) => el?.textContent?.replace(/\s+/g, ' ').trim() || null
  const metas = [...opf.getElementsByTagNameNS(OPF, 'meta')]
  const calibre = (name: string) => metas.find((m) => m.getAttribute('name') === name)?.getAttribute('content') ?? null
  const collection = metas.find((m) => m.getAttribute('property') === 'belongs-to-collection')
  const collectionId = collection?.getAttribute('id')
  const groupPosition = collectionId
    ? metas.find((m) => m.getAttribute('property') === 'group-position' && m.getAttribute('refines') === `#${collectionId}`)
    : undefined
  const series = calibre('calibre:series') ?? text(collection)
  const position = Number(calibre('calibre:series_index') ?? text(groupPosition) ?? NaN)

  return {
    title: text(opf.getElementsByTagNameNS(DC, 'title')[0]) ?? 'Untitled',
    authors: [...opf.getElementsByTagNameNS(DC, 'creator')].map((el) => text(el)).filter((name) => name !== null),
    series,
    seriesPosition: series && Number.isFinite(position) ? position : null,
    isbns: isbnsIn(opf),
    cover: await coverOf(epub, opf, path),
  }
}

/**
 * The ISBNs among the package's identifiers (dc:identifier), in order, e.g. urn:isbn:9780141439518 or
 * 978-0-14-143951-8.
 */
function isbnsIn(opf: Document): string[] {
  const isbns = [...opf.getElementsByTagNameNS(DC, 'identifier')]
    .map((el) => el.textContent?.trim() ?? '')
    .filter((id) => /^(?:urn:isbn:|isbn:?\s*)?\d[\d -]{8,15}[\dX]$/i.test(id))
    .map(validIsbn)
    .filter((isbn) => isbn !== null)
  return [...new Set(isbns)]
}

/** An ISBN-10 or ISBN-13 with only its digits (and X), if its check digit is right. */
function validIsbn(text: string): string | null {
  const isbn = text.replace(/[^\dXx]/g, '').toUpperCase()
  const digits = [...isbn].map((c) => (c === 'X' ? 10 : Number(c)))
  if (isbn.length === 10 && /^\d{9}/.test(isbn)) {
    return digits.reduce((sum, d, i) => sum + (10 - i) * d, 0) % 11 === 0 ? isbn : null
  }
  if (isbn.length === 13 && /^\d{13}$/.test(isbn)) {
    return digits.reduce((sum, d, i) => sum + d * (i % 2 === 0 ? 1 : 3), 0) % 10 === 0 ? isbn : null
  }
  return null
}

/** The cover picture: EPUB 3's cover-image, else EPUB 2's <meta name="cover">; null without one. */
async function coverOf(epub: Epub, opf: Document, opfPath: string): Promise<Blob | null> {
  const items = manifestItems(opf, opfPath)
  const coverId = [...opf.getElementsByTagNameNS(OPF, 'meta')].find((m) => m.getAttribute('name') === 'cover')
    ?.getAttribute('content')
  const item = items.find((i) => i.properties.includes('cover-image')) ?? items.find((i) => i.id === coverId)
  if (!item || !item.type.startsWith('image/') || !epub.has(item.href)) return null
  return new Blob([await epub.bytes(item.href) as BlobPart], { type: item.type })
}
