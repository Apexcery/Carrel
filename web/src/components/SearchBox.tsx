import { useState, type FormEvent } from 'react'
import { Combobox, ComboboxInput, ComboboxOption, ComboboxOptions } from '@headlessui/react'
import { useNavigate } from 'react-router'
import { useSession } from '../auth'
import { clearRecentSearches, getRecentSearches } from '../recentSearches'

const CLEAR = Symbol('clear')
type Choice = string | typeof CLEAR

/**
 * The header search box, with its own dropdown of recent searches (the browser's autocomplete is off).
 * The typed text is always the first option, so Enter searches for what was typed.
 */
export function SearchBox({ initialQuery }: { initialQuery: string }) {
  const navigate = useNavigate()
  const userId = useSession()?.user.id ?? ''
  const [text, setText] = useState(initialQuery)
  // Read when the box gains focus, so searches made since are included.
  const [recent, setRecent] = useState<string[]>([])
  // Only filter by what's in the box once the reader types; on focus it may just hold the current search.
  const [typing, setTyping] = useState(false)

  const typed = typing ? text.trim() : ''
  const matches = recent.filter((s) => s.toLowerCase().includes(typed.toLowerCase()) && s.toLowerCase() !== typed.toLowerCase())

  function search(query: string) {
    const q = query.trim()
    if (q) {
      setText(q)
      navigate(`/search?q=${encodeURIComponent(q)}`)
    }
  }

  function choose(choice: Choice | null) {
    if (choice === CLEAR) {
      clearRecentSearches(userId)
      setRecent([])
    } else if (choice !== null) {
      search(choice)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    search(text)
  }

  return (
    <form className="search-box" role="search" onSubmit={submit}>
      <Combobox<Choice | null> value={null} onChange={choose} immediate>
        <label htmlFor="search" className="visually-hidden">
          Search books
        </label>
        <ComboboxInput
          id="search"
          type="search"
          autoComplete="off"
          placeholder="Title, author, or ISBN"
          maxLength={200}
          // Keep the typed text when the dropdown closes (the combobox has no selected value to show).
          displayValue={() => text}
          onChange={(event) => {
            setText(event.target.value)
            setTyping(true)
          }}
          onFocus={(event) => {
            setRecent(getRecentSearches(userId))
            setTyping(false)
            // Typing then replaces the current search rather than adding to it.
            event.target.select()
          }}
        />
        {(typed || matches.length > 0) && (
          <ComboboxOptions anchor="bottom start" className="search-options">
            {typed && (
              <ComboboxOption value={typed} className="search-option">
                Search for “{typed}”
              </ComboboxOption>
            )}
            {matches.length > 0 && (
              <div role="group" aria-label="Recent searches">
                <div className="search-options-heading" aria-hidden="true">
                  Recent searches
                </div>
                {matches.map((query) => (
                  <ComboboxOption key={query} value={query} className="search-option">
                    {query}
                  </ComboboxOption>
                ))}
                <ComboboxOption value={CLEAR} className="search-option search-option-clear">
                  Clear recent searches
                </ComboboxOption>
              </div>
            )}
          </ComboboxOptions>
        )}
      </Combobox>
      <button type="submit">Search</button>
    </form>
  )
}
