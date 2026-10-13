// Readium keeps a chapter's frame at visibility: hidden until the frame has answered two messages (focus, then going
// to its place), each of which waits for an animation frame. Firefox runs animation frames in hidden frames about once
// a second, so opening a book or changing chapter took 2 s there. Making the frame visible as Readium starts showing it
// lets them run at once (95 ms); Readium still has it at opacity 0, without pointer events, until it's ready, and every
// other frame stays hidden. Readium doesn't offer a hook for this, so it wraps FrameManager.show; the package is pinned
// to an exact version for that reason.
import { FrameManager } from '@readium/navigator'

const prototype = FrameManager.prototype as unknown as {
  show: (...args: unknown[]) => Promise<void>
  frame: HTMLIFrameElement
}
const show = prototype.show

prototype.show = function (this: typeof prototype, ...args: unknown[]) {
  this.frame.style.visibility = 'visible'
  return show.apply(this, args)
}
