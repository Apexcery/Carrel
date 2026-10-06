/**
 * A reader's round profile picture, or a silhouette for a reader without one. Decorative: the username beside it
 * always names the reader. Pictures load without cookies (crossOrigin), so the browser ignores the cookie Supabase's
 * CDN tries to set on them, which Firefox would otherwise reject with a console error.
 */
export function Avatar({ url, size }: { url: string | null; size: 'small' | 'large' }) {
  return url ? (
    <img className={`avatar ${size}`} src={url} alt="" crossOrigin="anonymous" />
  ) : (
    <span className={`avatar ${size} avatar-empty`} aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="currentColor">
        <circle cx="12" cy="9" r="4.5" />
        <path d="M3.5 24c0-5 3.8-8.5 8.5-8.5s8.5 3.5 8.5 8.5z" />
      </svg>
    </span>
  )
}
