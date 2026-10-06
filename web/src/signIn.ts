/** The sign-in page's address, returning to `next` (the current page by default) afterwards. */
export function signInPath(next?: string, mode?: 'sign-up'): string {
  const params = new URLSearchParams()
  const target = next ?? `${window.location.pathname}${window.location.search}`
  if (target !== '/') {
    params.set('next', target)
  }
  if (mode) {
    params.set('mode', mode)
  }
  const query = params.toString()
  return query ? `/sign-in?${query}` : '/sign-in'
}

/** Where to go after signing in: only paths on this site, so the link can't send anyone elsewhere. */
export function safeNext(next: string | null): string {
  // Browsers treat a backslash like a slash, so "/\evil.example" would leave the site just like "//evil.example".
  return next && next.startsWith('/') && !next.startsWith('//') && !next.includes('\\') && !next.startsWith('/sign-in') ? next : '/'
}

/** Pages that only make sense signed in; signing out on one of them goes home. */
export function isMembersOnly(pathname: string): boolean {
  return pathname.startsWith('/shelves/') || pathname === '/settings/account' || pathname === '/settings/import-export'
}
