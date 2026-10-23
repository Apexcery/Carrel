import type { CSSProperties } from 'react'
import type { IInjectablesConfig } from '@readium/navigator'
import literata from '@fontsource-variable/literata/opsz.css?inline'
import literataItalic from '@fontsource-variable/literata/opsz-italic.css?inline'
import atkinson from '@fontsource/atkinson-hyperlegible/400.css?inline'
import atkinsonItalic from '@fontsource/atkinson-hyperlegible/400-italic.css?inline'
import atkinsonBold from '@fontsource/atkinson-hyperlegible/700.css?inline'
import atkinsonBoldItalic from '@fontsource/atkinson-hyperlegible/700-italic.css?inline'
import openDyslexic from '@fontsource/opendyslexic/400.css?inline'
import openDyslexicItalic from '@fontsource/opendyslexic/400-italic.css?inline'
import openDyslexicBold from '@fontsource/opendyslexic/700.css?inline'
import openDyslexicBoldItalic from '@fontsource/opendyslexic/700-italic.css?inline'

/** How the eReader shows a book, kept in this browser. */
export interface Look {
  /** 1 is the book's own size. */
  fontSize: number
  pages: Pages
  theme: ReaderTheme
  font: ReaderFont
  spacing: Step
  marginTop: Step
  marginBottom: Step
  marginLeft: Step
  marginRight: Step
}

export type Margin = 'marginTop' | 'marginBottom' | 'marginLeft' | 'marginRight'

/** Pages side by side: as the window's width suits (Readium's choice), or always one or two. */
export type Pages = 'auto' | 'one' | 'two'

/** The page's colours: Carrel's own paper and ink (light or dark, as the site is), or one of the reader's own. */
export type ReaderTheme = 'site' | 'sepia' | 'night' | 'black'

export type ReaderFont = 'book' | 'literata' | 'serif' | 'sans' | 'atkinson' | 'opendyslexic'

/** Line spacing or a margin: less than usual, usual (for line spacing, the book's own), or more. */
export type Step = 'less' | 'normal' | 'more'

export const FONT_STEP = 0.1
export const MIN_FONT_SIZE = 0.7
export const MAX_FONT_SIZE = 2

export const PAGES_LABELS: Record<Pages, string> = { auto: 'Auto', one: 'One page', two: 'Two pages' }
export const THEME_LABELS: Record<ReaderTheme, string> = { site: 'Carrel', sepia: 'Sepia', night: 'Night', black: 'Black' }
export const SPACING_LABELS: Record<Step, string> = { less: 'Tight', normal: 'Normal', more: 'Loose' }
export const MARGIN_LABELS: Record<Step, string> = { less: 'Narrow', normal: 'Normal', more: 'Wide' }
export const MARGINS: Record<Margin, string> = { marginTop: 'Top', marginBottom: 'Bottom', marginLeft: 'Left', marginRight: 'Right' }

/** Each font's name, and the CSS font-family for it (null keeps the book's own). */
export const FONTS: Record<ReaderFont, { label: string; family: string | null }> = {
  book: { label: 'Book’s own', family: null },
  literata: { label: 'Literata', family: "'Literata Variable', serif" },
  serif: { label: 'Serif', family: 'serif' },
  sans: { label: 'Sans', family: 'sans-serif' },
  atkinson: { label: 'Atkinson Hyperlegible', family: "'Atkinson Hyperlegible', sans-serif" },
  opendyslexic: { label: 'OpenDyslexic', family: 'OpenDyslexic, sans-serif' },
}

const LINE_HEIGHTS: Record<Step, number | null> = { less: 1.3, normal: null, more: 1.8 }

/**
 * The margins (px): space above and below Readium's box, and insets of its frames from either side of it. Readium's
 * own margin either side of each page stays small, so Narrow can be narrower than its usual 20px; Normal makes up the
 * difference.
 */
const PAGE_GUTTER = 12
const ABOVE_BELOW: Record<Step, number> = { less: 8, normal: 24, more: 56 }
const BESIDE: Record<Step, number> = { less: 0, normal: 8, more: 40 }

const KEYS = {
  fontSize: 'carrel-reader-font-size',
  pages: 'carrel-reader-pages',
  theme: 'carrel-reader-theme',
  font: 'carrel-reader-font',
  spacing: 'carrel-reader-spacing',
  marginTop: 'carrel-reader-margin-top',
  marginBottom: 'carrel-reader-margin-bottom',
  marginLeft: 'carrel-reader-margin-left',
  marginRight: 'carrel-reader-margin-right',
}

