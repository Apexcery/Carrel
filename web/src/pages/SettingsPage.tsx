import { useEffect, useRef } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useSession } from '../auth'

/**
 * Settings, split into sections with their own addresses (/settings/account, /settings/import-export, /settings/appearance).
 * Signed-out visitors only get Appearance. The last entry leaves Settings for the Android app's page.
 */
export function SettingsPage() {
  const signedIn = Boolean(useSession())
  const { pathname } = useLocation()
  const nav = useRef<HTMLElement>(null)

  // On a phone the sections are one row that scrolls sideways (see .settings-nav): keep the open one in view.
  useEffect(() => {
    const row = nav.current
    const current = row?.querySelector('[aria-current="page"]')
    if (!row || !current) {
      return
    }
    const rowBox = row.getBoundingClientRect()
    const box = current.getBoundingClientRect()
    if (box.right > rowBox.right) {
      row.scrollLeft += box.right - rowBox.right
    } else if (box.left < rowBox.left) {
      row.scrollLeft -= rowBox.left - box.left
    }
  }, [pathname, signedIn])

  return (
    <section className="settings">
      <header className="search-header">
        <h1 className="search-title">Settings</h1>
      </header>
      <div className="settings-layout">
        <nav className="settings-nav" aria-label="Settings sections" ref={nav}>
          {signedIn && <NavLink to="account">Account</NavLink>}
          {signedIn && <NavLink to="import-export">Import &amp; Export</NavLink>}
          <NavLink to="appearance">Appearance</NavLink>
          <NavLink to="/android">Android app</NavLink>
        </nav>
        <div className="settings-content">
          <Outlet />
        </div>
      </div>
    </section>
  )
}
