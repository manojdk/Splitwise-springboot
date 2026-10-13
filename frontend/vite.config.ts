import { copyFileSync } from 'node:fs'
import { resolve } from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { msw } from 'msw/vite'
import type { Plugin } from 'vite'
import { defineConfig } from 'vitest/config'

/**
 * GitHub Pages has no fallback for app routes: a link to /<repo>/groups/12 gets the site's 404.html. Making that page a
 * copy of index.html loads the app there too, and its router shows the right page.
 */
function pagesFallback(): Plugin {
  let outDir = ''
  return {
    name: 'pages-fallback',
    apply: 'build',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir)
    },
    closeBundle() {
      copyFileSync(resolve(outDir, 'index.html'), resolve(outDir, '404.html'))
    },
  }
}

// `--mode demo` builds the GitHub Pages demo: the API runs in the browser (src/demo), under the repository's path
export default defineConfig(({ mode }) => {
  const demo = mode === 'demo'
  return {
    base: demo ? '/Splitwise-springboot/' : '/',
    plugins: [react(), tailwindcss(), ...(demo ? [msw({ mode: 'worker-only' }), pagesFallback()] : [])],
    build: {
      outDir: demo ? 'dist-demo' : 'dist',
      rolldownOptions: {
        output: {
          // Libraries change far less often than our code: separate chunks stay cached across releases
          codeSplitting: {
            groups: [
              { name: 'react', test: /node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/ },
              { name: 'query', test: /node_modules[\\/]@tanstack[\\/]/ },
              { name: 'forms', test: /node_modules[\\/](zod|react-hook-form|@hookform)[\\/]/ },
            ],
          },
        },
      },
    },
    server: {
      // The Spring Boot API runs on :8080; proxying keeps the browser on one origin, so no CORS setup is needed.
      proxy: { '/api': 'http://localhost:8080' },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      restoreMocks: true,
    },
  }
})
