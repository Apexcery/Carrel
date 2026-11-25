import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMatch, useNavigate } from 'react-router'
import { apiGet, apiGetOrNull } from '../api'
import { useSession } from '../auth'
import { randomPick, type PickGroup } from '../randomPick'
import type { GenreShelves, LibraryItem, TopGenrePicks } from '../types'
import { useOwnedHardcoverIds } from './SuggestionShelf'
import { Tooltip } from './Tooltip'

const LABEL = 'Pick a random book'
/** How long a note under the dice stays. */
const NOTE_FOR = 4000

/** The book the dice opened last, so the next press opens a different one. Kept for the visit. */
let lastPick: number | null = null

/**
 * The dice in the search box, which opens a random book's page. On a genre's page it picks from that genre's shelves;
 * elsewhere, signed in, from the popular books in the reader's top genres (fetched on the first press), a genre more
 * likely the more of the reader's books have it. Books the reader has are left out. Hidden when there's nothing it
 * could pick from.
 */
export function RandomBookButton() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const signedIn = Boolean(useSession())
  const slug = useMatch('/genres/:slug')?.params.slug
  // The genre page's own query, so this uses what the page loaded.
  const genre = useQuery({
    queryKey: ['genre', slug],
    queryFn: () => apiGetOrNull<GenreShelves>(`/genres/${slug}`),
    enabled: Boolean(slug),
  })
  const library = useQuery({ queryKey: ['library'], queryFn: () => apiGet<LibraryItem[]>('/library'), enabled: signedIn })
  const owned = useOwnedHardcoverIds()
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  const shelves = genre.data
  const genreBooks = shelves ? [...shelves.popular, ...shelves.topRated, ...shelves.newReleases] : []
  // The reader's top genres come from the books they've read or are reading, so without one there are none.
  const hasTopGenres = library.data?.some((item) => item.entry.status === 'read' || item.entry.status === 'reading')
  if (!owned || !(slug ? genreBooks.length > 0 : signedIn && hasTopGenres)) {
    return null
  }
  const ownedIds = owned

  function show(text: string) {
    setNote(text)
    setTimeout(() => setNote(''), NOTE_FOR)
  }

  async function pick() {
    setNote('')
    let groups: PickGroup[] = [{ books: genreBooks, weight: 1 }]
    if (!slug) {
      setBusy(true)
      try {
        // Not under 'library': books the reader has are left out here, so a change to their shelves needn't fetch it
        // again (Hardcover's limits are tight).
        const top = await queryClient.fetchQuery({
          queryKey: ['genre-picks', 'top'],
          queryFn: () => apiGet<TopGenrePicks[]>('/library/genre-picks/top'),
        })
        groups = top.map((picks) => ({ books: picks.books, weight: picks.readerBooks }))
      } catch {
        show('Couldn’t pick a book. Try again.')
        return
      } finally {
        setBusy(false)
      }
    }
    const book = randomPick(groups, ownedIds, lastPick, Math.random())
    if (!book) {
      show('You have every book it could pick already.')
      return
    }
    lastPick = book.hardcoverId
    navigate(`/books/hardcover/${book.hardcoverId}`)
  }

  return (
    <span className="random-book">
      <Tooltip content={LABEL}>
        <button type="button" className="random-book-button" aria-label={LABEL} disabled={busy} onClick={pick}>
          <DiceIcon />
        </button>
      </Tooltip>
      <span className="random-book-note" role="status">
        {note}
      </span>
    </span>
  )
}

/** A die showing five, after the "dice-5" icon from Lucide (lucide.dev, ISC licence), with its pips filled. */
function DiceIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinejoin="round" aria-hidden="true">
      <rect width="18" height="18" x="3" y="3" rx="2" />
      <g fill="currentColor" stroke="none">
        <circle cx="8" cy="8" r="1.5" />
        <circle cx="16" cy="8" r="1.5" />
        <circle cx="12" cy="12" r="1.5" />
        <circle cx="8" cy="16" r="1.5" />
        <circle cx="16" cy="16" r="1.5" />
      </g>
    </svg>
  )
}
