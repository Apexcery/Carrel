import { useQuery } from '@tanstack/react-query'
import { ApiError, apiGet } from './api'
import { useProfile } from './profile'
import type { LibraryItem, Reader } from './types'

/**
 * The reader whose profile is at /@username, with their library. The signed-in reader's own comes from their library
 * and profile, so it keeps up with their changes; anyone else's from /readers. Not found covers a private profile too.
 */
export function useReader(username: string) {
  const profile = useProfile().data
  const isOwn = profile?.username?.toLowerCase() === username.toLowerCase()
  const library = useQuery({ queryKey: ['library'], queryFn: () => apiGet<LibraryItem[]>('/library'), enabled: isOwn })
  const other = useQuery({
    queryKey: ['readers', username.toLowerCase()],
    queryFn: () => apiGet<Reader>(`/readers/${encodeURIComponent(username)}`),
    enabled: !isOwn && username !== '',
  })
  const query = isOwn ? library : other
  const reader: Reader | undefined = isOwn
    ? library.data &&
      profile && { username: profile.username!, isPublic: profile.isPublic, goals: profile.goals, library: library.data }
    : other.data
  return {
    reader,
    isOwn,
    notFound: query.error instanceof ApiError && query.error.status === 404,
    error: query.error,
    refetch: query.refetch,
  }
}
