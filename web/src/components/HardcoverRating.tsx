/** Hardcover's aggregate rating. Hardcover's terms require crediting it whenever it's shown. */
export function HardcoverRating({
  rating,
  count,
  hardcoverId,
}: {
  rating: number | null
  count: number | null
  hardcoverId?: number | null
}) {
  if (rating === null || !count) {
    return null
  }

  const credit = hardcoverId ? (
    <a href={`https://hardcover.app/id/book/${hardcoverId}`} target="_blank" rel="noreferrer">
      Hardcover
    </a>
  ) : (
    'Hardcover'
  )

  return (
    <span className="rating">
      <span className="rating-stars" aria-hidden="true">
        ★
      </span>
      <span className="rating-value">{rating.toFixed(2)}</span>
      <span className="rating-credit">
        {count.toLocaleString()} {count === 1 ? 'rating' : 'ratings'} on {credit}
      </span>
    </span>
  )
}
