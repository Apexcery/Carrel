import { useState, type FormEvent } from 'react'
import { Link, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useSession } from '../auth'
import { supabase } from '../supabase'
import { ThemeSwitch } from './ThemeSwitch'

export function Layout() {
  const [params] = useSearchParams()
  const { pathname } = useLocation()
  const query = pathname === '/search' ? (params.get('q') ?? '') : ''

  return (
    <div className="shell">
      <header className="masthead">
        <Link to="/" className="wordmark" aria-label="Carrel home">
          Carrel
        </Link>
        {/* Keyed on the query so the box resets when the URL changes (back/forward, links). */}
        <SearchBox key={query} initialQuery={query} />
        <Account />
      </header>
      <main className="page">
        <Outlet />
      </main>
      <footer className="colophon">
        <span>Book data from Hardcover and Open Library.</span>
        <ThemeSwitch />
      </footer>
    </div>
  )
}

function SearchBox({ initialQuery }: { initialQuery: string }) {
  const navigate = useNavigate()
  const [text, setText] = useState(initialQuery)

  function submit(event: FormEvent) {
    event.preventDefault()
    const q = text.trim()
    if (q) {
      navigate(`/search?q=${encodeURIComponent(q)}`)
    }
  }

  return (
    <form className="search-box" role="search" onSubmit={submit}>
      <label htmlFor="search" className="visually-hidden">
        Search books
      </label>
      <input
        id="search"
        type="search"
        placeholder="Title, author or ISBN"
        maxLength={200}
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
      <button type="submit">Search</button>
    </form>
  )
}

function Account() {
  const session = useSession()
  return (
    <div className="account">
      <span className="account-email" title={session?.user.email}>
        {session?.user.email}
      </span>
      <button type="button" className="link-button" onClick={() => supabase.auth.signOut()}>
        Sign out
      </button>
    </div>
  )
}
