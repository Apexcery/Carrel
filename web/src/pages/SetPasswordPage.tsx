import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useSession } from '../auth'
import { usePageTitle } from '../pageTitle'
import { MIN_PASSWORD_LENGTH, passwordProblem } from '../passwords'
import { supabase } from '../supabase'

/**
 * Choosing a new password after following a reset or invitation link. The link signed the reader in, which proves
 * it's them, so unlike Settings this doesn't ask for the current password.
 */
export function SetPasswordPage() {
  usePageTitle('Set a password')
  const session = useSession()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [repeated, setRepeated] = useState('')
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const issue = passwordProblem(password) ?? (password !== repeated ? 'The passwords don’t match.' : null)
    if (issue) {
      setProblem(issue)
      return
    }
    setBusy(true)
    setProblem(null)
    const { error } = await supabase.auth.updateUser({ password })
    setBusy(false)
    if (error) {
      setProblem(error.message)
    } else {
      navigate('/', { replace: true })
    }
  }

  return (
    <div className="sign-in">
      <div className="sign-in-card">
        <p className="kicker">
          <Link to="/" className="kicker-link">
            Carrel
          </Link>
        </p>
        <h1 className="sign-in-title">Set a password.</h1>
        {!session ? (
          <>
            <p className="muted">This page works from the link in a password reset or invitation email.</p>
            <p className="sign-in-switch">
              <Link to="/sign-in?mode=reset">Send a reset link</Link>
            </p>
          </>
        ) : (
          <>
            <p className="muted">Choose a password for {session.user.email}.</p>
            <form onSubmit={submit} className="sign-in-form">
              {/* Lets password managers save the new password against the right account. */}
              <input type="email" autoComplete="username" value={session.user.email ?? ''} readOnly hidden />
              <label>
                <span>New password</span>
                <input
                  type="password"
                  autoComplete="new-password"
                  minLength={MIN_PASSWORD_LENGTH}
                  required
                  autoFocus
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </label>
              <label>
                <span>Repeat it</span>
                <input
                  type="password"
                  autoComplete="new-password"
                  required
                  value={repeated}
                  onChange={(e) => setRepeated(e.target.value)}
                />
              </label>
              <button type="submit" className="primary-button" disabled={busy}>
                {busy ? 'One moment…' : 'Save password'}
              </button>
            </form>
            {problem && (
              <p className="form-message error" role="alert">
                {problem}
              </p>
            )}
            <p className="sign-in-note">At least {MIN_PASSWORD_LENGTH} characters.</p>
          </>
        )}
      </div>
    </div>
  )
}
