import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiSend } from '../api'
import { PROFILE_KEY } from '../profile'
import { readsFinishedIn } from '../shelves'
import type { LibraryItem, Profile, ReadingGoal } from '../types'

const THIS_YEAR = new Date().getFullYear()
/** The most a goal can be; the API allows the same. */
const MAX_BOOKS = 1000

/**
 * This year's reading goal on a reader's profile: books finished (rereads count again) against the goal, and whether
 * that's ahead of or behind the pace the goal needs. The reader sets and changes it here; visitors only see it once
 * it's set.
 */
export function ReadingGoalPanel({
  items,
  goals,
  username,
  isOwn,
}: {
  items: LibraryItem[]
  goals: ReadingGoal[]
  username: string
  isOwn: boolean
}) {
  const [editing, setEditing] = useState(false)
  const goal = goals.find((g) => g.year === THIS_YEAR)
  if (!goal && !isOwn) {
    return null
  }
  const finished = readsFinishedIn(items, THIS_YEAR).length

  return (
    <section className="reading-goal">
      <h2 className="section-title">{isOwn ? `${THIS_YEAR} reading goal` : `@${username}’s ${THIS_YEAR} goal`}</h2>
      {editing ? (
        <GoalForm current={goal?.books ?? null} onDone={() => setEditing(false)} />
      ) : goal ? (
        <>
          <p className="goal-count">
            <span className="goal-number">{finished}</span> of {goal.books} {goal.books === 1 ? 'book' : 'books'}
          </p>
          <div
            className="progress-bar"
            role="progressbar"
            aria-label="Reading goal"
            aria-valuemin={0}
            aria-valuemax={goal.books}
            aria-valuenow={Math.min(finished, goal.books)}
          >
            <span style={{ width: `${Math.min(100, (finished / goal.books) * 100)}%` }} />
          </div>
          <p className="goal-pace">
            <span className="mono">{pace(finished, goal.books)}</span>
            {isOwn && (
              <button type="button" className="link-button" onClick={() => setEditing(true)}>
                Change
              </button>
            )}
          </p>
        </>
      ) : (
        <button type="button" className="secondary-button" onClick={() => setEditing(true)}>
          Set a reading goal
        </button>
      )}
    </section>
  )
}

/** Against where the reader would be by now, to the nearest book, reading at an even rate to reach the goal. */
function pace(finished: number, books: number): string {
  if (finished >= books) {
    return 'Goal reached'
  }
  const start = new Date(THIS_YEAR, 0, 1).getTime()
  const end = new Date(THIS_YEAR + 1, 0, 1).getTime()
  const difference = finished - Math.round((books * (Date.now() - start)) / (end - start))
  const count = `${Math.abs(difference)} ${Math.abs(difference) === 1 ? 'book' : 'books'}`
  return difference > 0 ? `${count} ahead of schedule` : difference < 0 ? `${count} behind schedule` : 'On track'
}

/** Sets this year's goal, or removes it; saved goals replace the profile's, so the panel shows them straight away. */
function GoalForm({ current, onDone }: { current: number | null; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [books, setBooks] = useState(current === null ? '' : String(current))
  const value = Number(books)
  const valid = books.trim() !== '' && Number.isInteger(value) && value >= 1 && value <= MAX_BOOKS

  const saved = (goals: ReadingGoal[]) => {
    queryClient.setQueryData<Profile>(PROFILE_KEY, (profile) => profile && { ...profile, goals })
    onDone()
  }
  const save = useMutation({
    mutationFn: () => apiSend<ReadingGoal[]>('PUT', `/profile/goals/${THIS_YEAR}`, { books: value }),
    onSuccess: saved,
  })
  const remove = useMutation({
    mutationFn: () => apiSend<ReadingGoal[]>('DELETE', `/profile/goals/${THIS_YEAR}`),
    onSuccess: saved,
  })
  const busy = save.isPending || remove.isPending
  const error = save.error ?? remove.error

  function submit(event: FormEvent) {
    event.preventDefault()
    if (valid && !busy) {
      save.mutate()
    }
  }

  return (
    <form className="goal-form" onSubmit={submit}>
      <label className="goal-input">
        <span className="visually-hidden">Books to finish in {THIS_YEAR}</span>
        <input
          type="number"
          inputMode="numeric"
          min={1}
          max={MAX_BOOKS}
          required
          autoFocus
          value={books}
          aria-invalid={books.trim() !== '' && !valid}
          onChange={(e) => setBooks(e.target.value)}
        />
        <span aria-hidden="true">books</span>
      </label>
      {books.trim() !== '' && !valid && (
        <p className="form-message error">Choose a goal from 1 to {MAX_BOOKS.toLocaleString()} books.</p>
      )}
      {error && <p className="form-message error">{error.message}</p>}
      <div className="settings-actions">
        <button type="submit" className="primary-button" disabled={!valid || busy}>
          {save.isPending ? 'Saving…' : 'Save'}
        </button>
        <button type="button" className="link-button" onClick={onDone}>
          Cancel
        </button>
        {current !== null && (
          <button type="button" className="link-button" disabled={busy} onClick={() => remove.mutate()}>
            {remove.isPending ? 'Removing…' : 'Remove'}
          </button>
        )}
      </div>
    </form>
  )
}
