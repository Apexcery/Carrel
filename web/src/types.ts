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

/** A suggested book from Hardcover; opening it imports it. */
export interface BookSuggestion {
  hardcoverId: number
  title: string
  authors: string[]
  releaseYear: number | null
  coverUrl: string | null
  seriesName: string | null
  seriesPosition: number | null
}

export interface RelatedBooks {
  similar: BookSuggestion[]
  /** The book's first author, whose other books are in byAuthor. */
  author: string | null
  byAuthor: BookSuggestion[]
}

/** Popular books in the reader's top genre; genre is null until their books have one. */
export interface GenrePicks {
  genre: string | null
  books: BookSuggestion[]
}

/** The Discover shelves on the home page, the same for every reader. */
export interface DiscoverShelves {
  popular: BookSuggestion[]
  newReleases: BookSuggestion[]
  comingSoon: BookSuggestion[]
  topRated: BookSuggestion[]
}

export type ReadingStatus = 'want_to_read' | 'reading' | 'paused' | 'read' | 'did_not_finish'
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
  /** Finished, but when isn't known; never set alongside finishedOn. */
  finishedDateUnknown: boolean
}

export interface SaveEntryRequest {
  status: ReadingStatus
  editionId: number | null
  rating: number | null
  progressUnit: ProgressUnit | null
  progressValue: number | null
  today: string
  /** When given, replaces the reading history: reads with an id are updated, without one added, missing ones deleted. */
  reads?: { id: number | null; startedOn: string | null; finishedOn: string | null; finishedDateUnknown: boolean }[]
}

export interface LibraryItem {
  entry: LibraryEntry
  book: {
    id: number
    hardcoverId: number | null
    title: string
    authors: string[]
    coverUrl: string | null
    series: SeriesEntry | null
    firstPublishedYear: number | null
    hardcoverRating: number | null
    hardcoverRatingsCount: number | null
    /** From the reader's edition, else the first edition with one. */
    pageCount: number | null
  }
}

/** How many books a reader means to finish in a year. */
export interface ReadingGoal {
  year: number
  books: number
}

export interface Profile {
  /** Null until the reader chooses one. */
  username: string | null
  /** Whether anyone can see the reader's profile and library; if not, only they can. */
  isPublic: boolean
  /** Null without a picture. */
  avatarUrl: string | null
  /** Every year's goal the reader has set, oldest first. */
  goals: ReadingGoal[]
}

/** A reader's profile page: their username, reading goals, and whole library. */
export interface Reader {
  username: string
  isPublic: boolean
  avatarUrl: string | null
  goals: ReadingGoal[]
  library: LibraryItem[]
}

export interface UsernameAvailability {
  available: boolean
  reason: string | null
}

export type ImportSource = 'goodreads' | 'story_graph' | 'carrel'

/** Matching rows to books, waiting for Hardcover's daily allowance, finished, or given up after repeated failures. */
export type ImportState = 'matching' | 'waiting' | 'done' | 'failed'

export interface ImportStatus {
  id: number
  source: ImportSource
  state: ImportState
  overwriteExisting: boolean
  createdAt: string
  finishedAt: string | null
  total: number
  matched: number
  /** Matched by title and not checked yet. */
  toCheck: number
  notFound: number
  /** Still being matched. */
  remaining: number
}

export interface ImportReviewItem {
  id: number
  row: number
  /** As the export has it. */
  title: string
  authors: string[]
  match: 'by_title' | 'not_found'
  /** The book a title match found; null when nothing matched. */
  book: {
    id: number
    title: string
    authors: string[]
    coverUrl: string | null
    firstPublishedYear: number | null
    seriesName: string | null
    seriesPosition: number | null
  } | null
}
