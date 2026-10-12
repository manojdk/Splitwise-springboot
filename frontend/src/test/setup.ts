import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { resetDb, server } from './server'

/**
 * A request without a handler fails the test that made it. MSW's 'error' strategy alone only makes the fetch
 * reject, which the app turns into an error message on screen, so a test could still pass by accident.
 */
const unhandled: string[] = []

// findBy* waits up to 1 s by default; the first render of a test file can take longer on a busy machine
configure({ asyncUtilTimeout: 3000 })

beforeAll(() =>
  server.listen({
    onUnhandledFrame: async ({ frame, defaults }) => {
      unhandled.push(await frame.getUnhandledMessage())
      defaults.error()
    },
  }),
)

afterEach(() => {
  cleanup()
  server.resetHandlers()
  resetDb()
  sessionStorage.clear()
  localStorage.clear()
  delete document.documentElement.dataset.theme
  const found = unhandled.splice(0)
  if (found.length > 0) throw new Error(`Requests without a handler:\n${found.join('\n')}`)
})

afterAll(() => server.close())
