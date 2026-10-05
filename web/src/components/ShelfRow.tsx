import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'

// A book's width plus the gap after it, matching .shelf-strip in styles.css.
const BOOK_GAP = 14
const BOOK_STEP = 92 + BOOK_GAP

/**
 * A shelf: its heading, then one row of books above a thick bottom border. The row scrolls sideways without a
 * scrollbar (the border already looks like one); when it holds more than fits, ‹ › buttons beside the heading move it
 * along.
 * Children are the row's <li> items.
 */
export function ShelfRow({ heading, children }: { heading: ReactNode; children: ReactNode }) {
  const scroller = useRef<HTMLDivElement>(null)
  const [room, setRoom] = useState({ left: false, right: false })
  // Where an arrow's smooth scroll will stop, while it's on its way.
  const target = useRef<number | null>(null)

  const measure = useCallback(() => {
    const el = scroller.current
    if (el) {
      // A pixel of slack for fractional widths.
      if (target.current !== null && Math.abs(el.scrollLeft - target.current) <= 1) {
        target.current = null
      }
      // Set the buttons for where an arrow's scroll will stop, not where it is now.
      const at = target.current ?? el.scrollLeft
      const left = at > 1
      const right = at + el.clientWidth < el.scrollWidth - 1
      // Runs on every scroll event, so only re-render when a button changes.
      setRoom((room) => (room.left === left && room.right === right ? room : { left, right }))
    }
  }, [])

  // Also when the reader takes over mid-scroll, so the buttons follow wherever the row ends up.
  const scrollEnded = () => {
    target.current = null
    measure()
  }

  useEffect(() => {
    const el = scroller.current
    if (!el) {
      return
    }
    measure()
    // Re-measure when the shelf or its row changes size, e.g. on resizing the window or when books arrive.
    const observer = new ResizeObserver(measure)
    observer.observe(el)
    if (el.firstElementChild) {
      observer.observe(el.firstElementChild)
    }
    return () => observer.disconnect()
  }, [measure])

  // Move by a shelf's worth of whole books, so each click shows a new set and a cover edge stays lined up with the
  // start of the shelf. Smooth unless the reader prefers less motion; it's set here rather than in CSS, where it would
  // also slow scrolling by wheel, middle-click, or touchpad.
  const move = (direction: 1 | -1) => {
    const el = scroller.current
    if (el) {
      const perShelf = Math.floor((el.clientWidth + BOOK_GAP) / BOOK_STEP)
      // From the previous click's stopping point, so quick clicks add up.
      const from = target.current ?? el.scrollLeft
      const to = Math.min(Math.max(from + direction * Math.max(perShelf, 1) * BOOK_STEP, 0), el.scrollWidth - el.clientWidth)
      target.current = to
      measure()
      el.scrollTo({ left: to, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
    }
  }

  return (
    <>
      <div className="shelf-row-header">
        {heading}
        {(room.left || room.right) && (
          <div className="shelf-arrows">
            <button type="button" className="shelf-arrow" aria-label="Previous books" disabled={!room.left} onClick={() => move(-1)}>
              <ChevronIcon direction="left" />
            </button>
            <button type="button" className="shelf-arrow" aria-label="More books" disabled={!room.right} onClick={() => move(1)}>
              <ChevronIcon direction="right" />
            </button>
          </div>
        )}
      </div>
      <div className="shelf-scroll" ref={scroller} onScroll={measure} onScrollEnd={scrollEnded}>
        <ul className="shelf-strip">{children}</ul>
      </div>
    </>
  )
}

/** The "chevron-left" and "chevron-right" icons from Lucide (lucide.dev, ISC licence). */
function ChevronIcon({ direction }: { direction: 'left' | 'right' }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={direction === 'left' ? 'm15 18-6-6 6-6' : 'm9 18 6-6-6-6'} />
    </svg>
  )
}
