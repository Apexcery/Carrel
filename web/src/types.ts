// Shapes returned by the Carrel API (see api/Carrel.Api/Books/BookDtos.cs).

export interface BookSearchResponse {
  found: number
  results: BookSearchResult[]
}

export interface BookSearchResult {
  hardcoverId: number | null
  openLibraryWorkId: string | null
  title: string
  subtitle: string | null
  authors: string[]
  releaseYear: number | null
  coverUrl: string | null
  hardcoverRating: number | null
  hardcoverRatingsCount: number | null
  seriesHardcoverId: number | null
  seriesName: string | null
  seriesPosition: number | null
}

export interface BookDetail {
  id: number
  hardcoverId: number | null
  title: string
  subtitle: string | null
  description: string | null
  descriptionSource: 'hardcover' | 'open_library' | 'google_books' | null
  coverUrl: string | null
  firstPublishedYear: number | null
  hardcoverRating: number | null
  hardcoverRatingsCount: number | null
  authors: Contributor[]
  series: SeriesEntry[]
  genres: string[]
  editions: Edition[]
}

export interface Contributor {
  id: number
  name: string
  role: string
}

export interface SeriesEntry {
  id: number
  hardcoverId: number | null
  name: string
  position: number | null
}

export interface Edition {
  id: number
  isbn13: string | null
  isbn10: string | null
  format: 'print' | 'ebook' | 'audio' | null
  pageCount: number | null
  audioSeconds: number | null
  publisher: string | null
  releaseDate: string | null
  language: string | null
  coverUrl: string | null
}

export interface SeriesDetail {
  hardcoverId: number
  name: string
  author: string | null
  isCompleted: boolean | null
  books: SeriesBook[]
  otherBooks: SeriesBook[]
}

export interface SeriesBook {
  hardcoverId: number
  position: number | null
  title: string
  authors: string[]
  releaseYear: number | null
  coverUrl: string | null
  hardcoverRating: number | null
  hardcoverRatingsCount: number | null
}

export type ReadingStatus = 'want_to_read' | 'reading' | 'read' | 'did_not_finish'
export type ProgressUnit = 'page' | 'percent' | 'seconds'

export interface LibraryEntry {
  id: number
  bookId: number
  status: ReadingStatus
  rating: number | null
  editionId: number | null
  progressUnit: ProgressUnit | null
  progressValue: number | null
  progressPercent: number | null
  /** Pages or seconds in the edition used for progress, when known. */
  progressTotal: number | null
  addedAt: string
  updatedAt: string
  reads: Read[]
}

export interface Read {
  id: number
  startedOn: string | null
  finishedOn: string | null
}

export interface SaveEntryRequest {
  status: ReadingStatus
  editionId: number | null
  rating: number | null
  progressUnit: ProgressUnit | null
  progressValue: number | null
  today: string
  /** When given, replaces the reading history: reads with an id are updated, without one added, missing ones deleted. */
  reads?: { id: number | null; startedOn: string | null; finishedOn: string | null }[]
}

export interface LibraryItem {
  entry: LibraryEntry
  book: {
    id: number
    title: string
    authors: string[]
    coverUrl: string | null
    series: SeriesEntry | null
    firstPublishedYear: number | null
    hardcoverRating: number | null
    hardcoverRatingsCount: number | null
  }
}
