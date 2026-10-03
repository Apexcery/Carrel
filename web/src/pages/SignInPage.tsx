import { useState, type FormEvent } from 'react'
import { supabase } from '../supabase'

type Mode = 'sign-in' | 'sign-up'

export function SignInPage() {
  const [mode, setMode] = useState<Mode>('sign-in')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ text: string; isError: boolean } | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
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

  return (
    <div className="sign-in">
      <div className="sign-in-card">
        <p className="kicker">Carrel</p>
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
              minLength={mode === 'sign-up' ? 8 : undefined}
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

        <p className="sign-in-switch">
          {mode === 'sign-in' ? 'New here?' : 'Already have an account?'}{' '}
          <button type="button" className="link-button" onClick={switchMode}>
            {mode === 'sign-in' ? 'Create an account' : 'Sign in'}
          </button>
        </p>
      </div>
    </div>
  )
}
