import { NavLink, Outlet } from 'react-router'

/** Settings, split into sections with their own addresses (/settings/account, /settings/appearance). */
export function SettingsPage() {
  return (
    <section className="settings">
      <header className="search-header">
        <h1 className="search-title">Settings</h1>
      </header>
      <div className="settings-layout">
        <nav className="settings-nav" aria-label="Settings sections">
          <NavLink to="account">Account</NavLink>
          <NavLink to="appearance">Appearance</NavLink>
        </nav>
        <div className="settings-content">
          <Outlet />
        </div>
      </div>
    </section>
  )
}
