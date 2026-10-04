import { Navigate, Route, Routes } from 'react-router'
import { useSession } from './auth'
import { Layout } from './components/Layout'
import { AccountSettings } from './pages/AccountSettings'
import { AppearanceSettings } from './pages/AppearanceSettings'
import { BookPage } from './pages/BookPage'
import { BookResolver } from './pages/BookResolver'
import { ChooseUsernamePage } from './pages/ChooseUsernamePage'
import { HomePage } from './pages/HomePage'
import { NotFoundPage } from './pages/NotFoundPage'
import { SearchPage } from './pages/SearchPage'
import { SeriesPage } from './pages/SeriesPage'
import { SettingsPage } from './pages/SettingsPage'
import { ShelfPage } from './pages/ShelfPage'
import { SignInPage } from './pages/SignInPage'
import { useProfile } from './profile'
import { ErrorNotice } from './components/ErrorNotice'

function App() {
  const session = useSession()

  if (session === undefined) {
    return null
  }
  if (session === null) {
    return <SignInPage />
  }
  return <SignedIn />
}

/** Everything behind sign-in. A reader without a username chooses one first. */
function SignedIn() {
  const profile = useProfile()

  if (profile.isError) {
    return <ErrorNotice error={profile.error} onRetry={() => profile.refetch()} />
  }
  if (!profile.data) {
    return null
  }
  if (profile.data.username === null) {
    return <ChooseUsernamePage />
  }

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="books/:id" element={<BookPage />} />
        <Route path="books/hardcover/:sourceId" element={<BookResolver source="hardcover" />} />
        <Route path="books/openlibrary/:sourceId" element={<BookResolver source="openlibrary" />} />
        <Route path="series/hardcover/:hardcoverId" element={<SeriesPage />} />
        <Route path="shelves/:slug" element={<ShelfPage />} />
        <Route path="settings" element={<SettingsPage />}>
          <Route index element={<Navigate to="account" replace />} />
          <Route path="account" element={<AccountSettings />} />
          <Route path="appearance" element={<AppearanceSettings />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
