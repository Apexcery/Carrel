import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiGetOrNull, apiSend } from '../api'
import { formatDuration, localToday, STATUS_LABELS } from '../format'
import type { BookDetail, Edition, LibraryEntry, ProgressUnit, ReadingStatus, SaveEntryRequest } from '../types'
import { EditionPicker } from './EditionPicker'
import { ErrorNotice } from './ErrorNotice'
import { Select } from './Select'
import { StarRating } from './StarRating'

const STATUSES: ReadingStatus[] = ['want_to_read', 'reading', 'paused', 'read', 'did_not_finish']
/** Started but not finished: these show and edit progress. */
const IN_PROGRESS: ReadingStatus[] = ['reading', 'paused']

/**
 * The signed-in reader's shelf entry for a book. Status and rating save as soon as they change; progress, edition
 * and reading history are edited together behind "Edit" and saved with one button.
 */
export function LibraryPanel({ book }: { book: BookDetail }) {
  const queryClient = useQueryClient()
  const entryKey = ['library-entry', book.id]
  const entry = useQuery({
    queryKey: entryKey,
    queryFn: () => apiGetOrNull<LibraryEntry>(`/library/books/${book.id}`),
  })
  const [editing, setEditing] = useState(false)
  // Bumped each time the form opens, so it starts from the saved values rather than abandoned edits.
  const [editSession, setEditSession] = useState(0)

  function stored(updated: LibraryEntry | null) {
    queryClient.setQueryData(entryKey, updated)
    queryClient.invalidateQueries({ queryKey: ['library'] })
  }

  const save = useMutation({
    mutationFn: (changes: Partial<SaveEntryRequest>) => {
      const current = entry.data
      const body: SaveEntryRequest = {
        status: current?.status ?? 'want_to_read',
        editionId: current?.editionId ?? null,
        rating: current?.rating ?? null,
        progressUnit: current?.progressUnit ?? null,
        progressValue: current?.progressValue ?? null,
        ...changes,
        today: localToday(),
      }
      return apiSend<LibraryEntry>('PUT', `/library/books/${book.id}`, body)
    },
    onSuccess: stored,
  })

  const remove = useMutation({
    mutationFn: () => apiSend<void>('DELETE', `/library/books/${book.id}`),
    onSuccess: () => {
      setEditing(false)
      stored(null)
    },
  })

  const busy = save.isPending || remove.isPending

  if (entry.isPending) {
    return <section className="shelf-panel" aria-busy="true" />
  }
  if (entry.isError) {
    return <ErrorNotice error={entry.error} onRetry={() => entry.refetch()} />
  }

  const current = entry.data

  return (
    <section className="shelf-panel" aria-label="Your shelf">
      <p className="kicker">{current ? 'On your shelf' : 'Add to your library'}</p>

      <div className="status-picker" role="radiogroup" aria-label="Reading status">
        {STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            role="radio"
            aria-checked={current?.status === status}
            disabled={busy}
            onClick={() => save.mutate({ status })}
          >
            {STATUS_LABELS[status]}
          </button>
        ))}
      </div>

      {current && (
        <>
          <div className="shelf-row">
            <span className="shelf-label">Your rating</span>
            <StarRating value={current.rating} disabled={busy} onChange={(rating) => save.mutate({ rating })} />
          </div>

          {IN_PROGRESS.includes(current.status) && (
            <div className="progress">
              <div className="progress-bar" role="progressbar" aria-label="Progress" aria-valuemin={0} aria-valuemax={100}
                aria-valuenow={current.progressPercent ?? undefined}>
                <span style={{ width: `${current.progressPercent ?? 0}%` }} />
              </div>
              <p className="progress-summary mono">{progressSummary(current)}</p>
            </div>
          )}

          {!editing && (
            <button
              type="button"
              className="link-button shelf-edit"
              onClick={() => {
                setEditSession(editSession + 1)
                setEditing(true)
              }}
            >
              Edit
            </button>
          )}

          {/* Stays mounted so it can slide open and closed; inert while closed. */}
          <div className="shelf-edit-wrap" data-open={editing} inert={!editing}>
            <div className="shelf-edit-inner">
              <EditForm
                // A status change can add or close a read, so the form also starts over when status or reads change.
                key={`${editSession}:${current.status}:${current.reads.map((r) => r.id).join(',')}`}
                entry={current}
                editions={book.editions}
                busy={busy}
                onSave={(changes) => save.mutate(changes, { onSuccess: () => setEditing(false) })}
                onCancel={() => {
                  save.reset()
                  setEditing(false)
                }}
                onRemove={() => remove.mutate()}
              />
            </div>
          </div>
        </>
      )}

      {(save.error ?? remove.error) && <ErrorNotice error={save.error ?? remove.error} />}
    </section>
  )
}

interface ReadDraft {
  key: string
  id: number | null
  startedOn: string
  finishedOn: string
}

