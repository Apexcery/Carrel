import { useState } from 'react'

const BINDINGS = ['#6b2a1f', '#2f4a3a', '#2b3a55', '#5a4a2a', '#4a2b45', '#1f4547']

/** A book cover image, or a plain cloth binding with the title when there is no image (or it fails to load). */
export function Cover({
  url,
  title,
  author,
  size = 'medium',
}: {
  url: string | null
  title: string
  author?: string
  size?: 'small' | 'medium' | 'large'
}) {
  const [failed, setFailed] = useState(false)

  if (url && !failed) {
    return (
      <img
        className={`cover cover-${size}`}
        src={url}
        alt={`Cover of ${title}`}
        loading="lazy"
        referrerPolicy="no-referrer"
        onError={() => setFailed(true)}
      />
    )
  }

  return (
    <div className={`cover cover-${size} cover-blank`} style={{ background: BINDINGS[hash(title) % BINDINGS.length] }} role="img" aria-label={`No cover for ${title}`}>
      <span className="cover-blank-title">{title}</span>
      {author && <span className="cover-blank-author">{author}</span>}
    </div>
  )
}

function hash(text: string): number {
  let h = 0
  for (let i = 0; i < text.length; i++) {
    h = (h * 31 + text.charCodeAt(i)) >>> 0
  }
  return h
}
