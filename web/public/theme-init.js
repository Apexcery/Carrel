// Apply the saved theme and accent before first paint, so the page doesn't flash the wrong colours.
try {
  var theme = localStorage.getItem('carrel-theme')
  var accent = localStorage.getItem('carrel-accent')
  if (theme === 'light' || theme === 'dark') document.documentElement.setAttribute('data-theme', theme)
  if (accent) document.documentElement.setAttribute('data-accent', accent)
  // The eReader's own colours (see src/reader/look.ts), while a book is open in it.
  var readerTheme = localStorage.getItem('carrel-reader-theme')
  if (location.pathname.indexOf('/read/') === 0 && /[?&]book=/.test(location.search) && readerTheme && readerTheme !== 'site') {
    document.documentElement.setAttribute('data-reader-theme', readerTheme)
  }
} catch {}
