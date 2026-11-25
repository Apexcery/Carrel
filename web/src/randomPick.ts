// Picking a book at random for the dice in the search box. The app's randomPick (Suggestions.kt) does the same; keep
// the two in step.

import type { BookSuggestion } from './types'

/** Books to pick from, and how likely they are to be picked. */
export interface PickGroup {
  books: BookSuggestion[]
  weight: number
}

/** The groups without books in the reader's library, each book in the first group with it, and no empty groups. */
function pickable(groups: PickGroup[], owned: Set<number>): PickGroup[] {
  const seen = new Set<number>()
  return groups
    .map((group) => ({
      ...group,
      books: group.books.filter((b) => {
        if (owned.has(b.hardcoverId) || seen.has(b.hardcoverId)) {
          return false
        }
        seen.add(b.hardcoverId)
        return true
      }),
    }))
    .filter((group) => group.books.length > 0)
}

/**
 * The pickable book `seed` (from 0 up to 1) lands on, leaving out the one picked last while there are others: where it
 * falls among the groups, each as wide as the square root of its weight (so a reader's favourite genre doesn't crowd
 * out the rest), picks the group, and where it falls within that group the book. Undefined when none are pickable.
 */
export function randomPick(
  groups: PickGroup[],
  owned: Set<number>,
  previous: number | null,
  seed: number,
): BookSuggestion | undefined {
  const eligible = pickable(groups, owned)
  const others = eligible
    .map((group) => ({ ...group, books: group.books.filter((b) => b.hardcoverId !== previous) }))
    .filter((group) => group.books.length > 0)
  const pool = others.length > 0 ? others : eligible
  let at = seed * pool.reduce((sum, group) => sum + Math.sqrt(group.weight), 0)
  for (const group of pool) {
    const width = Math.sqrt(group.weight)
    if (at < width || group === pool[pool.length - 1]) {
      return group.books[Math.min(Math.floor((at / width) * group.books.length), group.books.length - 1)]
    }
    at -= width
  }
  return undefined
}
