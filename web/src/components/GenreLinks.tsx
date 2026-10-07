import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { GenreLink } from '../types'

/** Genres as a row of labels, each linking to its page (except one with no address); children go at the end. */
export function GenreLinks({ genres, label, children }: { genres: GenreLink[]; label?: string; children?: ReactNode }) {
  return (
    <ul className="genres" aria-label={label}>
      {genres.map((genre) => (
        <li key={genre.name}>{genre.slug ? <Link to={`/genres/${genre.slug}`}>{genre.name}</Link> : genre.name}</li>
      ))}
      {children}
    </ul>
  )
}
