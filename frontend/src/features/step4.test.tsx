import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { renderApp, signedIn } from '../test/renderApp'
import { ALICE_PASSWORD, errorBody, server } from '../test/server'

afterEach(() => server.events.removeAllListeners())

function captureBodies(method: string, path: RegExp) {
  const bodies: unknown[] = []
  server.events.on('request:start', async ({ request }) => {
    if (request.method === method && path.test(new URL(request.url).pathname)) bodies.push(await request.clone().json())
  })
  return bodies
}

describe('settings', () => {
  it('is reached from the avatar in the header', async () => {
    signedIn()
    const { user, router } = renderApp('/')

    await user.click(await screen.findByRole('link', { name: 'Account settings' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Settings' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/settings')
  })

  it('changes the name, and the header shows the new one', async () => {
    const sent = captureBodies('PATCH', /^\/api\/users\/me$/)
    signedIn()
    const { user } = renderApp('/settings')

    const profile = await screen.findByRole('region', { name: 'Profile' })
    const name = within(profile).getByLabelText('Name')
    await waitFor(() => expect(name).toHaveValue('Alice Smith'))
    expect(within(profile).getByText('alice@example.com')).toBeInTheDocument()
    await user.clear(name)
    await user.type(name, 'Alice Cooper')
    await user.click(within(profile).getByRole('button', { name: 'Save name' }))

    expect(await screen.findByText('Name saved')).toBeInTheDocument()
    expect(sent).toEqual([{ name: 'Alice Cooper' }])
    expect(screen.getByRole('link', { name: 'Account settings' })).toHaveTextContent('Alice Cooper')
  })

  it('changes the password and carries on with the new token', async () => {
    const sent = captureBodies('POST', /^\/api\/users\/me\/password$/)
    signedIn('token-old')
    server.use(http.get('*/api/users/me', ({ request }) => {
      // The old token works until the change; the API then answers with token-alice
      const auth = request.headers.get('Authorization')
      return auth === 'Bearer token-old' || auth === 'Bearer token-alice'
        ? HttpResponse.json({ id: 1, name: 'Alice Smith', email: 'alice@example.com' })
        : errorBody(401, 'Missing or invalid access token', '/api/users/me')
    }))
    const { user } = renderApp('/settings')

    const section = await screen.findByRole('region', { name: 'Password' })
    await user.type(within(section).getByLabelText('Current password'), ALICE_PASSWORD)
    await user.type(within(section).getByLabelText('New password'), 'a-new-password')
    await user.type(within(section).getByLabelText('Confirm new password'), 'a-new-password')
    await user.click(within(section).getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Password changed. Your other devices were signed out.')).toBeInTheDocument()
    expect(sent).toEqual([{ currentPassword: ALICE_PASSWORD, newPassword: 'a-new-password' }])
    expect(JSON.parse(sessionStorage.getItem('splitwise-session')!)).toMatchObject({ token: 'token-alice' })
    expect(within(section).getByLabelText('Current password')).toHaveValue('')
  })

  it('says when the current password is wrong', async () => {
    signedIn()
    const { user } = renderApp('/settings')

    const section = await screen.findByRole('region', { name: 'Password' })
    await user.type(within(section).getByLabelText('Current password'), 'not-it')
    await user.type(within(section).getByLabelText('New password'), 'a-new-password')
    await user.type(within(section).getByLabelText('Confirm new password'), 'a-new-password')
    await user.click(within(section).getByRole('button', { name: 'Change password' }))

    expect(await within(section).findByText('That is not your current password')).toBeInTheDocument()
    expect(within(section).getByLabelText('Current password')).toHaveAttribute('aria-invalid', 'true')
  })

  it('checks the new password before sending it, counting bytes like the API', async () => {
    const sent = captureBodies('POST', /^\/api\/users\/me\/password$/)
    signedIn()
    const { user } = renderApp('/settings')

    const section = await screen.findByRole('region', { name: 'Password' })
    await user.type(within(section).getByLabelText('Current password'), ALICE_PASSWORD)
    // 30 rupee signs: 30 characters, 90 bytes
    await user.type(within(section).getByLabelText('New password'), '₹'.repeat(30))
    await user.type(within(section).getByLabelText('Confirm new password'), 'something-else')
    await user.click(within(section).getByRole('button', { name: 'Change password' }))

    expect(
      await within(section).findByText('Too long: use at most 72 bytes (letters with accents and symbols count as 2 to 4)'),
    ).toBeInTheDocument()
    expect(within(section).getByText('Passwords do not match')).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('signs out of all devices after a confirmation, and says so on the sign-in page', async () => {
    signedIn()
    const { user, router } = renderApp('/settings')

    const devices = await screen.findByRole('region', { name: 'Devices' })
    await user.click(within(devices).getByRole('button', { name: 'Sign out of all devices' }))
    await user.click(within(devices).getByRole('button', { name: 'Yes, sign out everywhere' }))

    expect(await screen.findByText('You signed out of all devices.')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(sessionStorage.getItem('splitwise-session')).toBeNull()
  })

  it('tells a device that was signed out elsewhere why it has to sign in again', async () => {
    signedIn()
    server.use(
      http.get('*/api/users/me/friends', () =>
        errorBody(401, 'You were signed out of this device; please sign in again', '/api/users/me/friends'),
      ),
    )
    renderApp('/')

    expect(await screen.findByText(/You were signed out of this device\. This happens when the password is changed/)).toBeInTheDocument()
  })
})

describe('signing up', () => {
  it('counts the password limit in bytes, like the API', async () => {
    const { user } = renderApp('/register')

    await user.type(await screen.findByLabelText('Password'), 'é'.repeat(40))
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(
      await screen.findByText('Too long: use at most 72 bytes (letters with accents and symbols count as 2 to 4)'),
    ).toBeInTheDocument()
  })
})