export function storedLook(): Look {
  const size = Number(read(KEYS.fontSize))
  return {
    fontSize: size >= MIN_FONT_SIZE && size <= MAX_FONT_SIZE ? size : 1,
    pages: oneOf(read(KEYS.pages), PAGES_LABELS, 'auto'),
    theme: oneOf(read(KEYS.theme), THEME_LABELS, 'site'),
    font: oneOf(read(KEYS.font), FONTS, 'book'),
    spacing: oneOf(read(KEYS.spacing), SPACING_LABELS, 'normal'),
    marginTop: oneOf(read(KEYS.marginTop), MARGIN_LABELS, 'normal'),
    marginBottom: oneOf(read(KEYS.marginBottom), MARGIN_LABELS, 'normal'),
    marginLeft: oneOf(read(KEYS.marginLeft), MARGIN_LABELS, 'normal'),
    marginRight: oneOf(read(KEYS.marginRight), MARGIN_LABELS, 'normal'),
  }
}

export function storeLook(look: Look) {
  try {
    for (const [name, key] of Object.entries(KEYS)) localStorage.setItem(key, String(look[name as keyof Look]))
  } catch {
    // Kept for this visit only.
  }
}

/**
 * Colours the whole reader (its bars and dialogs too) in the theme; Carrel's own leaves the site's light or dark. The
 * themes' colours are in styles.css, under [data-reader-theme].
 */
export function applyTheme(theme: ReaderTheme) {
  if (theme === 'site') document.documentElement.removeAttribute('data-reader-theme')
  else document.documentElement.setAttribute('data-reader-theme', theme)
}

/** Readium's preferences for the look, in the theme's paper and ink (so applyTheme first). */
export function readerPreferences(look: Look) {
  const style = getComputedStyle(document.documentElement)
  return {
    backgroundColor: style.getPropertyValue('--paper').trim(),
    textColor: style.getPropertyValue('--ink').trim(),
    fontSize: look.fontSize,
    columnCount: look.pages === 'one' ? 1 : look.pages === 'two' ? 2 : null,
    fontFamily: FONTS[look.font].family,
    lineHeight: LINE_HEIGHTS[look.spacing],
    pageGutter: PAGE_GUTTER,
  }
}

/** The top and bottom margins, as space above and below Readium's box. */
export function bookStyle(look: Look): CSSProperties {
  return { paddingTop: ABOVE_BELOW[look.marginTop], paddingBottom: ABOVE_BELOW[look.marginBottom] }
}

/**
 * The left and right margins, which inset Readium's frames from the box it sizes for them (see .readium-navigator-iframe
 * in styles.css): the page gets narrower, and each side moves on its own.
 */
export function framesStyle(look: Look) {
  return {
    '--reader-margin-left': `${BESIDE[look.marginLeft]}px`,
    '--reader-margin-right': `${BESIDE[look.marginRight]}px`,
  } as CSSProperties
}

/**
 * Loads the look's font into the page, where Readium measures it to decide how wide a line is (and so how many pages
 * fit side by side): measured before it arrives, it's measured in the fallback font.
 */
export async function loadFont(look: Look) {
  const family = FONTS[look.font].family
  if (family) await document.fonts.load(`1em ${family}`)
}

/**
 * The reader's fonts, for every chapter's frame. The frames are blob: documents, where Vite's root-relative font URLs
 * don't resolve, so they're made absolute; Readium's policy for the frames lets them load from the site itself.
 */
export function fontInjectables(): IInjectablesConfig {
  const css = [
    literata,
    literataItalic,
    atkinson,
    atkinsonItalic,
    atkinsonBold,
    atkinsonBoldItalic,
    openDyslexic,
    openDyslexicItalic,
    openDyslexicBold,
    openDyslexicBoldItalic,
  ]
    .join('\n')
    .replace(/url\((['"]?)\//g, `url($1${window.location.origin}/`)
  return {
    rules: [{ resources: [/.*/], append: [{ as: 'link', rel: 'stylesheet', blob: new Blob([css], { type: 'text/css' }) }] }],
    allowedDomains: [window.location.origin],
  }
}

function oneOf<T extends string>(value: string | null, options: Record<T, unknown>, fallback: T): T {
  return value !== null && Object.hasOwn(options, value) ? (value as T) : fallback
}

function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}
