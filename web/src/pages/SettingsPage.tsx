import { NavLink, Outlet } from 'react-router'
import { useSession } from '../auth'

/**
 * Settings, split into sections with their own addresses (/settings/account, /settings/appearance). Signed-out
 * visitors only get Appearance.
 */
export function SettingsPage() {
  const signedIn = Boolean(useSession())
  return (
    <section className="settings">
      <header className="search-header">
        <h1 className="search-title">Settings</h1>
      </header>
      <div className="settings-layout">
        <nav className="settings-nav" aria-label="Settings sections">
          {signedIn && <NavLink to="account">Account</NavLink>}
          <NavLink to="appearance">Appearance</NavLink>
        </nav>
        <div className="settings-content">
          <Outlet />
        </div>
      </div>
    </section>
  )
}
