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
