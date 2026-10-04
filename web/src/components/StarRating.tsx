import { useState } from 'react'

const STARS = [1, 2, 3, 4, 5]

/** Half-star rating. Each star has two buttons (left half, right half); choosing the current rating clears it. */
export function StarRating({
  value,
  onChange,
  disabled,
}: {
  value: number | null
  onChange: (rating: number | null) => void
  disabled?: boolean
}) {
  const [hover, setHover] = useState<number | null>(null)
  const shown = hover ?? value ?? 0

  return (
    <div className="stars" role="radiogroup" aria-label="Your rating" onMouseLeave={() => setHover(null)}>
      {STARS.map((star) => (
        <span key={star} className="star">
          <span className="star-empty" aria-hidden="true">
            ★
          </span>
          <span
            className="star-full"
            aria-hidden="true"
            style={{ width: shown >= star ? '100%' : shown >= star - 0.5 ? '50%' : '0%' }}
          >
            ★
          </span>
          {[star - 0.5, star].map((rating) => (
            <button
              key={rating}
              type="button"
              role="radio"
              aria-checked={value === rating}
              aria-label={`${rating} ${rating === 1 ? 'star' : 'stars'}`}
              className={rating % 1 ? 'star-half star-half-left' : 'star-half star-half-right'}
              disabled={disabled}
              onMouseEnter={() => setHover(rating)}
              onFocus={() => setHover(rating)}
              onBlur={() => setHover(null)}
              onClick={() => onChange(value === rating ? null : rating)}
            />
          ))}
        </span>
      ))}
      <span className="stars-value mono">{value ? value.toFixed(1) : 'Not rated'}</span>
    </div>
  )
}

/** A read-only rating for shelves. */
export function StarDisplay({ value }: { value: number }) {
  return (
    <span className="stars stars-small" role="img" aria-label={`Rated ${value} out of 5`}>
      {STARS.map((star) => (
        <span key={star} className="star">
          <span className="star-empty">★</span>
          <span className="star-full" style={{ width: value >= star ? '100%' : value >= star - 0.5 ? '50%' : '0%' }}>
            ★
          </span>
        </span>
      ))}
    </span>
  )
}
