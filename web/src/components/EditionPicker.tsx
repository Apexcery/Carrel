import { useState } from 'react'
import { Combobox, ComboboxButton, ComboboxInput, ComboboxOption, ComboboxOptions } from '@headlessui/react'
import { formatDuration } from '../format'
import type { Edition } from '../types'

const GROUPS: { format: Edition['format']; label: string }[] = [
  { format: 'print', label: 'Print' },
  { format: 'ebook', label: 'Ebook' },
  { format: 'audio', label: 'Audiobook' },
  { format: null, label: 'Other' },
]

/** Searchable edition picker, grouped by format. Matches publisher, year, length, language and ISBN. */
export function EditionPicker({
  id,
  editions,
  value,
  onChange,
}: {
  id: string
  editions: Edition[]
  value: number | null
  onChange: (editionId: number | null) => void
}) {
  const [query, setQuery] = useState('')
  const selected = editions.find((e) => e.id === value) ?? null
  const terms = query.toLowerCase().split(/\s+/).filter(Boolean)
  const matches = editions.filter((edition) => {
    const text = searchText(edition)
    return terms.every((term) => text.includes(term))
  })

  return (
    <Combobox
      value={selected}
      by="id"
      onChange={(edition: Edition | null) => onChange(edition?.id ?? null)}
      onClose={() => setQuery('')}
      immediate
    >
      <div className="edition-picker">
        <ComboboxInput
          id={id}
          placeholder="Not specified. Type to search"
          displayValue={(edition: Edition | null) => (edition ? editionLabel(edition) : '')}
          onChange={(event) => setQuery(event.target.value)}
          // Select the current edition's label so typing replaces it with a search.
          onFocus={(event) => event.target.select()}
        />
        <ComboboxButton className="edition-picker-button" aria-label="Show editions">
          ▾
        </ComboboxButton>
      </div>
      <ComboboxOptions anchor="bottom start" className="edition-options">
        {!query && (
          <ComboboxOption value={null} className="edition-option">
            Not specified
          </ComboboxOption>
        )}
        {GROUPS.map(({ format, label }) => {
          const group = matches.filter((e) => e.format === format)
          return (
            group.length > 0 && (
              <div key={label} role="group" aria-label={label}>
                <div className="edition-group" aria-hidden="true">
                  {label}
                </div>
                {group.map((edition) => (
                  <ComboboxOption key={edition.id} value={edition} className="edition-option">
                    <span className="edition-option-main">{[edition.publisher ?? 'Unknown publisher', year(edition)].filter(Boolean).join(' · ')}</span>
                    <span className="edition-option-detail mono">{details(edition)}</span>
                  </ComboboxOption>
                ))}
              </div>
            )
          )
        })}
        {matches.length === 0 && <div className="edition-empty">No editions match “{query}”.</div>}
      </ComboboxOptions>
    </Combobox>
  )
}

export function editionLabel(edition: Edition): string {
  const format = GROUPS.find((g) => g.format === edition.format)?.label ?? 'Edition'
  return [format, edition.publisher, year(edition), length(edition)].filter(Boolean).join(' · ')
}

function year(edition: Edition): string | undefined {
  return edition.releaseDate?.slice(0, 4)
}

function length(edition: Edition): string | null {
  return edition.pageCount ? `${edition.pageCount} pp.` : edition.audioSeconds ? formatDuration(edition.audioSeconds) : null
}

function details(edition: Edition): string {
  return [length(edition), edition.language?.toUpperCase(), edition.isbn13 ?? edition.isbn10].filter(Boolean).join(' · ')
}

function searchText(edition: Edition): string {
  return [editionLabel(edition), edition.language, edition.isbn13, edition.isbn10].filter(Boolean).join(' ').toLowerCase()
}
