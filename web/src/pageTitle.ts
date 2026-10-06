import { useEffect } from 'react'

/** Sets the browser tab's title to "Carrel | {title}" while the page is open, or just "Carrel" without one. */
export function usePageTitle(title?: string | null) {
  useEffect(() => {
    document.title = title ? `Carrel | ${title}` : 'Carrel'
  }, [title])
}
