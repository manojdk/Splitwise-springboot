import { setupWorker } from 'msw/browser'
import { demoHandlers } from './handlers'

/**
 * Starts the in-browser API: a Service Worker answers the app's /api requests, so the demo needs no server.
 * Anything else (fonts, the app's own files) goes to the network as usual.
 */
export async function startDemo(): Promise<void> {
  await setupWorker(...demoHandlers()).start({
    serviceWorker: { url: `${import.meta.env.BASE_URL}mockServiceWorker.js` },
    onUnhandledFrame: 'bypass',
    quiet: true,
  })
}
