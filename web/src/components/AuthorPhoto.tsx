import { useState } from 'react'
import { Avatar } from './Avatar'

/**
 * An author's round photo, or the silhouette when there is none (or it fails to load). Decorative: the name beside it
 * always says who it is. Unlike a reader's picture it loads without crossOrigin, since Hardcover's images don't allow it.
 */
export function AuthorPhoto({ url, size }: { url: string | null; size: 'small' | 'large' }) {
  const [failed, setFailed] = useState(false)
  if (!url || failed) {
    return <Avatar url={null} size={size} />
  }
  return (
    <img
      className={`avatar ${size}`}
      src={url}
      alt=""
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
    />
  )
}
