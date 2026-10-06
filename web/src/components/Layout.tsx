import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useSession } from '../auth'
import { useProfile } from '../profile'
import { profilePath } from '../shelves'
import { isMembersOnly, signInPath } from '../signIn'
import { supabase } from '../supabase'
import { Avatar } from './Avatar'
import { SearchBox } from './SearchBox'
import { Tooltip } from './Tooltip'

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
      <Footer />
    </div>
  )
}

function Footer() {
  return (
    <footer className="colophon">
      <span>Book data from Hardcover and Open Library.</span>
      <nav className="colophon-links" aria-label="About Carrel">
        <Link to="/privacy">Privacy</Link>
        <Link to="/copyright">Copyright</Link>
      </nav>
    </footer>
  )
}

/** Settings are for everyone (appearance needs no account); the rest depends on whether someone is signed in. */
function Account() {
  const signedIn = Boolean(useSession())
  const profile = useProfile().data
  const username = profile?.username
  const navigate = useNavigate()
  const { pathname } = useLocation()

  async function signOut() {
    // A private profile, and its shelves, are only there for its reader.
    const ownPrivateProfile =
      username && !profile.isPublic && pathname.split('/')[1]?.toLowerCase() === `@${username.toLowerCase()}`
    // Leave first: once signed out, a page that needs signing in sends the visitor to sign in instead.
    if (isMembersOnly(pathname) || ownPrivateProfile) {
      navigate('/', { replace: true })
    }
    await supabase.auth.signOut()
  }

  return (
    <div className="account">
      {signedIn && username && (
        <NavLink to={profilePath(username)} className="account-profile">
          <Avatar url={profile.avatarUrl} size="small" />
          <span className="account-name">{username}</span>
        </NavLink>
      )}
      <Tooltip content="Settings">
        <NavLink to="/settings" className="account-settings" aria-label="Settings">
          <CogIcon />
        </NavLink>
      </Tooltip>
      {signedIn ? (
        <button type="button" className="link-button" onClick={signOut}>
          Sign out
        </button>
      ) : (
        <Link to={signInPath()} className="link-button">
          Sign in
        </Link>
      )}
    </div>
  )
}

/** The "settings" icon from Lucide (lucide.dev, ISC licence). */
function CogIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}
