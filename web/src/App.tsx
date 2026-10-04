import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { useSession } from './auth'
import { Layout, PublicLayout } from './components/Layout'
import { AccountSettings } from './pages/AccountSettings'
import { AppearanceSettings } from './pages/AppearanceSettings'
import { BookPage } from './pages/BookPage'
import { BookResolver } from './pages/BookResolver'
import { ChooseUsernamePage } from './pages/ChooseUsernamePage'
import { CopyrightPage } from './pages/CopyrightPage'
import { HomePage } from './pages/HomePage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PrivacyPage } from './pages/PrivacyPage'
import { SearchPage } from './pages/SearchPage'
import { SeriesPage } from './pages/SeriesPage'
import { SettingsPage } from './pages/SettingsPage'
import { ShelfPage } from './pages/ShelfPage'
import { SignInPage } from './pages/SignInPage'
import { useProfile } from './profile'
import { ErrorNotice } from './components/ErrorNotice'

/** Pages anyone can read, signed in or not: people need the privacy notice before signing up. */
const PUBLIC_PAGES: Record<string, ReactNode> = {
  '/privacy': <PrivacyPage />,
  '/copyright': <CopyrightPage />,
}

function App() {
  const session = useSession()
  const publicPage = PUBLIC_PAGES[useLocation().pathname]

  if (session === undefined || session === null) {
    if (publicPage) {
      return <PublicLayout>{publicPage}</PublicLayout>
    }
    return session === null ? <SignInPage /> : null
  }
  return <SignedIn publicPage={publicPage} />
}

/** Everything behind sign-in. A reader without a username chooses one first, but can still read the public pages. */
function SignedIn({ publicPage }: { publicPage: ReactNode | undefined }) {
  const profile = useProfile()

  if (profile.isError) {
    return <ErrorNotice error={profile.error} onRetry={() => profile.refetch()} />
  }
  if (!profile.data) {
    return null
  }
  if (profile.data.username === null) {
    return publicPage ? <PublicLayout>{publicPage}</PublicLayout> : <ChooseUsernamePage />
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
        <Route path="privacy" element={<PrivacyPage />} />
        <Route path="copyright" element={<CopyrightPage />} />
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
