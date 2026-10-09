// The home page's data, kept in this browser so the next visit shows it straight away while it refreshes.

import type { QueryClient } from '@tanstack/react-query'
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister'
import { persistQueryClient, removeOldestQuery } from '@tanstack/react-query-persist-client'

/** This build (see vite.config.ts). Data saved by an earlier one may not have the shape this one expects. */
declare const __BUILD__: string

/** How long saved data stays usable. Queries are kept in memory as long, so they're still there to save. */
export const SAVED_FOR = 7 * 24 * 60 * 60 * 1000
/** Queries saved, by the first part of their key: what the home page needs (with the reader's own profile). */
const SAVED = ['library', 'profile', 'related', 'discover', 'genres']

// Storage can be unavailable (private windows, blocked site data); nothing is then saved. A failed save is caught by
// the persister, which retries with the oldest query left out.
const persister = createAsyncStoragePersister({
  key: 'carrel-queries',
  storage: {
    getItem: (key) => {
      try {
        return localStorage.getItem(key)
      } catch {
        return null
      }
    },
    setItem: (key, value) => localStorage.setItem(key, value),
    removeItem: (key) => {
      try {
        localStorage.removeItem(key)
      } catch {
        // Ignore; see above.
      }
    },
  },
  retry: removeOldestQuery,
})

/**
 * Restores what was saved for this reader (or for signed-out browsing) on their last visit, then saves changes as
 * they happen. Anything saved for someone else, or by an earlier version of the website, is discarded unseen. Restored data is from an earlier visit, so it's
 * marked stale and refreshes as soon as a page uses it.
 */
export function saveQueries(queryClient: QueryClient, readerId: string | null) {
  const [stop, restoring] = persistQueryClient({
    queryClient,
    persister,
    maxAge: SAVED_FOR,
    buster: `${__BUILD__}:${readerId ?? ''}`,
    dehydrateOptions: {
      shouldDehydrateQuery: (query) =>
        query.state.status === 'success' && SAVED.includes(query.queryKey[0] as string),
    },
  })
  const restored = restoring
    .then(() => queryClient.invalidateQueries({ refetchType: 'none' }))
    // Unreadable saved data has been discarded; carry on without it.
    .catch(() => {})
  return { stop, restored }
}
