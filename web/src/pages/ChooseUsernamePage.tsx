import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGet, apiSend } from '../api'
import { PROFILE_KEY } from '../profile'
import { supabase } from '../supabase'
import type { Profile, UsernameAvailability } from '../types'

const VALID = /^[A-Za-z0-9_-]{3,20}$/

/** Shown after sign-in until the reader has chosen their public username. */
export function ChooseUsernamePage() {
  const queryClient = useQueryClient()
  const [username, setUsername] = useState('')
  const trimmed = username.trim()

  // Check availability once typing pauses, rather than on every keystroke.
  const [checking, setChecking] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => setChecking(trimmed), 350)
    return () => clearTimeout(timer)
  }, [trimmed])

  const availability = useQuery({
    queryKey: ['username-available', checking],
    queryFn: () => apiGet<UsernameAvailability>(`/profile/username-available?username=${encodeURIComponent(checking)}`),
    enabled: VALID.test(checking),
    staleTime: 30_000,
  })

  const save = useMutation({
    mutationFn: () => apiSend<Profile>('PUT', '/profile', { username: trimmed }),
    onSuccess: (profile) => queryClient.setQueryData(PROFILE_KEY, profile),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (VALID.test(trimmed)) {
      save.mutate()
    }
  }

  const status = (() => {
    if (!trimmed) {
      return { text: '3 to 20 letters, numbers, underscores or hyphens. You can change it later.', tone: '' }
    }
    if (!VALID.test(trimmed)) {
      return { text: 'Use 3 to 20 letters, numbers, underscores or hyphens.', tone: 'error' }
    }
    if (checking !== trimmed || availability.isPending) {
      return { text: 'Checking…', tone: '' }
    }
    if (availability.data?.available) {
      return { text: `${trimmed} is available.`, tone: 'ok' }
    }
    return { text: availability.data?.reason ?? 'Couldn’t check that name. Try again.', tone: 'error' }
  })()

  const canSave = VALID.test(trimmed) && checking === trimmed && availability.data?.available === true && !save.isPending

  return (
    <div className="sign-in">
      <div className="sign-in-card">
        <p className="kicker">Carrel</p>
        <h1 className="sign-in-title">Choose a username.</h1>
        <p className="muted">It’s how other readers will see you. Your email address stays private.</p>

        <form onSubmit={submit} className="sign-in-form">
          <label>
            <span>Username</span>
            <input
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              maxLength={20}
              required
              value={username}
              aria-describedby="username-status"
              aria-invalid={status.tone === 'error'}
              onChange={(e) => setUsername(e.target.value)}
            />
          </label>
          <p id="username-status" className={`form-message ${status.tone}`} aria-live="polite">
            {save.error ? save.error.message : status.text}
          </p>
          <button type="submit" className="primary-button" disabled={!canSave}>
            {save.isPending ? 'Saving…' : 'Continue'}
          </button>
        </form>

        <p className="sign-in-switch">
          Not you?{' '}
          <button type="button" className="link-button" onClick={() => supabase.auth.signOut()}>
            Sign out
          </button>
        </p>
      </div>
    </div>
  )
}
