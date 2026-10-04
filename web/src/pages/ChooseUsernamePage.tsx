import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiSend } from '../api'
import { PROFILE_KEY } from '../profile'
import { supabase } from '../supabase'
import type { Profile } from '../types'
import { useUsernameStatus } from '../username'

/** Shown after sign-in until the reader has chosen their public username. */
export function ChooseUsernamePage() {
  const queryClient = useQueryClient()
  const [username, setUsername] = useState('')
  const trimmed = username.trim()
  const status = useUsernameStatus(username, '3 to 20 letters, numbers, underscores, or hyphens. You can change it later.')

  const save = useMutation({
    mutationFn: () => apiSend<Profile>('PUT', '/profile', { username: trimmed }),
    onSuccess: (profile) => queryClient.setQueryData(PROFILE_KEY, profile),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (status.canSave) {
      save.mutate()
    }
  }

  const canSave = status.canSave && !save.isPending

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
