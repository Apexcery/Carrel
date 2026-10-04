// Recent searches, kept in this browser only and separately for each signed-in account.

const MAX_RECENT = 8

function key(userId: string): string {
  return `carrel-recent-searches:${userId}`
}

export function getRecentSearches(userId: string): string[] {
  try {
    const stored = JSON.parse(localStorage.getItem(key(userId)) ?? '[]')
    return Array.isArray(stored) ? stored.filter((s): s is string => typeof s === 'string') : []
  } catch {
    return []
  }
}

/** Moves the search to the top of the list (ignoring case), keeping the most recent few. */
export function addRecentSearch(userId: string, query: string) {
  const recent = [query, ...getRecentSearches(userId).filter((s) => s.toLowerCase() !== query.toLowerCase())]
  write(userId, recent.slice(0, MAX_RECENT))
}

export function clearRecentSearches(userId: string) {
  write(userId, [])
}

// Storage can be unavailable (private windows, blocked site data); history is then simply not kept.
function write(userId: string, searches: string[]) {
  try {
    if (searches.length === 0) {
      localStorage.removeItem(key(userId))
    } else {
      localStorage.setItem(key(userId), JSON.stringify(searches))
    }
  } catch {
    // Ignore; see above.
  }
}
