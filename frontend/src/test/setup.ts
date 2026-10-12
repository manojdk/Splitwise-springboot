import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

// Any request without a handler fails the test, so no test can silently depend on a real network
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  sessionStorage.clear()
  localStorage.clear()
  delete document.documentElement.dataset.theme
})
afterAll(() => server.close())
