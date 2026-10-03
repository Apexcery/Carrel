import { Route, Routes } from 'react-router'
import { useSession } from './auth'
import { Layout } from './components/Layout'
import { BookPage } from './pages/BookPage'
import { BookResolver } from './pages/BookResolver'
import { HomePage } from './pages/HomePage'
import { NotFoundPage } from './pages/NotFoundPage'
import { SearchPage } from './pages/SearchPage'
import { SeriesPage } from './pages/SeriesPage'
import { SignInPage } from './pages/SignInPage'

function App() {
  const session = useSession()

  if (session === undefined) {
    return null
  }
  if (session === null) {
    return <SignInPage />
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
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
