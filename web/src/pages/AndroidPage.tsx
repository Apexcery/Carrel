import { useEffect, useRef, useState } from 'react'
import { Dialog, DialogBackdrop, DialogPanel, DialogTitle } from '@headlessui/react'
import { useQuery } from '@tanstack/react-query'
import { ErrorNotice } from '../components/ErrorNotice'
import { ImageViewer } from '../components/ImageViewer'
import { formatDate } from '../format'
import { usePageTitle } from '../pageTitle'

/**
 * The Android app's page: what it does, and the latest APK. GitHub sends /releases/latest/download/carrel.apk to the
 * newest release's file, which stays right while every release marked latest is an Android one (see android.yml).
 */
const DOWNLOAD_URL = 'https://github.com/Apexcery/Carrel/releases/latest/download/carrel.apk'
const RELEASES_URL = 'https://github.com/Apexcery/Carrel/releases'
/** The same list the app checks for updates (allowed in the CSP's connect-src). */
const RELEASES_API = 'https://api.github.com/repos/Apexcery/Carrel/releases?per_page=30'

type Theme = 'light' | 'dark'

/** Screenshots in public/android-screens, each taken in both themes as {name}-{theme}.webp. */
const SHOTS = [
  { name: 'home', caption: 'For you: suggestions from what you’ve read', width: 540, height: 1200 },
  { name: 'book', caption: 'A book’s page, with your shelf and rating', width: 540, height: 1200 },
  { name: 'library', caption: 'Your library, shelf by shelf', width: 540, height: 1200 },
  { name: 'reader', caption: 'Reading your own EPUBs', width: 540, height: 1200 },
  { name: 'finished', caption: 'Finishing a book', width: 540, height: 1200 },
  { name: 'spread', caption: 'Two pages side by side on an unfolded foldable', width: 690, height: 828 },
]

const FEATURES = [
  {
    title: 'Your whole library',
    text: 'Shelve books as Want to read, Reading, Paused, Read, or Did not finish, rate them, log your progress, and keep up with your reading goal.',
  },
  {
    title: 'Works offline',
    text: 'Shelve, rate, and update your progress without a signal. Carrel keeps the changes and syncs them once you’re back online.',
  },
  {
    title: 'A reader for your EPUBs',
    text: 'Read DRM-free EPUBs in the app. Your progress saves as you go, and your place follows you to your other devices.',
  },
  {
    title: 'Two pages on wide screens',
    text: 'On a tablet or an unfolded foldable, books open as a two-page spread.',
  },
  {
    title: 'Your own catalogues',
    text: 'Browse and download books from any OPDS catalogue, such as a Calibre content server.',
  },
  {
    title: 'Links and sharing',
    text: 'Carrel links to books, series, genres, profiles, and shelves open in the app. Share a book from Goodreads, StoryGraph, Amazon, or Hardcover to find it in Carrel.',
  },
  {
    title: 'Shortcuts',
    text: 'Press and hold the icon to jump to Search, your Library, or the books you’re reading.',
  },
  {
    title: 'Keeps itself up to date',
    text: 'Install it once. Carrel tells you when a new version is out, shows what’s changed, and installs it for you.',
  },
]

