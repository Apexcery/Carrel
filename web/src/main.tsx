import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import '@fontsource-variable/literata/opsz.css'
import '@fontsource-variable/literata/opsz-italic.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import './styles.css'
import { ApiError } from './api'
import { AuthProvider } from './auth'
import App from './App.tsx'
import { SAVED_FOR } from './savedQueries'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      gcTime: SAVED_FOR,
      refetchOnWindowFocus: false,
      // Don't retry answers that won't change (not found, rate limited, bad request).
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
    },
  },
})

// Cloudflare redirects a profile opened by its address, /@reader, to /%40reader. Put the @ back before the app starts,
// so the address bar and copied links show it and the app sees the same path its own links use.
if (window.location.pathname.includes('%40')) {
  const { pathname, search, hash } = window.location
  window.history.replaceState(window.history.state, '', pathname.replace(/%40/gi, '@') + search + hash)
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
