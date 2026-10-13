// An EPUB as a Readium publication, converted in the browser: Readium TS expects a server to have turned the book into
// a Readium Web Publication, so the package document becomes its manifest here, and a Fetcher reads the zip.
import { Link, Locator, Manifest, Publication, Resource, type Fetcher } from '@readium/shared'
import { Epub, EpubError, manifestItems, resolvePath, type ManifestItem } from '../epub'

/**
 * Where Readium takes the book's files to be. Nothing is fetched from it: each chapter's stylesheets, pictures, and
 * fonts are rewritten to blob: URLs (Chrome doesn't send a blob: frame's requests through the page's service worker,
 * so the frames can't load them from a URL the page serves). It's under the reader's own address so the <base> Readium
 * gives each chapter passes the page's base-uri 'self'.
 */
const BASE = new URL('/read/book/', window.location.origin).href

const HTML_TYPES = new Set(['application/xhtml+xml', 'text/html'])

/** Blob: URLs for the book's files, made once each; stylesheets have their own references rewritten first. */
class BookFiles {
  private readonly urls = new Map<string, Promise<string>>()
  private readonly epub: Epub
  private readonly types: Map<string, string>

  constructor(epub: Epub, types: Map<string, string>) {
    this.epub = epub
    this.types = types
  }

  type(path: string) {
    return this.types.get(path) ?? 'application/octet-stream'
  }

  blobUrl(path: string): Promise<string> {
    let url = this.urls.get(path)
    if (!url) {
      url = (async () => {
        const type = this.type(path)
        const body = type === 'text/css' ? await this.rewriteCss(await this.epub.text(path), path) : await this.epub.bytes(path)
        return URL.createObjectURL(new Blob([body as BlobPart], { type }))
      })()
      this.urls.set(path, url)
    }
    return url
  }

  /** Revokes every blob: URL made for the book, once it's closed. */
  async close() {
    for (const url of this.urls.values()) URL.revokeObjectURL(await url.catch(() => ''))
    this.urls.clear()
  }