function EditForm({
  entry,
  editions,
  busy,
  onSave,
  onCancel,
  onRemove,
}: {
  entry: LibraryEntry
  editions: Edition[]
  busy: boolean
  onSave: (changes: Partial<SaveEntryRequest>) => void
  onCancel: () => void
  onRemove: () => void
}) {
  const [unit, setUnit] = useState<ProgressUnit>(entry.progressUnit ?? 'page')
  const [progressText, setProgressText] = useState(entry.progressValue === null ? '' : formatValue(entry.progressUnit ?? 'page', entry.progressValue))
  const [editionId, setEditionId] = useState<number | null>(entry.editionId)
  const [reads, setReads] = useState<ReadDraft[]>(
    entry.reads.map((r) => ({ key: String(r.id), id: r.id, startedOn: r.startedOn ?? '', finishedOn: r.finishedOn ?? '' })),
  )
  const [problem, setProblem] = useState<string | null>(null)
  const reading = IN_PROGRESS.includes(entry.status)

  function updateRead(key: string, changes: Partial<ReadDraft>) {
    setReads(reads.map((r) => (r.key === key ? { ...r, ...changes } : r)))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const progressValue = progressText.trim() === '' ? null : parseValue(unit, progressText)
    if (reading && progressText.trim() !== '' && progressValue === null) {
      setProblem(`Enter progress as a number${unit === 'seconds' ? ' or hours:minutes, like 3:45' : ''}.`)
      return
    }
    if (reads.some((r) => r.startedOn && r.finishedOn && r.startedOn > r.finishedOn)) {
      setProblem('A read can’t finish before it starts.')
      return
    }
    setProblem(null)
    onSave({
      editionId,
      ...(reading ? { progressUnit: progressValue === null ? null : unit, progressValue } : {}),
      reads: reads.map((r) => ({ id: r.id, startedOn: r.startedOn || null, finishedOn: r.finishedOn || null })),
    })
  }

  return (
    <form className="shelf-edit-form" onSubmit={submit}>
      {reading && (
        <div className="shelf-row">
          <label className="shelf-label" htmlFor="progress-value">
            Progress
          </label>
          <div className="progress-inputs">
            <input
              id="progress-value"
              inputMode={unit === 'seconds' ? 'text' : 'decimal'}
              placeholder={unit === 'page' ? 'Page' : unit === 'percent' ? 'Percent' : 'h:mm'}
              value={progressText}
              onChange={(e) => setProgressText(e.target.value)}
            />
            <Select<ProgressUnit>
              aria-label="Progress unit"
              value={unit}
              onChange={setUnit}
              options={[
                { value: 'page', label: 'pages' },
                { value: 'percent', label: '%' },
                { value: 'seconds', label: 'time (h:mm)' },
              ]}
            />
          </div>
        </div>
      )}

      {editions.length > 0 && (
        <div className="shelf-row">
          <label className="shelf-label" htmlFor="edition">
            Your edition
          </label>
          <EditionPicker id="edition" editions={editions} value={editionId} onChange={setEditionId} />
        </div>
      )}

      <fieldset className="reads">
        <legend className="shelf-label">Reading history</legend>
        {reads.length === 0 && <p className="muted">No read dates yet.</p>}
        <ul>
          {reads.map((read) => (
            <li key={read.key} className="read-row">
              <label>
                <span className="mono">Started</span>
                <input type="date" value={read.startedOn} onChange={(e) => updateRead(read.key, { startedOn: e.target.value })} />
              </label>
              <label>
                <span className="mono">Finished</span>
                <input type="date" value={read.finishedOn} onChange={(e) => updateRead(read.key, { finishedOn: e.target.value })} />
              </label>
              <button type="button" className="link-button" onClick={() => setReads(reads.filter((r) => r.key !== read.key))}>
                Remove
              </button>
            </li>
          ))}
        </ul>
        <button
          type="button"
          className="link-button"
          onClick={() => setReads([...reads, { key: crypto.randomUUID(), id: null, startedOn: '', finishedOn: '' }])}
        >
          Add another read
        </button>
      </fieldset>

      {problem && (
        <p className="form-message error" role="alert">
          {problem}
        </p>
      )}

      <div className="shelf-actions">
        <button type="submit" className="primary-button" disabled={busy}>
          {busy ? 'Saving…' : 'Save'}
        </button>
        <button type="button" className="link-button" onClick={onCancel}>
          Cancel
        </button>
        <button
          type="button"
          className="link-button shelf-remove"
          disabled={busy}
          onClick={() => {
            if (window.confirm('Remove this book from your library? Its rating, progress, and read dates will be deleted.')) {
              onRemove()
            }
          }}
        >
          Remove from library
        </button>
      </div>
    </form>
  )
}

function progressSummary(entry: LibraryEntry): string {
  const { progressUnit: unit, progressValue: value, progressPercent: percent, progressTotal: total } = entry
  if (unit === null || value === null) {
    return 'No progress recorded yet.'
  }
  const done =
    unit === 'page'
      ? `Page ${value}${total ? ` of ${total}` : ''}`
      : unit === 'seconds'
        ? `${formatDuration(value)}${total ? ` of ${formatDuration(total)}` : ''}`
        : null
  const share = percent === null ? null : `${Math.round(percent)}%`
  return [share, done].filter(Boolean).join(' · ') || `${value}`
}

function formatValue(unit: ProgressUnit, value: number): string {
  if (unit !== 'seconds') {
    return String(value)
  }
  const minutes = Math.round(value / 60)
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`
}

function parseValue(unit: ProgressUnit, text: string): number | null {
  const trimmed = text.trim()
  if (unit === 'seconds') {
    const match = /^(\d+)(?::(\d{1,2}))?$/.exec(trimmed)
    return match ? (Number(match[1]) * 60 + Number(match[2] ?? 0)) * 60 : null
  }
  const value = Number(trimmed)
  if (trimmed === '' || !Number.isFinite(value) || value < 0 || (unit === 'percent' && value > 100)) {
    return null
  }
  return value
}
