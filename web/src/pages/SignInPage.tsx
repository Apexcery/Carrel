import { useState, type FormEvent } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router'
import { useSession } from '../auth'
import { MIN_PASSWORD_LENGTH, passwordProblem } from '../passwords'
import { safeNext } from '../signIn'
import { supabase } from '../supabase'

type Mode = 'sign-in' | 'sign-up'

/** Sign in or create an account, then return to the page that sent you here (?next=). */
export function SignInPage() {
  const session = useSession()
  const [params] = useSearchParams()
  const [mode, setMode] = useState<Mode>(params.get('mode') === 'sign-up' ? 'sign-up' : 'sign-in')
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
        : await supabase.auth.signUp({ email, password })
    setBusy(false)
    if (error) {
      setMessage({ text: error.message, isError: true })
    } else if (mode === 'sign-up') {
      setMessage({ text: 'Check your email for a link to confirm your account.', isError: false })
    }
  }

  function switchMode() {
    setMode(mode === 'sign-in' ? 'sign-up' : 'sign-in')
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
        <h1 className="sign-in-title">{mode === 'sign-in' ? 'Welcome back.' : 'Take a seat.'}</h1>
        <p className="muted">A quiet place to keep track of what you read.</p>

        <form onSubmit={submit} className="sign-in-form">
          <label>
            <span>Email</span>
            <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
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
          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? 'One moment…' : mode === 'sign-in' ? 'Sign in' : 'Create account'}
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

        <p className="sign-in-switch">
          {mode === 'sign-in' ? 'New here?' : 'Already have an account?'}{' '}
          <button type="button" className="link-button" onClick={switchMode}>
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
