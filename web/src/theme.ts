// Theme preference, kept in this browser. index.html applies it before first paint to avoid a flash.
// Accent colours are defined in styles.css under [data-accent]; a picker can set that attribute the same way.

export type ThemePreference = 'system' | 'light' | 'dark'

const THEME_KEY = 'carrel-theme'

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
