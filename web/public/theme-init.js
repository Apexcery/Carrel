// Apply the saved theme and accent before first paint, so the page doesn't flash the wrong colours.
try {
  var theme = localStorage.getItem('carrel-theme')
  var accent = localStorage.getItem('carrel-accent')
  if (theme === 'light' || theme === 'dark') document.documentElement.setAttribute('data-theme', theme)
  if (accent) document.documentElement.setAttribute('data-accent', accent)
} catch {}
