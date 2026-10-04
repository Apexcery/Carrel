// Theme and accent preferences, kept in this browser. index.html applies both before first paint to avoid a flash.
// Accent colours are defined in styles.css under [data-accent]; red is the default and needs no attribute.

export type ThemePreference = 'system' | 'light' | 'dark'
export type Accent = 'red' | 'green' | 'blue' | 'gold'

const THEME_KEY = 'carrel-theme'
const ACCENT_KEY = 'carrel-accent'
const ACCENTS: Accent[] = ['red', 'green', 'blue', 'gold']

export function getTheme(): ThemePreference {
  const value = read(THEME_KEY)
  return value === 'light' || value === 'dark' ? value : 'system'
}

export function setTheme(theme: ThemePreference) {
  write(THEME_KEY, theme === 'system' ? null : theme)
  if (theme === 'system') {
    document.documentElement.removeAttribute('data-theme')
  } else {
    document.documentElement.setAttribute('data-theme', theme)
  }
}

export function getAccent(): Accent {
  const value = read(ACCENT_KEY)
  return ACCENTS.find((accent) => accent === value) ?? 'red'
}

export function setAccent(accent: Accent) {
  write(ACCENT_KEY, accent === 'red' ? null : accent)
  if (accent === 'red') {
    document.documentElement.removeAttribute('data-accent')
  } else {
    document.documentElement.setAttribute('data-accent', accent)
  }
}

// Storage can be unavailable (private windows, blocked site data); the preference then lasts for the visit only.
function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) {
      localStorage.removeItem(key)
    } else {
      localStorage.setItem(key, value)
    }
  } catch {
    // Ignore; see read().
  }
}
