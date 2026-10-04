import { useState, type FormEvent } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router'
import { useSession } from '../auth'
import { MIN_PASSWORD_LENGTH, passwordProblem } from '../passwords'
import { safeNext } from '../signIn'
import { supabase } from '../supabase'

type Mode = 'sign-in' | 'sign-up' | 'reset'

const TITLES: Record<Mode, string> = {
  'sign-in': 'Welcome back.',
  'sign-up': 'Take a seat.',
  reset: 'Forgot your password?',
}

const SUBMIT_LABELS: Record<Mode, string> = {
  'sign-in': 'Sign in',
  'sign-up': 'Create account',
  reset: 'Send reset link',
}

function modeFrom(value: string | null): Mode {
  return value === 'sign-up' || value === 'reset' ? value : 'sign-in'
}

/**
 * Sign in, create an account, or ask for a password reset link, then return to the page that sent you here (?next=).
 * ?mode= picks which one opens first.
 */
export function SignInPage() {
  const session = useSession()
  const [params] = useSearchParams()
  const [mode, setMode] = useState<Mode>(modeFrom(params.get('mode')))
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ text: string; isError: boolean } | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const problem = mode === 'sign-up' ? passwordProblem(password) : null
    if (problem) {
      setMessage({ text: problem, isError: true })
      return
    }
    setBusy(true)
    setMessage(null)
    const { error } =
      mode === 'sign-in'
        ? await supabase.auth.signInWithPassword({ email, password })
        : mode === 'sign-up'
          ? await supabase.auth.signUp({ email, password })
          : await supabase.auth.resetPasswordForEmail(email)
    setBusy(false)
    if (error) {
      setMessage({ text: error.message, isError: true })
    } else if (mode === 'sign-up') {
      setMessage({ text: 'Check your email for a link to confirm your account.', isError: false })
    } else if (mode === 'reset') {
      // Worded the same whether or not the address has an account, so it can't be used to find out who does.
      setMessage({ text: 'If there’s an account for that email, we’ve sent it a link to set a new password.', isError: false })
    }
  }

  function switchTo(next: Mode) {
    setMode(next)
    setMessage(null)
  }

  if (session) {
    return <Navigate to={safeNext(params.get('next'))} replace />
  }

  return (
    <div className="sign-in">
      <div className="sign-in-card">
        <p className="kicker">
          <Link to="/" className="kicker-link">
            Carrel
          </Link>
        </p>
        <h1 className="sign-in-title">{TITLES[mode]}</h1>
        <p className="muted">
          {mode === 'reset'
            ? 'Enter your email and we’ll send you a link to set a new one.'
            : 'A quiet place to keep track of what you read.'}
        </p>

        <form onSubmit={submit} className="sign-in-form">
          <label>
            <span>Email</span>
            <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          {mode !== 'reset' && (
            <label>
              <span>Password</span>
              <input
                type="password"
                autoComplete={mode === 'sign-in' ? 'current-password' : 'new-password'}
                minLength={mode === 'sign-up' ? MIN_PASSWORD_LENGTH : undefined}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
          )}
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'One moment…' : SUBMIT_LABELS[mode]}
          </button>
        </form>

        {message && (
          <p className={message.isError ? 'form-message error' : 'form-message'} role={message.isError ? 'alert' : 'status'}>
            {message.text}
          </p>
        )}

        {mode === 'sign-up' && (
          <p className="sign-in-note">
            You must be 13 or over to create an account. See how Carrel handles your information in the{' '}
            <Link to="/privacy">privacy notice</Link>.
          </p>
        )}

        {mode === 'sign-in' && (
          <p className="sign-in-switch">
            <button type="button" className="link-button" onClick={() => switchTo('reset')}>
              Forgot your password?
            </button>
          </p>
        )}

        <p className="sign-in-switch">
          {mode === 'sign-up' ? 'Already have an account?' : mode === 'reset' ? 'Remembered it?' : 'New here?'}{' '}
          <button type="button" className="link-button" onClick={() => switchTo(mode === 'sign-in' ? 'sign-up' : 'sign-in')}>
            {mode === 'sign-in' ? 'Create an account' : 'Sign in'}
          </button>
        </p>
      </div>
      <nav className="colophon-links" aria-label="About Carrel">
        <Link to="/privacy">Privacy</Link>
        <Link to="/copyright">Copyright</Link>
      </nav>
    </div>
  )
}
