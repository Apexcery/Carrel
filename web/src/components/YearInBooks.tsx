import type { LibraryItem } from '../types'

const THIS_YEAR = new Date().getFullYear()
const MONTHS = Array.from({ length: 12 }, (_, month) => new Date(2000, month, 1))
const monthName = new Intl.DateTimeFormat(undefined, { month: 'long' })
const monthLetter = new Intl.DateTimeFormat(undefined, { month: 'narrow' })
/** Titles listed in a month's tooltip before "and N more". */
const TOOLTIP_TITLES = 4

/**
 * The reader's year so far: books finished (rereads count again), pages read, average rating, and books finished each
 * month, with their titles on hover or focus. Pages come from each book's edition, so books without a page count add
 * none.
 */
export function YearInBooks({ items }: { items: LibraryItem[] }) {
  const finished = items.flatMap((item) =>
    item.entry.reads
      .filter((read) => read.finishedOn?.startsWith(`${THIS_YEAR}-`))
      .map((read) => ({ item, month: Number(read.finishedOn!.slice(5, 7)) - 1 })),
  )
  const perMonth = MONTHS.map((_, month) => finished.filter((f) => f.month === month).length)
  const most = Math.max(...perMonth, 1)
  const pages = finished.reduce((sum, f) => sum + (f.item.book.pageCount ?? 0), 0)
  const ratings = [...new Set(finished.map((f) => f.item))].flatMap((item) => item.entry.rating ?? [])
  const averageRating = ratings.length > 0 ? ratings.reduce((sum, r) => sum + r, 0) / ratings.length : null

  return (
    <section className="year-in-books">
      <h3 className="section-title">Your {THIS_YEAR}</h3>
      {finished.length === 0 ? (
        <p className="muted">Nothing finished yet this year. Books you mark as read will add up here.</p>
      ) : (
        <>
          <dl className="year-stats">
            <div>
              <dt>Books finished</dt>
              <dd>{finished.length}</dd>
            </div>
            <div>
              <dt>Pages read</dt>
              <dd>{pages.toLocaleString()}</dd>
            </div>
            {/* Left out until at least one of this year's books has a rating. */}
            {averageRating !== null && (
              <div>
                <dt>Average rating</dt>
                <dd>{averageRating.toFixed(1)}</dd>
              </div>
            )}
          </dl>
          <ol className="year-months" aria-label="Books finished each month">
            {MONTHS.map((date, month) => {
              const titles = finished.filter((f) => f.month === month).map((f) => f.item.book.title)
              const count = titles.length === 0 ? 'none finished' : `${titles.length} ${titles.length === 1 ? 'book' : 'books'}`
              const summary = `${monthName.format(date)}: ${count}`
              // Months near either end open their tooltip inwards, so it stays within the column.
              const align = month < 3 ? ' align-start' : month > 8 ? ' align-end' : ''
              return (
                <li
                  key={month}
                  className={`year-month-column${align}`}
                  tabIndex={titles.length > 0 ? 0 : undefined}
                  aria-label={titles.length > 0 ? `${summary}: ${titles.join('; ')}` : summary}
                >
                  <span className="year-tooltip" aria-hidden="true">
                    <span className="year-tooltip-heading">
                      {monthName.format(date)} · {count}
                    </span>
                    {titles.slice(0, TOOLTIP_TITLES).map((title, i) => (
                      <span key={i} className="year-tooltip-title">
                        {title}
                      </span>
                    ))}
                    {titles.length > TOOLTIP_TITLES && (
                      <span className="year-tooltip-more">and {titles.length - TOOLTIP_TITLES} more</span>
                    )}
                  </span>
                  <span className="year-bar-track">
                    {titles.length > 0 && <span className="year-bar" style={{ height: `${(titles.length / most) * 100}%` }} />}
                  </span>
                  <span className="year-month mono" aria-hidden="true">
                    {monthLetter.format(date)}
                  </span>
                </li>
              )
            })}
          </ol>
        </>
      )}
    </section>
  )
}
