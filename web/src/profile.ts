import { useQuery } from '@tanstack/react-query'
import { apiGet } from './api'
import { useSession } from './auth'
import type { Profile } from './types'

export const PROFILE_KEY = ['profile']

/** The signed-in reader's profile (their public username). Doesn't run for signed-out visitors. */
export function useProfile() {
  const signedIn = Boolean(useSession())
  return useQuery({ queryKey: PROFILE_KEY, queryFn: () => apiGet<Profile>('/profile'), staleTime: Infinity, enabled: signedIn })
}
