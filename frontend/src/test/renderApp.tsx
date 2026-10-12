import { QueryClient } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { Providers } from '../app/Providers'
import { routes } from '../app/routes'
import { VALID_TOKEN } from './server'

/** Renders the whole app (real routes, providers and API client) at the given URL, against the MSW fake API. */
export function renderApp(path = '/') {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const user = userEvent.setup()
  const view = render(
    <Providers queryClient={queryClient}>
      <RouterProvider router={router} />
    </Providers>,
  )
  return { ...view, router, user }
}

/** Puts a signed-in session in storage, as if the user had signed in before reloading the page. */
export function signedIn(token = VALID_TOKEN, expiresAt = Date.now() + 3_600_000) {
  sessionStorage.setItem('splitwise-session', JSON.stringify({ token, expiresAt }))
}