  async rewriteCss(css: string, from: string): Promise<string> {
    const refs = [...css.matchAll(/url\(\s*(['"]?)([^'")]+)\1\s*\)|@import\s+(['"])([^'"]+)\3/g)]
    for (const m of refs) {
      const path = resolvePath(m[2] ?? m[4], from)
      if (!path || !this.epub.has(path)) continue
      const url = await this.blobUrl(path)
      css = css.replace(m[0], m[2] !== undefined ? `url("${url}")` : `@import "${url}"`)
    }
    return css
  }

  /**
   * A chapter with its stylesheets, pictures, and media pointing at blob: URLs, and without anything that could run
   * on Carrel's page: a book's scripts would reach it (the frames share its origin), and the reader's policy has to
   * allow blob: scripts for Readium's own. Nested documents (iframe, object, embed) aren't rewritten, so the page's
   * policy blocks them rather than loading something not cleaned here.
   */
  async rewriteHtml(html: string, from: string, type: string): Promise<string> {
    const doc = new DOMParser().parseFromString(html, type === 'text/html' ? 'text/html' : 'application/xhtml+xml')
    // In any namespace, so SVG's scripts go too.
    for (const script of [...doc.getElementsByTagNameNS('*', 'script')]) script.remove()
    for (const el of doc.querySelectorAll('*')) {
      for (const attr of [...el.attributes]) {
        if (/^on/i.test(attr.name)) el.removeAttributeNode(attr)
      }
    }
    const swap = async (el: Element, attr: string, ns?: string) => {
      const ref = ns ? el.getAttributeNS(ns, attr.split(':').pop()!) : el.getAttribute(attr)
      const path = ref ? resolvePath(ref, from) : undefined
      if (!path || !this.epub.has(path)) return
      const url = await this.blobUrl(path)
      if (ns) el.setAttributeNS(ns, attr, url)
      else el.setAttribute(attr, url)
    }
    for (const el of doc.querySelectorAll('link[href]')) await swap(el, 'href')
    for (const el of doc.querySelectorAll('img[src], audio[src], video[src], source[src]')) await swap(el, 'src')
    for (const el of doc.querySelectorAll('video[poster]')) await swap(el, 'poster')
    for (const el of doc.getElementsByTagNameNS('http://www.w3.org/2000/svg', 'image')) {
      if (el.hasAttributeNS('http://www.w3.org/1999/xlink', 'href')) await swap(el, 'xlink:href', 'http://www.w3.org/1999/xlink')
      else await swap(el, 'href')
    }
    for (const el of doc.querySelectorAll('style')) el.textContent = await this.rewriteCss(el.textContent ?? '', from)
    for (const el of doc.querySelectorAll('[style]')) el.setAttribute('style', await this.rewriteCss(el.getAttribute('style')!, from))
    return new XMLSerializer().serializeToString(doc)
  }
}

class ZipResource extends Resource {
  private readonly files: BookFiles
  private readonly epub: Epub
  private readonly _link: Link

  constructor(files: BookFiles, epub: Epub, link: Link) {
    super()
    this.files = files
    this.epub = epub
    this._link = link
  }

  link() {
    return Promise.resolve(this._link)
  }

  length() {
    return Promise.resolve(this.epub.size(this._link.href))
  }

  async read(): Promise<Uint8Array | undefined> {
    const path = this._link.href
    if (!this.epub.has(path)) return undefined
    const type = this.files.type(path)
    if (HTML_TYPES.has(type)) {
      return new TextEncoder().encode(await this.files.rewriteHtml(await this.epub.text(path), path, type))
    }
    return this.epub.bytes(path)
  }

  close() {}
}

class ZipFetcher implements Fetcher {
  private readonly files: BookFiles
  private readonly epub: Epub

  constructor(files: BookFiles, epub: Epub) {
    this.files = files
    this.epub = epub
  }

  links() {
    return []
  }

  get(link: Link) {
    return new ZipResource(this.files, this.epub, link)
  }

  close() {}
}

/** A book open for Readium: the publication, its positions, and closing it. */
export interface OpenPublication {
  publication: Publication
  positions: Locator[]
  contents: ContentsLink[]
  close(): Promise<void>
}

/** Opens an EPUB as a Readium publication, with its positions counted as the Android app's Readium counts them. */
export async function openPublication(file: Blob): Promise<OpenPublication> {
  const epub = await Epub.open(file)
  const { path: opfPath, opf } = await epub.packageDocument()
  const items = manifestItems(opf, opfPath)
  const byId = new Map(items.map((item) => [item.id, item]))
  const spine = [...opf.getElementsByTagNameNS('http://www.idpf.org/2007/opf', 'itemref')]
    .map((ref) => byId.get(ref.getAttribute('idref') ?? ''))
    .filter((item) => item !== undefined && epub.has(item.href))
    .map((item) => item!)
  if (spine.length === 0) throw new EpubError('That isn’t an EPUB Carrel can open.')
  const inSpine = new Set(spine.map((item) => item.href))
  const dc = (name: string) => opf.getElementsByTagNameNS('http://purl.org/dc/elements/1.1/', name)[0]?.textContent?.trim()
  const link = (item: (typeof items)[number]) => ({
    href: item.href,
    type: item.type,
    ...(item.properties.length ? { properties: { contains: item.properties } } : {}),
    ...(item.properties.includes('nav') ? { rel: 'contents' } : {}),
  })

  const contents = await contentsOf(epub, opf, items)
  const manifest = Manifest.deserialize({
    '@context': 'https://readium.org/webpub-manifest/context.jsonld',
    metadata: {
      title: dc('title') || 'Untitled',
      language: dc('language') || 'en',
      conformsTo: 'https://readium.org/webpub-manifest/profiles/epub',
    },
    links: [{ rel: 'self', href: BASE + 'manifest.json', type: 'application/webpub+json' }],
    readingOrder: spine.map(link),
    resources: items.filter((item) => !inSpine.has(item.href)).map(link),
    toc: contents,
  })
  if (!manifest) throw new EpubError('That isn’t an EPUB Carrel can open.')
  const files = new BookFiles(epub, new Map(items.map((item) => [item.href, item.type])))
  const publication = new Publication({ manifest, fetcher: new ZipFetcher(files, epub) })
  return {
    publication,
    positions: positionsOf(epub, spine),
    contents,
    close: () => files.close(),
  }
}

/** An entry in the book's contents, as the manifest has it: where it goes (a path in the zip, and maybe an element). */
export interface ContentsLink {
  href: string
  title: string
  children: ContentsLink[]
}

/** The book's contents list: EPUB 3's navigation document, else EPUB 2's NCX; empty if it has neither. */
async function contentsOf(epub: Epub, opf: Document, items: ManifestItem[]): Promise<ContentsLink[]> {
  const parse = (text: string, type: string) => new DOMParser().parseFromString(text, type === 'text/html' ? 'text/html' : 'application/xhtml+xml')
  const label = (el: Element | null | undefined) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
  const target = (ref: string | null, from: string) => {
    const [file, fragment] = (ref ?? '').split('#')
    const path = file ? resolvePath(file, from) : from
    return path ? path + (fragment ? `#${fragment}` : '') : undefined
  }

  const nav = items.find((item) => item.properties.includes('nav'))
  if (nav && epub.has(nav.href)) {
    const doc = parse(await epub.text(nav.href), nav.type)
    const toc = [...doc.getElementsByTagName('nav')].find((el) =>
      (el.getAttributeNS('http://www.idpf.org/2007/ops', 'type') ?? el.getAttribute('epub:type') ?? '').split(/\s+/).includes('toc'))
    const list = (ol: Element | null | undefined): ContentsLink[] =>
      [...(ol?.children ?? [])].filter((li) => li.localName === 'li').flatMap((li) => {
        const a = [...li.children].find((el) => el.localName === 'a' || el.localName === 'span')
        const href = target(a?.getAttribute('href') ?? null, nav.href)
        const children = list([...li.children].find((el) => el.localName === 'ol'))
        // A heading without a link of its own goes to its first entry.
        const to = href ?? children[0]?.href
        return to ? [{ href: to, title: label(a), children }] : children
      })
    const entries = list(toc && [...toc.children].find((el) => el.localName === 'ol'))
    if (entries.length) return entries
  }

  const ncxId = opf.getElementsByTagNameNS('http://www.idpf.org/2007/opf', 'spine')[0]?.getAttribute('toc')
  const ncx = items.find((item) => item.id === ncxId) ?? items.find((item) => item.type === 'application/x-dtbncx+xml')
  if (!ncx || !epub.has(ncx.href)) return []
  const doc = new DOMParser().parseFromString(await epub.text(ncx.href), 'application/xml')
  const points = (parent: Element | null | undefined): ContentsLink[] =>
    [...(parent?.children ?? [])].filter((el) => el.localName === 'navPoint').flatMap((point) => {
      const children = points(point)
      const content = [...point.children].find((el) => el.localName === 'content')
      const href = target(content?.getAttribute('src') ?? null, ncx.href)
      const text = [...point.children].find((el) => el.localName === 'navLabel')
      return href ? [{ href, title: label(text), children }] : children
    })
  return points(doc.getElementsByTagNameNS('*', 'navMap')[0])
}

/**
 * Positions as Readium Kotlin makes them for reflowable EPUBs: one per 1024 bytes of each spine file's zip entry, as
 * compressed, at least one each. So `position` and `totalProgression` line up with the Android app's for the same file.
 */
function positionsOf(epub: Epub, spine: { href: string; type: string }[]): Locator[] {
  const counts = spine.map((item) => Math.max(1, Math.ceil(epub.compressedSize(item.href) / 1024)))
  const total = counts.reduce((a, b) => a + b, 0)
  const positions: Locator[] = []
  let position = 1
  spine.forEach((item, i) => {
    for (let n = 0; n < counts[i]; n++, position++) {
      positions.push(Locator.deserialize({
        href: item.href,
        type: item.type,
        locations: { position, progression: n / counts[i], totalProgression: (position - 1) / total },
      })!)
    }
  })
  return positions
}
