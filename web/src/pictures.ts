import type { Area } from 'react-easy-crop'

/** The cropped square is sent at this size; the API shrinks it again to what it stores. */
const OUTPUT_SIZE = 512

/** The chosen square of the picture, as a PNG (which keeps any transparency) for the upload. */
export async function cropPicture(src: string, area: Area): Promise<Blob> {
  const image = new Image()
  image.src = src
  await image.decode()
  const canvas = document.createElement('canvas')
  canvas.width = OUTPUT_SIZE
  canvas.height = OUTPUT_SIZE
  canvas.getContext('2d')!.drawImage(image, area.x, area.y, area.width, area.height, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE)
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('That picture couldn’t be cropped.'))),
      'image/png',
    ),
  )
}
