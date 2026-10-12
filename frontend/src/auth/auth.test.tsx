import { screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderApp, signedIn } from '../test/renderApp'
import { ALICE, ALICE_PASSWORD, errorBody, server } from '../test/server'

async function signIn(user: ReturnType<typeof renderApp>['user'], email: string, password: string) {
  await user.type(await screen.findByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('signing in', () => {
  it('sends a guest to the sign-in page', async () => {
    const { router } = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('signs in and opens the dashboard', async () => {
    const { user, router } = renderApp('/')

    await signIn(user, ALICE.email, ALICE_PASSWORD)

    expect(await screen.findByRole('heading', { name: 'Welcome, Alice' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(JSON.parse(sessionStorage.getItem('splitwise-session')!)).toMatchObject({ token: 'token-alice' })
  })

  it('returns to the page the user was trying to open', async () => {
    const { user, router } = renderApp('/activity')

    await signIn(user, ALICE.email, ALICE_PASSWORD)

    expect(await screen.findByRole('heading', { name: 'Activity' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/activity')
  })

  it('shows the server message for a wrong password and stays on the page', async () => {
    const { user, router } = renderApp('/login')

    await signIn(user, ALICE.email, 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(router.state.location.pathname).toBe('/login')
    expect(sessionStorage.getItem('splitwise-session')).toBeNull()
  })

  it('checks the form before calling the API', async () => {
    let calls = 0
    server.use(http.post('*/api/auth/login', () => void calls++))
    const { user } = renderApp('/login')

    await user.click(await screen.findByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Enter your email')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Email'), 'not-an-email')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(calls).toBe(0)
  })

  it('explains when the server cannot be reached', async () => {
    server.use(http.post('*/api/auth/login', () => Response.error()))
    const { user } = renderApp('/login')

    await signIn(user, ALICE.email, ALICE_PASSWORD)

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server')
  })
})

describe('an existing session', () => {
  it('survives a page reload', async () => {
    signedIn()
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Welcome, Alice' })).toBeInTheDocument()
    expect(screen.getByText('Alice Smith')).toBeInTheDocument()
  })

  it('is dropped once it has expired', async () => {
    signedIn('token-alice', Date.now() - 1000)
    const { router } = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('signs the user out when the server rejects the token', async () => {
    signedIn('revoked-token')
    const { router } = renderApp('/')

    expect(await screen.findByText('Your session has expired. Please sign in again.')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(sessionStorage.getItem('splitwise-session')).toBeNull()
  })

  it('offers a retry when the profile cannot be loaded, without signing out', async () => {
    let fail = true
    server.use(
      http.get('*/api/users/me', ({ request }) =>
        fail ? errorBody(503, 'Service unavailable', '/api/users/me') : Response.json({ ...ALICE, auth: request.url }),
      ),
    )
    signedIn()
    const { user } = renderApp('/')

    expect(await screen.findByRole('heading', { name: "Can't load your account" })).toBeInTheDocument()
    expect(sessionStorage.getItem('splitwise-session')).not.toBeNull()

    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Welcome, Alice' })).toBeInTheDocument()
  })

  it('ends on sign out', async () => {
    signedIn()
    const { user, router } = renderApp('/')

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(sessionStorage.getItem('splitwise-session')).toBeNull()
  })

  it('skips the sign-in page for a user who is already signed in', async () => {
    signedIn()
    const { router } = renderApp('/login')

    expect(await screen.findByRole('heading', { name: 'Welcome, Alice' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })
})
