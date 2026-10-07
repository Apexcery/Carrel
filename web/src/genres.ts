import { useQuery } from '@tanstack/react-query'
import { apiGet } from './api'
import type { GenreIndex } from './types'

/**
 * A genre's address on Carrel, made from its name ("Self-Help" is self-help), the same way the API makes it
 * (Genres.Slug). Empty for a name with no letters or digits in it.
 */
export function genreSlug(name: string): string {
  return name
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/['’]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

/** The genres listed for browsing, and the rest for searching. They change at most daily, so they're fetched once. */
export function useGenreIndex() {
  return useQuery({ queryKey: ['genres'], queryFn: () => apiGet<GenreIndex>('/genres'), staleTime: Infinity })
}
