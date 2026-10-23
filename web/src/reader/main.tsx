import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '@fontsource-variable/literata/opsz.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
// The reader's fonts, shown in their own faces in its settings.
import '@fontsource/atkinson-hyperlegible/400.css'
import '@fontsource/opendyslexic/400.css'
import '../styles.css'
import './firefoxFix'
import { LibraryPage } from './LibraryPage'
import { ReaderPage } from './ReaderPage'

const queryClient = new QueryClient({ defaultOptions: { queries: { refetchOnWindowFocus: false } } })

// One page for the eReader and online libraries, which both need its security policy: ?book= reads a book, and
// ?library= browses a library.
const library = new URLSearchParams(window.location.search).has('library')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>{library ? <LibraryPage /> : <ReaderPage />}</QueryClientProvider>
  </StrictMode>,
)
