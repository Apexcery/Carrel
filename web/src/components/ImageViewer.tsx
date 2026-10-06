import { Dialog, DialogBackdrop, DialogPanel } from '@headlessui/react'

/**
 * A picture shown larger over the page, on a card with a Close button. Escape or a click outside closes it too, and
 * focus stays inside while it's open. Round shows a profile picture as the circle it was cropped to.
 */
export function ImageViewer({
  src,
  alt,
  open,
  onClose,
  round = false,
  crossOrigin,
}: {
  src: string
  alt: string
  open: boolean
  onClose: () => void
  round?: boolean
  /** As on the image shown in the page, so the browser can reuse it (see Avatar). */
  crossOrigin?: 'anonymous'
}) {
  return (
    <Dialog open={open} onClose={onClose} className="image-viewer">
      <DialogBackdrop className="image-viewer-backdrop" />
      <div className="image-viewer-frame">
        <DialogPanel className="image-viewer-panel">
          <button type="button" className="link-button image-viewer-close" onClick={onClose}>
            Close
          </button>
          <img
            src={src}
            alt={alt}
            referrerPolicy="no-referrer"
            crossOrigin={crossOrigin}
            className={round ? 'image-viewer-image round' : 'image-viewer-image'}
          />
        </DialogPanel>
      </div>
    </Dialog>
  )
}
