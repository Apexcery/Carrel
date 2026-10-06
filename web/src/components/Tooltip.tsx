import {
  cloneElement,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type FocusEvent,
  type PointerEvent,
  type ReactElement,
  type ReactNode,
} from 'react'
import { createPortal } from 'react-dom'

/** Space between the tooltip and its trigger. */
const GAP = 6
/** Space kept between the tooltip and the edges of the window. */
const EDGE = 8

interface TriggerProps {
  onPointerEnter?: (event: PointerEvent<HTMLElement>) => void
  onPointerLeave?: (event: PointerEvent<HTMLElement>) => void
  onFocus?: (event: FocusEvent<HTMLElement>) => void
  onBlur?: (event: FocusEvent<HTMLElement>) => void
}

/**
 * The site's tooltip, used instead of the browser's: shown above its trigger (below, if there's no room) on hover with a
 * mouse, or on keyboard focus. It's drawn over the page, so scrolling shelves and narrow columns don't cut it off. It's
 * only for sighted readers, so the trigger's own label must say the same for screen readers.
 */
export function Tooltip({ content, children }: { content: ReactNode; children: ReactElement<TriggerProps> }) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const props = children.props

  // Scrolling would leave it behind, and Escape dismisses it, as with the browser's own.
  useEffect(() => {
    if (!anchor) {
      return
    }
    const hide = () => setAnchor(null)
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && hide()
    window.addEventListener('scroll', hide, true)
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('scroll', hide, true)
      window.removeEventListener('keydown', onKey)
    }
  }, [anchor])

  const trigger = cloneElement(children, {
    onPointerEnter: (event: PointerEvent<HTMLElement>) => {
      props.onPointerEnter?.(event)
      // A tap would flash it on the way to following the link.
      if (event.pointerType === 'mouse') {
        setAnchor(event.currentTarget)
      }
    },
    onPointerLeave: (event: PointerEvent<HTMLElement>) => {
      props.onPointerLeave?.(event)
      setAnchor(null)
    },
    onFocus: (event: FocusEvent<HTMLElement>) => {
      props.onFocus?.(event)
      // Keyboard focus only: a click focuses too, and the pointer has already shown it.
      if (event.currentTarget.matches(':focus-visible')) {
        setAnchor(event.currentTarget)
      }
    },
    onBlur: (event: FocusEvent<HTMLElement>) => {
      props.onBlur?.(event)
      setAnchor(null)
    },
  })

  return (
    <>
      {trigger}
      {anchor && createPortal(<Bubble anchor={anchor}>{content}</Bubble>, document.body)}
    </>
  )
}

function Bubble({ anchor, children }: { anchor: HTMLElement; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null)

  // Placed once its size is known, before it's painted.
  useLayoutEffect(() => {
    const tip = ref.current!.getBoundingClientRect()
    const target = anchor.getBoundingClientRect()
    const above = target.top - GAP - tip.height
    const centred = target.left + target.width / 2 - tip.width / 2
    const maxLeft = document.documentElement.clientWidth - EDGE - tip.width
    setPosition({
      top: above >= EDGE ? above : target.bottom + GAP,
      left: Math.max(EDGE, Math.min(centred, maxLeft)),
    })
  }, [anchor, children])

  return (
    <div
      ref={ref}
      className="tooltip"
      aria-hidden="true"
      style={position ? { top: position.top, left: position.left } : { visibility: 'hidden' }}
    >
      {children}
    </div>
  )
}
