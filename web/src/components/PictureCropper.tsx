import { useState } from 'react'
import Cropper, { type Area } from 'react-easy-crop'
import 'react-easy-crop/react-easy-crop.css'

const MAX_ZOOM = 4

/**
 * Drag to position and a slider to zoom, with a round preview of the square that will be kept. The library's own
 * styles come from its stylesheet, since the site's Content Security Policy blocks the <style> tag it would add.
 */
export function PictureCropper({ src, onCropped }: { src: string; onCropped: (area: Area) => void }) {
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  return (
    <div className="picture-cropper">
      <div className="picture-crop-area">
        <Cropper
          image={src}
          crop={crop}
          zoom={zoom}
          maxZoom={MAX_ZOOM}
          aspect={1}
          cropShape="round"
          showGrid={false}
          disableAutomaticStylesInjection
          onCropChange={setCrop}
          onZoomChange={setZoom}
          onCropComplete={(_, pixels) => onCropped(pixels)}
        />
      </div>
      <label className="picture-zoom">
        <span>Zoom</span>
        <input
          type="range"
          min={1}
          max={MAX_ZOOM}
          step={0.01}
          value={zoom}
          onChange={(e) => setZoom(Number(e.target.value))}
        />
      </label>
    </div>
  )
}
