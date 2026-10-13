import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Identifies this build, so data the website saved in the browser is dropped when a new version is deployed.
  define: { __BUILD__: JSON.stringify(Date.now().toString(36)) },
  build: {
    rollupOptions: {
      // The eReader is its own page (/read/), with its own security policy.
      input: { main: 'index.html', read: 'read/index.html' },
    },
  },
})
