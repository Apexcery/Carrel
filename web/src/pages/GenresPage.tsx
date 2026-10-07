import { useState } from 'react'
import { ErrorNotice } from '../components/ErrorNotice'
import { GenreLinks } from '../components/GenreLinks'
import { useGenreIndex } from '../genres'
import { usePageTitle } from '../pageTitle'
import type { GenreIndex, GenreLink } from '../types'

/** Search results shown at most; a few letters can match hundreds of genres. */
const MATCHES_SHOWN = 40

/** The genres listed for browsing, at /genres, with a search across every genre Carrel knows of. */
export function GenresPage() {
  const genres = useGenreIndex()
  const [query, setQuery] = useState('')
  usePageTitle('Genres')

  if (genres.isError) {
    return <ErrorNotice error={genres.error} onRetry={() => genres.refetch()} />
  }
  const matches = genres.data && query.trim() ? findGenres(genres.data, query.trim()) : null
  return (
    <section aria-busy={!genres.data}>
      <header className="series-header">
        <p className="kicker">Browse</p>
        <h1 className="series-title">Genres</h1>
      </header>
      {genres.data && (
        <>
          <label className="genre-search">
            <span className="visually-hidden">Find a genre</span>
            <input type="search" placeholder="Find a genre" value={query} onChange={(e) => setQuery(e.target.value)} />
          </label>
          {matches ? (
            matches.length > 0 ? (
              <GenreLinks genres={matches} label="Matching genres" />
            ) : (
              <p className="muted">No genres match “{query.trim()}”.</p>
            )
          ) : (
            <>
              <GenreGroup title="Fiction" genres={genres.data.fiction} />
              <GenreGroup title="Nonfiction" genres={genres.data.nonfiction} />
            </>
          )}
        </>
      )}
    </section>
  )
}

function GenreGroup({ title, genres }: { title: string; genres: GenreLink[] }) {
  return (
    <section className="book-section">
      <h2 className="section-title">{title}</h2>
      <GenreLinks genres={genres} />
    </section>
  )
}

/** Genres whose name has the text in it: those starting with it first, then the listed ones, then the most used. */
function findGenres(index: GenreIndex, text: string): GenreLink[] {
  const lower = text.toLowerCase()
  const matching = [...index.fiction, ...index.nonfiction, ...index.other].filter((g) => g.name.toLowerCase().includes(lower))
  const starting = matching.filter((g) => g.name.toLowerCase().startsWith(lower))
  return [...starting, ...matching.filter((g) => !starting.includes(g))].slice(0, MATCHES_SHOWN)
}
