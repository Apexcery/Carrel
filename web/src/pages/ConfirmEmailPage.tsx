import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import type { EmailOtpType } from '@supabase/supabase-js'
import { supabase } from '../supabase'

/** The link types Carrel's email templates use (configured in Supabase; see the README). */
const TYPES: EmailOtpType[] = ['email', 'invite', 'recovery', 'email_change']

/**
 * Where every link in Carrel's emails lands (/auth/confirm?token_hash=…&type=…), so links point at Carrel's own
 * address rather than Supabase's. It confirms the link with Supabase, which signs the reader in, then sends them on.
 */
export function ConfirmEmailPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const tokenHash = params.get('token_hash')
  const type = TYPES.find((t) => t === params.get('type'))
  const [state, setState] = useState<'checking' | 'email-changed' | 'failed'>('checking')
  // A link works once; don't send it twice (React runs effects twice in development).
  const started = useRef(false)

  useEffect(() => {
    if (started.current || !tokenHash || !type) {
      return
    }
    started.current = true
    supabase.auth.verifyOtp({ token_hash: tokenHash, type }).then(({ error }) => {
      if (error) {
        setState('failed')
      } else if (type === 'recovery' || type === 'invite') {
        // Signed in by the link; invited readers have no password yet, and resetting means choosing a new one.
        navigate('/auth/set-password', { replace: true })
      } else if (type === 'email_change') {
        setState('email-changed')
      } else {
        // A confirmed sign-up: home, where a new reader chooses their username first.
        navigate('/', { replace: true })
      }
    })
  }, [tokenHash, type, navigate])

  const failed = state === 'failed' || !tokenHash || !type

  return (
    <div className="sign-in">
      <div className="sign-in-card">
        <p className="kicker">
          <Link to="/" className="kicker-link">
            Carrel
          </Link>
        </p>
        {failed ? (
          <>
            <h1 className="sign-in-title">That link didn’t work.</h1>
            <p className="muted">It may have expired or already been used. Links in Carrel’s emails last an hour.</p>
            <p className="sign-in-switch">
              {type === 'recovery' || type === 'invite' ? (
                <Link to="/sign-in?mode=reset">Send a new link</Link>
              ) : (
                <Link to="/sign-in">Sign in</Link>
              )}
            </p>
          </>
        ) : state === 'email-changed' ? (
          <>
            <h1 className="sign-in-title">Email confirmed.</h1>
            <p className="muted">
              If we also emailed your other address, confirm that link too: your email changes once both are confirmed.
            </p>
            <p className="sign-in-switch">
              <Link to="/settings/account">Go to your settings</Link>
            </p>
          </>
        ) : (
          <>
            <h1 className="sign-in-title">One moment.</h1>
            <p className="muted" role="status">
              Checking your link…
            </p>
          </>
        )}
      </div>
    </div>
  )
}
