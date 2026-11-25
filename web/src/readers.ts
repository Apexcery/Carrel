import { useQuery } from '@tanstack/react-query'
import { ApiError, apiGet } from './api'
import { useCustomShelves } from './customShelves'
import { useProfile } from './profile'
import type { LibraryItem, Reader } from './types'

/**
 * The reader whose profile is at /@username, with their library. The signed-in reader's own comes from their library,
 * shelves, and profile, so it keeps up with their changes; anyone else's from /readers. Not found covers a private
 * profile too.
 */
export function useReader(username: string) {
  const profile = useProfile().data
  const isOwn = profile?.username?.toLowerCase() === username.toLowerCase()
  const library = useQuery({ queryKey: ['library'], queryFn: () => apiGet<LibraryItem[]>('/library'), enabled: isOwn })
  const shelves = useCustomShelves(isOwn)
  const other = useQuery({
    queryKey: ['readers', username.toLowerCase()],
    // An API from before custom shelves sends none.
    queryFn: async () => {
      const reader = await apiGet<Reader>(`/readers/${encodeURIComponent(username)}`)
      return { ...reader, shelves: reader.shelves ?? [] }
    },
    enabled: !isOwn && username !== '',
  })
  const query = isOwn ? library : other
  const reader: Reader | undefined = isOwn
    ? library.data &&
      shelves.data &&
      profile && {
        username: profile.username!,
        isPublic: profile.isPublic,
        avatarUrl: profile.avatarUrl,
        goals: profile.goals,
        library: library.data,
        shelves: shelves.data,
      }
    : other.data
  return {
    reader,
    isOwn,
    notFound: query.error instanceof ApiError && query.error.status === 404,
    error: query.error ?? (isOwn ? shelves.error : null),
    refetch: () => {
      if (isOwn) {
        shelves.refetch()
      }
      return query.refetch()
    },
  }
}
