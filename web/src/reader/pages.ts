// The chapter and its pages in the footer, and the first words on the page, measured in the chapter's frame (the
// frames share the page's origin). Ported from the Android app's reader/Chapters.kt and ReaderActivity.kt.
import { LocatorText } from '@readium/shared'

/** An entry in the book's contents: its title, its file (place in the reading order), and the element it starts at (null for the top). */
export interface ContentsEntry {
  title: string
  file: number
  id: string | null
}

/**
 * The file on screen, laid out: how many screens it makes, the first column each of its contents entries is at the top
 * of (by index in the entries), and how many columns hold its text. A column is a page: one to a screen, or two side
 * by side.
 */
export interface FileLayout {
  file: number
  screens: number
  starts: Map<number, number>
  columns: number
}

/** The chapter on the left (or only) page, and which of its pages are showing (`first` to `last`, of `pages`). */
export interface ChapterPlace {
  title: string
  first: number
  last: number
  pages: number
}

/** The frame's pages: how many screens the file makes, which one is showing, and how many pages are side by side. */
export function screensOf(wnd: Window) {
  const width = wnd.innerWidth
  const scroller = wnd.document.scrollingElement
  if (!scroller || width <= 0) return null
  const perScreen = wnd.getComputedStyle(wnd.document.documentElement).columnCount === '2' ? 2 : 1
  return {
    screens: Math.max(1, Math.round(scroller.scrollWidth / width)),
    screen: Math.round(wnd.scrollX / width),
    perScreen,
  }
}

/**
 * The chapter at `column` of the file on screen: the last contents entry to start at or before it. Where two start in
 * the same place ("Part One" and its first chapter), the later in the contents wins. A chapter carried on from an
 * earlier file is counted from the top of this one. Null before the first entry, e.g. a cover not in the contents.
 */
export function chapterPlace(entries: ContentsEntry[], layout: FileLayout, column: number, perScreen: number): ChapterPlace | null {
  // An entry whose element isn't found is taken as the top of its file.
  const start = (index: number) => (entries[index].file === layout.file ? (layout.starts.get(index) ?? 0) : 0)
  let current = -1
  entries.forEach((entry, i) => {
    if (entry.file > layout.file || (entry.file === layout.file && start(i) > column)) return
    if (
      current < 0 ||
      entry.file > entries[current].file ||
      (entry.file === entries[current].file && start(i) >= start(current))
    ) {
      current = i
    }
  })
  if (current < 0) return null
  const chapterStart = start(current)
  const later = entries.map((entry, i) => (entry.file === layout.file ? start(i) : -1)).filter((at) => at > chapterStart)
  const end = later.length ? Math.min(...later) : Math.max(layout.columns, column + 1)
  const first = column - chapterStart + 1
  return { title: entries[current].title, first, last: Math.max(first, Math.min(column + perScreen, end) - chapterStart), pages: end - chapterStart }
}

/** The footer's words, e.g. "(Page 3 / 9)", or "(Pages 5–6 / 20)" with two pages showing; the chapter's title goes before. */
export function pagesText(place: ChapterPlace) {
  return place.first === place.last
    ? `(Page ${place.first} / ${place.pages})`
    : `(Pages ${place.first}–${place.last} / ${place.pages})`
}

/**
 * Lays out the file in the frame: where its contents entries start, and how many columns its text fills. Every entry
 * whose element isn't found is taken as the top of the file.
 */
export function measureFile(wnd: Window, entries: ContentsEntry[], file: number, screens: number, perScreen: number): FileLayout {
  const doc = wnd.document
  // Not innerWidth, which is rounded: a fraction of a pixel out adds up over a long file.
  const width = doc.scrollingElement!.scrollWidth / screens / perScreen
  const column = (rect: DOMRect) => Math.floor((rect.left + wnd.scrollX + 1) / width)
  const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT)
  // Where words or a picture show, or null for anything else. A picture can be cut, leaving a sliver at the foot of
  // one column, so it's where most of it is.
  const shown = (node: Node, last: boolean): DOMRect | null => {
    const range = doc.createRange()
    const picture = /^(img|svg|image|video)$/i.test(node.nodeName)
    if (node.nodeType === Node.TEXT_NODE && node.textContent?.trim()) range.selectNodeContents(node)
    else if (picture) range.selectNode(node)
    else return null
    const rects = [...range.getClientRects()].filter((r) => r.width > 0 && r.height > 0)
    if (picture) rects.sort((a, b) => b.height - a.height)
    return rects.length ? rects[picture || !last ? 0 : rects.length - 1] : null
  }
  // Where the first words or picture from the element on are: a heading's box can start at the foot of one column
  // while what's in it moves to the next.
  const startOf = (element: Element) => {
    walker.currentNode = element
    for (let node: Node | null = element; node; node = walker.nextNode()) {
      const rect = shown(node, false)
      if (rect) return rect
    }
    return element.getBoundingClientRect()
  }
  // Where the last words or picture before the element are, if any.
  const before = (element: Element) => {
    walker.currentNode = element
    for (let node = walker.previousNode(); node && node !== doc.body; node = walker.previousNode()) {
      const rect = shown(node, true)
      if (rect) return rect
    }
    return null
  }
  // The text ends where the last words or picture are, not at the end of the screen, nor in the blank column added to
  // end two pages side by side evenly.
  let columns = screens * perScreen
  walker.currentNode = doc.body
  while (walker.lastChild()) {
    // To the deepest last node.
  }
  for (let node: Node | null = walker.currentNode; node && node !== doc.body; node = walker.previousNode()) {
    const rect = shown(node, true)
    if (rect) {
      columns = column(rect) + 1
      break
    }
  }
  const starts = new Map<number, number>()
  entries.forEach((entry, index) => {
    if (entry.file !== file || entry.id === null) return
    const element = doc.getElementById(entry.id)
    if (!element) return
    const first = column(startOf(element))
    const previous = before(element)
    // A page belongs to the chapter at its top, so one starting partway down a page starts on the next.
    starts.set(index, previous && column(previous) === first ? first + 1 : first)
  })
  return { file, screens, starts, columns: Math.max(1, columns) }
}

/**
 * The first words on the page, with a little either side to tell them apart, for Readium to find again however the
 * text flows; null on a page without any, such as a picture.
 */
export function firstWordsShown(wnd: Window): LocatorText | null {
  const doc = wnd.document
  const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT)
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (!node.textContent?.trim()) continue
    const range = doc.createRange()
    range.selectNodeContents(node)
    // The first line starting on this page (the width is rounded, hence the pixel to spare).
    const line = [...range.getClientRects()].find((r) => r.width > 0 && r.left > -1 && r.left < wnd.innerWidth - 1)
    if (!line) continue
    const start = caretOffset(doc, node, line.left + 1, line.top + line.height / 2)
    const text = node.textContent
    return new LocatorText({
      before: text.slice(Math.max(0, start - 32), start),
      highlight: text.slice(start, start + 64),
      after: text.slice(start + 64, start + 96),
    })
  }
  return null
}

/** Where in `node` the point is, if it's in it; else its start. */
function caretOffset(doc: Document, node: Node, x: number, y: number): number {
  const caret = doc.caretPositionFromPoint?.(x, y)
  if (caret) return caret.offsetNode === node ? caret.offset : 0
  // Older browsers without caretPositionFromPoint.
  const range = (doc as Document & { caretRangeFromPoint?: (x: number, y: number) => Range | null }).caretRangeFromPoint?.(x, y)
  return range && range.startContainer === node ? range.startOffset : 0
}
