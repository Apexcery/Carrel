import { useEffect, useState, type FormEvent } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase } from './supabase'
import { apiFetch } from './api'

function App() {
  const [session, setSession] = useState<Session | null>(null)

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session))
    const { data } = supabase.auth.onAuthStateChange((_event, session) => setSession(session))
    return () => data.subscription.unsubscribe()
  }, [])

  return (
    <main>
      <h1>Carrel</h1>
      {session ? <SignedIn session={session} /> : <SignInForm />}
    </main>
  )
}

function SignInForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  async function signIn(event: FormEvent) {
    event.preventDefault()
    const { error } = await supabase.auth.signInWithPassword({ email, password })
    setMessage(error?.message ?? null)
  }

  async function signUp() {
    const { error } = await supabase.auth.signUp({ email, password })
    setMessage(error?.message ?? 'Check your email for a confirmation link.')
  }

  return (
    <form onSubmit={signIn}>
      <input
        type="email"
        placeholder="Email"
        autoComplete="email"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />
      <input
        type="password"
        placeholder="Password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <button type="submit">Sign in</button>
      <button type="button" onClick={signUp}>
        Sign up
      </button>
      {message && <p>{message}</p>}
    </form>
  )
}

function SignedIn({ session }: { session: Session }) {
  const [result, setResult] = useState<string | null>(null)

  async function callApi() {
    try {
      const response = await apiFetch('/me')
      setResult(`${response.status} ${await response.text()}`)
    } catch (error) {
      setResult(String(error))
    }
  }

  return (
    <section>
      <p>Signed in as {session.user.email}</p>
      <button onClick={callApi}>Call API /me</button>
      <button onClick={() => supabase.auth.signOut()}>Sign out</button>
      {result && <pre>{result}</pre>}
    </section>
  )
}

export default App
