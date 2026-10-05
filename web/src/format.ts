import type { Contributor } from './types'

/** Series position as written on a spine: 1, 1.5, 3.5. */
export function seriesPosition(position: number | null): string | null {
  return position === null ? null : String(Number(position.toFixed(2)))
}

/** Hides a subtitle that only repeats the end of the title, e.g. "Mistborn: The Final Empire" / "The Final Empire". */
export function displaySubtitle(title: string, subtitle: string | null): string | null {
  return subtitle && !title.toLowerCase().endsWith(subtitle.toLowerCase()) ? subtitle : null
}

export function listNames(names: string[]): string {
  if (names.length <= 2) {
    return names.join(' and ')
  }
  return `${names.slice(0, -1).join(', ')}, and ${names[names.length - 1]}`
}

const ROLE_PHRASES: Record<string, string> = {
  translator: 'Translated by',
  illustrator: 'Illustrated by',
  narrator: 'Narrated by',
  editor: 'Edited by',
  foreword: 'Foreword by',
  afterword: 'Afterword by',
  'cover artist': 'Cover by',
}

/** "Translated by X" style credits for everyone who isn't an author, grouped by role. */
export function otherCredits(contributors: Contributor[]): string[] {
  const byRole = new Map<string, string[]>()
  for (const c of contributors.filter((c) => c.role !== 'author')) {
    byRole.set(c.role, [...(byRole.get(c.role) ?? []), c.name])
  }
  return [...byRole].map(([role, names]) => {
    const phrase = ROLE_PHRASES[role] ?? `${role.charAt(0).toUpperCase()}${role.slice(1)}:`
    return `${phrase} ${listNames(names)}`
  })
}

/** "2006-07-17" as "17 Jul 2006", without timezone shifts. */
export function formatDate(isoDate: string | null): string | null {
  if (!isoDate) {
    return null
  }
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function formatDuration(seconds: number): string {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.round((seconds % 3600) / 60)
  return hours > 0 ? `${hours} h ${minutes} m` : `${minutes} m`
}

/** Today's date in the reader's timezone, as yyyy-mm-dd. */
export function localToday(): string {
  return new Date().toLocaleDateString('en-CA')
}

export const STATUS_LABELS: Record<string, string> = {
  want_to_read: 'Want to read',
  reading: 'Reading',
  paused: 'Paused',
  read: 'Read',
  did_not_finish: 'Did not finish',
}
