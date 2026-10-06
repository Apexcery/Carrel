import type { LibraryItem, ReadingStatus } from './types'

/** URL slugs for each shelf, e.g. /@reader/shelves/want-to-read. */
const SLUGS: Record<ReadingStatus, string> = {
  want_to_read: 'want-to-read',
  reading: 'reading',
  paused: 'paused',
  read: 'read',
  did_not_finish: 'did-not-finish',
}

/** A reader's profile page, e.g. /@reader. */
export function profilePath(username: string): string {
  return `/@${username}`
}

export function shelfPath(username: string, status: ReadingStatus): string {
  return `${profilePath(username)}/shelves/${SLUGS[status]}`
}

export function statusFromSlug(slug: string | undefined): ReadingStatus | null {
  const match = Object.entries(SLUGS).find(([, s]) => s === slug)
  return match ? (match[0] as ReadingStatus) : null
}

export type SortKey = 'added' | 'title' | 'author' | 'rating' | 'hardcover' | 'popularity' | 'published' | 'finished' | 'progress'
export type SortDirection = 'asc' | 'desc'

type Kind = 'text' | 'number' | 'date'

interface SortOption {
  key: SortKey
  label: string
  kind: Kind
  /** Only offered on these shelves. */
  shelves?: ReadingStatus[]
  value: (item: LibraryItem) => string | number | null
}

const SORT_OPTIONS: SortOption[] = [
  { key: 'added', label: 'Date added', kind: 'date', value: (i) => i.entry.addedAt },
  { key: 'finished', label: 'Date finished', kind: 'date', shelves: ['read', 'did_not_finish'], value: lastFinished },
  { key: 'progress', label: 'Progress', kind: 'number', shelves: ['reading', 'paused'], value: (i) => i.entry.progressPercent },
  { key: 'title', label: 'Title', kind: 'text', value: (i) => i.book.title },
  { key: 'author', label: 'Author', kind: 'text', value: (i) => surname(i.book.authors[0]) },
  { key: 'rating', label: 'Your rating', kind: 'number', value: (i) => i.entry.rating },
  { key: 'hardcover', label: 'Hardcover rating', kind: 'number', value: (i) => i.book.hardcoverRating },
  { key: 'popularity', label: 'Popularity', kind: 'number', value: (i) => i.book.hardcoverRatingsCount },
  { key: 'published', label: 'Year published', kind: 'number', value: (i) => i.book.firstPublishedYear },
]

export function sortOptions(status: ReadingStatus): SortOption[] {
  return SORT_OPTIONS.filter((o) => !o.shelves || o.shelves.includes(status))
}

export function defaultSort(status: ReadingStatus): SortKey {
  return status === 'read' ? 'finished' : 'added'
}

/** Names sort A–Z; dates, ratings and counts sort newest or highest first. */
export function naturalDirection(key: SortKey): SortDirection {
  return SORT_OPTIONS.find((o) => o.key === key)?.kind === 'text' ? 'asc' : 'desc'
}

const DIRECTION_LABELS: Record<Kind, Record<SortDirection, string>> = {
  text: { asc: 'A–Z', desc: 'Z–A' },
  number: { asc: 'Lowest first', desc: 'Highest first' },
  date: { asc: 'Oldest first', desc: 'Newest first' },
}

export function directionLabel(key: SortKey, direction: SortDirection): string {
  const kind = SORT_OPTIONS.find((o) => o.key === key)?.kind
  return DIRECTION_LABELS[kind ?? 'text'][direction]
}
/** Sorts by the given key; books without a value go last in either direction, then ties go by title. */
export function sortItems(items: LibraryItem[], key: SortKey, direction: SortDirection): LibraryItem[] {
  const option = SORT_OPTIONS.find((o) => o.key === key) ?? SORT_OPTIONS[0]
  const sign = direction === 'asc' ? 1 : -1
  const collator = new Intl.Collator(undefined, { sensitivity: 'base', numeric: true })
  return [...items].sort((a, b) => {
    const x = option.value(a)
    const y = option.value(b)
    if (x === null || x === '' || y === null || y === '') {
      if ((x === null || x === '') !== (y === null || y === '')) {
        return x === null || x === '' ? 1 : -1
      }
    } else if (x !== y) {
      return sign * (typeof x === 'number' && typeof y === 'number' ? x - y : collator.compare(String(x), String(y)))
    }
    return collator.compare(a.book.title, b.book.title)
  })
}

/** Keeps books whose title, author or series contains every word of the query. */
export function filterItems(items: LibraryItem[], query: string): LibraryItem[] {
  const terms = query.toLowerCase().split(/\s+/).filter(Boolean)
  if (terms.length === 0) {
    return items
  }
  return items.filter((item) => {
    const text = [item.book.title, ...item.book.authors, item.book.series?.name ?? ''].join(' ').toLowerCase()
    return terms.every((term) => text.includes(term))
  })
}

/** Reads finished in a year, one for each read, so a book read twice that year counts twice. */
export function readsFinishedIn(items: LibraryItem[], year: number): { item: LibraryItem; finishedOn: string }[] {
  return items.flatMap((item) =>
    item.entry.reads
      .filter((read) => read.finishedOn?.startsWith(`${year}-`))
      .map((read) => ({ item, finishedOn: read.finishedOn! })),
  )
}

/** A shelf's books in its default order. */
export function shelfItems(library: LibraryItem[], status: ReadingStatus): LibraryItem[] {
  const key = defaultSort(status)
  return sortItems(
    library.filter((item) => item.entry.status === status),
    key,
    naturalDirection(key),
  )
}

function lastFinished(item: LibraryItem): string | null {
  return item.entry.reads.map((r) => r.finishedOn).filter((d): d is string => d !== null).sort().at(-1) ?? null
}

function surname(name: string | undefined): string | null {
  return name ? (name.trim().split(/\s+/).at(-1) ?? name) : null
}
