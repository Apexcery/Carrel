import { Link, Outlet, useLocation, useSearchParams } from 'react-router'
import { useProfile } from '../profile'
import { supabase } from '../supabase'
import { SearchBox } from './SearchBox'
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

function Account() {
  const username = useProfile().data?.username
  return (
    <div className="account">
      <span className="account-name" title={username ?? undefined}>
        {username}
      </span>
      <button type="button" className="link-button" onClick={() => supabase.auth.signOut()}>
        Sign out
      </button>
    </div>
  )
}
