import { useQuery } from '@tanstack/react-query'
import { apiGet } from './api'
import type { Profile } from './types'

export const PROFILE_KEY = ['profile']

/** The signed-in reader's profile (their public username). */
export function useProfile() {
  return useQuery({ queryKey: PROFILE_KEY, queryFn: () => apiGet<Profile>('/profile'), staleTime: Infinity })
}
