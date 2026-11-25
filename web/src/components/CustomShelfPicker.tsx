import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiSend } from '../api'
import { CUSTOM_SHELVES_KEY, storeCustomShelf, useCustomShelves } from '../customShelves'
import type { CustomShelf } from '../types'
import { ErrorNotice } from './ErrorNotice'

/**
 * A book's place on the reader's custom shelves, whether or not it's in their library: each shelf is a switch that
 * puts the book on it or takes it off, straight away. "New shelf" starts one (private) with the book on it.
 */
export function CustomShelfPicker({ bookId }: { bookId: number }) {
  const queryClient = useQueryClient()
  const shelves = useCustomShelves()
  const [naming, setNaming] = useState(false)
  const [name, setName] = useState('')

  const toggle = useMutation({
    mutationFn: ({ shelf, on }: { shelf: CustomShelf; on: boolean }) =>
      apiSend<CustomShelf>(on ? 'PUT' : 'DELETE', `/shelves/${shelf.id}/books/${bookId}`),
    onSuccess: (saved) => storeCustomShelf(queryClient, saved),
  })

  // Fetched again either way: a new shelf goes in A–Z order, and one made just before a failure still exists.
  const create = useMutation({
    mutationFn: async () => {
      const shelf = await apiSend<CustomShelf>('POST', '/shelves', { name })
      return apiSend<CustomShelf>('PUT', `/shelves/${shelf.id}/books/${bookId}`)
    },
    onSuccess: () => {
      setName('')
      setNaming(false)
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: CUSTOM_SHELVES_KEY }),
  })

  if (!shelves.data) {
    return shelves.isError ? <ErrorNotice error={shelves.error} onRetry={() => shelves.refetch()} /> : null
  }

  const busy = toggle.isPending || create.isPending

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <div className="shelf-row">
      <span className="shelf-label">Your shelves</span>
      <div className="custom-shelves">
        {shelves.data.map((shelf) => {
          const on = shelf.books.some((b) => b.book.id === bookId)
          return (
            <button
              key={shelf.id}
              type="button"
              className="shelf-chip"
              aria-pressed={on}
              disabled={busy}
              onClick={() => toggle.mutate({ shelf, on: !on })}
            >
              {shelf.name}
            </button>
          )
        })}
        {naming ? (
          <form className="shelf-name-form" onSubmit={submit}>
            <label htmlFor="new-shelf-name" className="visually-hidden">
              New shelf’s name
            </label>
            <input
              id="new-shelf-name"
              value={name}
              maxLength={50}
              autoComplete="off"
              placeholder="e.g. Favourites"
              autoFocus
              onChange={(e) => setName(e.target.value)}
            />
            <button type="submit" className="primary-button" disabled={busy}>
              {create.isPending ? 'Adding…' : 'Add'}
            </button>
            <button
              type="button"
              className="link-button"
              onClick={() => {
                create.reset()
                setName('')
                setNaming(false)
              }}
            >
              Cancel
            </button>
          </form>
        ) : (
          <button type="button" className="link-button" onClick={() => setNaming(true)}>
            New shelf
          </button>
        )}
      </div>
      {(toggle.error ?? create.error) && <ErrorNotice error={toggle.error ?? create.error} />}
    </div>
  )
}
