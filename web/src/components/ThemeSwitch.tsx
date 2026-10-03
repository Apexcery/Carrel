import { useState } from 'react'
import { getTheme, setTheme, type ThemePreference } from '../theme'

const OPTIONS: { value: ThemePreference; label: string }[] = [
  { value: 'system', label: 'Auto' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

export function ThemeSwitch() {
  const [theme, setThemeState] = useState(getTheme)

  function choose(value: ThemePreference) {
    setTheme(value)
    setThemeState(value)
  }

  return (
    <div className="theme-switch" role="radiogroup" aria-label="Colour theme">
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={theme === option.value}
          onClick={() => choose(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
