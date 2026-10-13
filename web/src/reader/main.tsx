import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource-variable/literata/opsz.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import '../styles.css'
import './firefoxFix'
import { ReaderPage } from './ReaderPage'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ReaderPage />
  </StrictMode>,
)
