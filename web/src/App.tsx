import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router'
import { useSession } from './auth'
import { ErrorNotice } from './components/ErrorNotice'
import { Layout } from './components/Layout'
import { AccountSettings } from './pages/AccountSettings'
import { AppearanceSettings } from './pages/AppearanceSettings'
import { BookPage } from './pages/BookPage'
import { BookResolver } from './pages/BookResolver'
import { ChooseUsernamePage } from './pages/ChooseUsernamePage'
import { ConfirmEmailPage } from './pages/ConfirmEmailPage'
import { CopyrightPage } from './pages/CopyrightPage'
import { HomePage } from './pages/HomePage'
import { ImportExportSettings } from './pages/ImportExportSettings'
import { NotFoundPage } from './pages/NotFoundPage'
import { PrivacyPage } from './pages/PrivacyPage'
import { ProfilePage } from './pages/ProfilePage'
import { SearchPage } from './pages/SearchPage'
import { SeriesPage } from './pages/SeriesPage'
import { SetPasswordPage } from './pages/SetPasswordPage'
import { SettingsPage } from './pages/SettingsPage'
import { OwnShelfRedirect, ShelfPage } from './pages/ShelfPage'
import { SignInPage } from './pages/SignInPage'
import { useProfile } from './profile'
import { signInPath } from './signIn'

/**
 * Pages a reader who hasn't chosen a username yet can still open. The email-link pages are here too: a link from an
 * invitation signs in a reader with no username, and the page has to stay put while it finishes.
 */
const BEFORE_USERNAME = ['/privacy', '/copyright', '/auth/confirm', '/auth/set-password']

/** Browsing is open to everyone; signing in is only for keeping a library. */
function App() {
  const session = useSession()
  const profile = useProfile()
  const { pathname } = useLocation()

  if (session === undefined) {
    return null
  }
  // A signed-in reader without a username chooses one first.
  if (session && !BEFORE_USERNAME.includes(pathname)) {
    if (profile.isError) {
      return <ErrorNotice error={profile.error} onRetry={() => profile.refetch()} />
    }
    if (!profile.data) {
      return null
    }
    if (profile.data.username === null) {
      return <ChooseUsernamePage />
    }
  }

  return (
    <Routes>
      <Route path="sign-in" element={<SignInPage />} />
      <Route path="auth/confirm" element={<ConfirmEmailPage />} />
      <Route path="auth/set-password" element={<SetPasswordPage />} />
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="books/:id" element={<BookPage />} />
        <Route path="books/hardcover/:sourceId" element={<BookResolver source="hardcover" />} />
        <Route path="books/openlibrary/:sourceId" element={<BookResolver source="openlibrary" />} />
        <Route path="series/hardcover/:hardcoverId" element={<SeriesPage />} />
        <Route path="privacy" element={<PrivacyPage />} />
        <Route path="copyright" element={<CopyrightPage />} />
        <Route path="settings" element={<SettingsPage />}>
          <Route index element={<Navigate to={session ? 'account' : 'appearance'} replace />} />
          <Route path="appearance" element={<AppearanceSettings />} />
          <Route element={<RequireSignIn />}>
            <Route path="account" element={<AccountSettings />} />
            <Route path="import-export" element={<ImportExportSettings />} />
            {/* The section's old address, from before it had exports. */}
            <Route path="import" element={<Navigate to="/settings/import-export" replace />} />
          </Route>
        </Route>
        <Route element={<RequireSignIn />}>
          {/* Shelves' old address, from before they were under the reader's profile. */}
          <Route path="shelves/:slug" element={<OwnShelfRedirect />} />
        </Route>
        {/* Profiles are at /@username; a route can't match part of a segment, so the pages check for the @. */}
        <Route path=":handle" element={<ProfilePage />} />
        <Route path=":handle/shelves/:slug" element={<ShelfPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

/** Sends signed-out visitors to sign in, then back here. */
function RequireSignIn() {
  return useSession() ? <Outlet /> : <Navigate to={signInPath()} replace />
}

export default App