/** The theme the site is showing now: the one chosen in Settings, or the device's. */
function currentTheme(): Theme {
  const chosen = document.documentElement.dataset.theme
  if (chosen === 'light' || chosen === 'dark') {
    return chosen
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function AndroidPage() {
  usePageTitle('Android app')
  // Only switches the screenshots; the site keeps its own theme.
  const [theme, setTheme] = useState(currentTheme)
  const [zoomed, setZoomed] = useState<(typeof SHOTS)[number] | null>(null)
  const [notesOpen, setNotesOpen] = useState(false)
  const gallery = useRef<HTMLUListElement>(null)
  // Whether the gallery is scrolled to either end, to turn off that end's arrow.
  const [ends, setEnds] = useState({ start: true, end: false })

  function updateEnds() {
    const list = gallery.current
    if (list) {
      setEnds({ start: list.scrollLeft <= 1, end: list.scrollLeft + list.clientWidth >= list.scrollWidth - 1 })
    }
  }

  useEffect(() => {
    updateEnds()
    window.addEventListener('resize', updateEnds)
    return () => window.removeEventListener('resize', updateEnds)
  }, [])

  function scrollGallery(direction: 1 | -1) {
    const list = gallery.current
    list?.scrollBy({ left: direction * list.clientWidth * 0.8, behavior: 'smooth' })
  }

  const src = (name: string) => `/android-screens/${name}-${theme}.webp`
  const alt = (caption: string) => `Carrel’s Android app: ${caption}`

  return (
    <article className="android">
      <header className="android-intro">
        <p className="kicker">Android app</p>
        <h1 className="android-title">Carrel on your phone</h1>
        <p className="android-lede">
          Everything the website does, plus a reader for your own books, offline changes, and links that open in the
          app.
        </p>
        <div className="android-download">
          <a className="primary-button" href={DOWNLOAD_URL}>
            Download for Android
          </a>
          <p className="mono muted">
            Android 8.0 or later ·{' '}
            <button type="button" className="link-button" onClick={() => setNotesOpen(true)}>
              Release notes
            </button>
          </p>
        </div>
      </header>

      <section className="android-section" aria-labelledby="android-screens">
        <div className="android-section-header">
          <h2 id="android-screens">A look around</h2>
          <div className="android-gallery-controls">
            <div className="option-switch" role="radiogroup" aria-label="Screenshot theme">
              {(['light', 'dark'] as const).map((value) => (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={theme === value}
                  onClick={() => setTheme(value)}
                >
                  {value === 'light' ? 'Light' : 'Dark'}
                </button>
              ))}
            </div>
            <div className="option-switch">
              <button type="button" aria-label="Previous screenshots" disabled={ends.start} onClick={() => scrollGallery(-1)}>
                ←
              </button>
              <button type="button" aria-label="Next screenshots" disabled={ends.end} onClick={() => scrollGallery(1)}>
                →
              </button>
            </div>
          </div>
        </div>
        <ul className="android-shots" ref={gallery} onScroll={updateEnds}>
          {SHOTS.map((shot) => (
            <li key={shot.name}>
              <figure>
                <button
                  type="button"
                  className="zoom-button"
                  aria-label={`View larger: ${shot.caption}`}
                  onClick={() => setZoomed(shot)}
                >
                  <img
                    src={src(shot.name)}
                    alt={alt(shot.caption)}
                    width={shot.width}
                    height={shot.height}
                    loading="lazy"
                  />
                </button>
                <figcaption className="mono muted">{shot.caption}</figcaption>
              </figure>
            </li>
          ))}
        </ul>
        {zoomed && (
          <ImageViewer src={src(zoomed.name)} alt={alt(zoomed.caption)} open onClose={() => setZoomed(null)} />
        )}
      </section>

      <section className="android-section" aria-labelledby="android-features">
        <h2 id="android-features">What it does</h2>
        <ul className="android-features">
          {FEATURES.map((feature) => (
            <li key={feature.title}>
              <h3>{feature.title}</h3>
              <p>{feature.text}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="android-section legal" aria-labelledby="android-install">
        <h2 id="android-install">Installing</h2>
        <p>Carrel isn’t in the Play Store, so you install it from the file. You only need to do this once.</p>
        <ol>
          <li>
            On your phone, tap <strong>Download for Android</strong>. Your browser may warn about the file type; choose
            to download it anyway.
          </li>
          <li>
            Open the downloaded file. If Android asks, allow your browser to install unknown apps, then go back.
          </li>
          <li>
            Tap <strong>Install</strong>. Google Play Protect may offer to scan the app first, as Carrel isn’t from the
            Play Store. Scanning it or installing without scanning both install Carrel.
          </li>
          <li>From then on, Carrel offers each new version itself.</li>
        </ol>
      </section>

      <ReleaseNotes open={notesOpen} onClose={() => setNotesOpen(false)} />
    </article>
  )
}

type Release = { tag_name: string; name: string; body: string | null; published_at: string; draft: boolean; prerelease: boolean }

/** The app's releases, newest first, with each one's changes: its notes' bullet points, without pull request numbers (as the app shows them). */
async function fetchReleases() {
  const response = await fetch(RELEASES_API)
  if (!response.ok) {
    throw new Error(`GitHub answered ${response.status}`)
  }
  const releases: Release[] = await response.json()
  return releases
    .filter((release) => release.tag_name.startsWith('android-v') && !release.draft && !release.prerelease)
    .map((release) => ({
      tag: release.tag_name,
      name: release.name,
      date: formatDate(release.published_at.slice(0, 10)),
      changes: (release.body ?? '')
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.startsWith('- ') || line.startsWith('* '))
        .map((line) => line.slice(2).replace(/\s*\(#\d+\)$/, '').trim())
        .filter(Boolean),
    }))
}

/** Every release's changes, fetched from GitHub when first opened. */
function ReleaseNotes({ open, onClose }: { open: boolean; onClose: () => void }) {
  const releases = useQuery({ queryKey: ['android-releases'], queryFn: fetchReleases, enabled: open })

  return (
    <Dialog open={open} onClose={onClose} className="release-notes">
      <DialogBackdrop className="image-viewer-backdrop" />
      <div className="image-viewer-frame">
        <DialogPanel className="release-notes-panel">
          <div className="release-notes-header">
            <DialogTitle className="release-notes-title">Release notes</DialogTitle>
            <button type="button" className="link-button" onClick={onClose}>
              Close
            </button>
          </div>
          {releases.isPending && <p className="muted">Loading…</p>}
          {releases.isError && <ErrorNotice error={releases.error} onRetry={() => releases.refetch()} />}
          {releases.data && (
            <ol className="release-notes-list">
              {releases.data.map((release) => (
                <li key={release.tag}>
                  <h3>{release.name}</h3>
                  <p className="mono muted">{release.date}</p>
                  {release.changes.length > 0 && (
                    <ul>
                      {release.changes.map((change) => (
                        <li key={change}>{change}</li>
                      ))}
                    </ul>
                  )}
                </li>
              ))}
            </ol>
          )}
          <a className="mono" href={RELEASES_URL}>
            Releases on GitHub
          </a>
        </DialogPanel>
      </div>
    </Dialog>
  )
}
