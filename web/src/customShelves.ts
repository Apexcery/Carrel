import { useQuery, type QueryClient } from '@tanstack/react-query'
import { apiGet } from './api'
import { useSession } from './auth'
import type { CustomShelf } from './types'

/** Under 'library', so it refreshes whenever the library does, as after an import or deleting book data. */
export const CUSTOM_SHELVES_KEY = ['library', 'shelves']

/** The signed-in reader's custom shelves, A–Z. Doesn't run for signed-out visitors. */
export function useCustomShelves(enabled = true) {
  const signedIn = Boolean(useSession())
  return useQuery({
    queryKey: CUSTOM_SHELVES_KEY,
    queryFn: () => apiGet<CustomShelf[]>('/shelves'),
    enabled: signedIn && enabled,
  })
}

/** Puts a shelf the API returned after a change in place of the saved one, so it shows straight away. */
export function storeCustomShelf(queryClient: QueryClient, shelf: CustomShelf) {
  queryClient.setQueryData<CustomShelf[]>(CUSTOM_SHELVES_KEY, (shelves) => shelves?.map((s) => (s.id === shelf.id ? shelf : s)))
}
