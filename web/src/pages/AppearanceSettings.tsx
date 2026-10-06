import { useState } from 'react'
import { ThemeSwitch } from '../components/ThemeSwitch'
import { usePageTitle } from '../pageTitle'
import { getAccent, setAccent, type Accent } from '../theme'

const ACCENTS: { value: Accent; label: string }[] = [
  { value: 'red', label: 'Red' },
  { value: 'green', label: 'Green' },
  { value: 'blue', label: 'Blue' },
  { value: 'gold', label: 'Gold' },
]

export function AppearanceSettings() {
  usePageTitle('Appearance')
  return (
    <>
      <section className="settings-section">
        <h2 className="section-title">Theme</h2>
        <p className="settings-note">Auto follows your device’s light or dark setting.</p>
        <ThemeSwitch />
      </section>
      <section className="settings-section">
        <h2 className="section-title">Accent colour</h2>
        <p className="settings-note">Used for links, buttons, and highlights.</p>
        <AccentPicker />
      </section>
      <p className="settings-note">Appearance is saved in this browser.</p>
    </>
  )
}

function AccentPicker() {
  const [accent, setAccentState] = useState(getAccent)

  function choose(value: Accent) {
    setAccent(value)
    setAccentState(value)
  }

  return (
    <div className="option-switch" role="radiogroup" aria-label="Accent colour">
      {ACCENTS.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={accent === option.value}
          onClick={() => choose(option.value)}
        >
          {/* data-accent gives the swatch that accent's colours, whichever accent the page is using. */}
          <span className="accent-swatch" data-accent={option.value} aria-hidden="true" />
          {option.label}
        </button>
      ))}
    </div>
  )
}
