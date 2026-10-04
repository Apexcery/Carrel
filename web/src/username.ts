import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiGet } from './api'
import type { UsernameAvailability } from './types'

const VALID = /^[A-Za-z0-9_-]{3,20}$/

export interface UsernameStatus {
  text: string
  tone: '' | 'ok' | 'error'
  canSave: boolean
}

/**
 * Validates a username as it's typed, and checks it's free once typing pauses rather than on every keystroke.
 * `hint` shows while the box is empty; the reader's `current` username counts as unchanged rather than available.
 */
export function useUsernameStatus(username: string, hint: string, current: string | null = null): UsernameStatus {
  const trimmed = username.trim()
  const [checking, setChecking] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => setChecking(trimmed), 350)
    return () => clearTimeout(timer)
  }, [trimmed])

  const availability = useQuery({
    queryKey: ['username-available', checking],
    queryFn: () => apiGet<UsernameAvailability>(`/profile/username-available?username=${encodeURIComponent(checking)}`),
    enabled: VALID.test(checking) && checking !== current,
    staleTime: 30_000,
  })

  if (!trimmed) {
    return { text: hint, tone: '', canSave: false }
  }
  if (trimmed === current) {
    return { text: 'That’s your current username.', tone: '', canSave: false }
  }
  if (!VALID.test(trimmed)) {
    return { text: 'Use 3 to 20 letters, numbers, underscores, or hyphens.', tone: 'error', canSave: false }
  }
  if (checking !== trimmed || availability.isPending) {
    return { text: 'Checking…', tone: '', canSave: false }
  }
  if (availability.data?.available) {
    return { text: `${trimmed} is available.`, tone: 'ok', canSave: true }
  }
  return { text: availability.data?.reason ?? 'Couldn’t check that name. Try again.', tone: 'error', canSave: false }
}
