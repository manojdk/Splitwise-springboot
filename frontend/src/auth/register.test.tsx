import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/renderApp'
import { server } from '../test/server'

type User = ReturnType<typeof renderApp>['user']

async function fillIn(user: User, values: { name: string; email: string; password: string; confirm: string }) {
  await user.type(await screen.findByLabelText('Name'), values.name)
  await user.type(screen.getByLabelText('Email'), values.email)
  await user.type(screen.getByLabelText('Password'), values.password)
  await user.type(screen.getByLabelText('Confirm password'), values.confirm)
  await user.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('creating an account', () => {
  it('registers, signs in and opens the dashboard', async () => {
    let registered: unknown
    server.use(
      http.post('*/api/auth/register', async ({ request }) => {
        registered = await request.json()
        return HttpResponse.json({ id: 1, name: 'Alice Smith', email: 'alice@example.com' }, { status: 201 })
      }),
    )
    const { user } = renderApp('/register')

    await fillIn(user, { name: '  Alice Smith ', email: 'alice@example.com', password: 'password123', confirm: 'password123' })

    expect(await screen.findByRole('heading', { name: 'Welcome, Alice' })).toBeInTheDocument()
    // Trimmed, and the confirmation is not sent
    expect(registered).toEqual({ name: 'Alice Smith', email: 'alice@example.com', password: 'password123' })
  })

  it('points at the email field when the email is already registered', async () => {
    const { user, router } = renderApp('/register')

    await fillIn(user, { name: 'Tom', email: 'taken@example.com', password: 'password123', confirm: 'password123' })

    expect(await screen.findByText('This email is already registered. Sign in instead?')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(router.state.location.pathname).toBe('/register')
  })

  it('applies the same rules as the API before sending anything', async () => {
    let calls = 0
    server.use(http.post('*/api/auth/register', () => void calls++))
    const { user } = renderApp('/register')

    await fillIn(user, { name: ' ', email: 'tom@example.com', password: 'short', confirm: 'different' })

    expect(await screen.findByText('Enter your name')).toBeInTheDocument()
    expect(screen.getByText('Use at least 8 characters')).toBeInTheDocument()
    expect(screen.getByText('Passwords do not match')).toBeInTheDocument()
    expect(calls).toBe(0)
  })
})
