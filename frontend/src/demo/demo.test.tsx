import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { formatMoney } from '../lib/money'
import { renderApp } from '../test/renderApp'
import { server } from '../test/server'
import { DemoBackend } from './backend'
import { DemoBanner } from './DemoBanner'
import { demoHandlers, STORAGE_KEY } from './handlers'
import { DEMO_EMAIL, DEMO_PASSWORD, seedState } from './seed'

const NOW = new Date()

/** The whole app against the in-browser demo API instead of the test fake. */
function startDemo() {
  const backend = new DemoBackend(seedState(NOW))
  server.use(...demoHandlers(backend))
  return backend
}

describe('GitHub Pages demo', () => {
  it('signs in with the demo account and keeps every screen consistent after a change', async () => {
    const backend = startDemo()
    const alice = backend.caller(`Bearer ${backend.login(DEMO_EMAIL, DEMO_PASSWORD).accessToken}`)
    const netBefore = backend.friends(alice).totals.find((t) => t.currency === 'INR')!.net
    const { user } = renderApp('/login')

    await user.type(await screen.findByLabelText('Email'), DEMO_EMAIL)
    await user.type(screen.getByLabelText('Password'), DEMO_PASSWORD)
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    const net = await screen.findByRole('region', { name: 'Net balance' })
    expect(await within(net).findByText(formatMoney(netBefore, 'INR', { signed: true }))).toBeInTheDocument()

    // Alice pays 900 for the flat, split three ways: she is owed 600 more
    await user.click(within(screen.getByRole('region', { name: 'Your groups' })).getByText('Flat 4B'))
    await user.click(await screen.findByRole('button', { name: '+ Add expense' }))
    const form = screen.getByRole('dialog', { name: 'Add expense · Flat 4B' })
    await user.type(within(form).getByLabelText('Description'), 'Plumber')
    await user.type(within(form).getByLabelText('Amount (INR)'), '900')
    await user.click(within(form).getByRole('button', { name: 'Add expense' }))
    expect(await screen.findByText('Plumber')).toBeInTheDocument()

    await user.click(screen.getAllByRole('link', { name: /Dashboard/ })[0]!)
    const after = await screen.findByRole('region', { name: 'Net balance' })
    expect(await within(after).findByText(formatMoney(netBefore + 600, 'INR', { signed: true }))).toBeInTheDocument()

    await user.click(screen.getAllByRole('link', { name: /Activity/ })[0]!)
    expect(await screen.findByText('Alice Smith added "Plumber" (INR 900.00)')).toBeInTheDocument()
    // Saved in this browser, so a reload keeps it
    expect(localStorage.getItem(STORAGE_KEY)).toContain('"Plumber"')
  })

  it('registers a new visitor, who starts with no groups', async () => {
    startDemo()
    const { user } = renderApp('/register')

    await user.type(await screen.findByLabelText('Name'), 'Visitor')
    await user.type(screen.getByLabelText('Email'), 'visitor@example.com')
    await user.type(screen.getByLabelText('Password'), 'visitor-pass')
    await user.type(screen.getByLabelText('Confirm password'), 'visitor-pass')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText(/No groups yet/)).toBeInTheDocument()
  })

  it('says it is a demo, how to sign in, and resets to the sample data', async () => {
    localStorage.setItem(STORAGE_KEY, '{"version":1}')
    sessionStorage.setItem('splitwise-session', '{}')
    const reload = vi.fn()
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload })
    render(<DemoBanner />)

    const banner = screen.getByRole('note', { name: 'Demo' })
    expect(banner).toHaveTextContent(`Sign in as ${DEMO_EMAIL} / ${DEMO_PASSWORD}`)
    await userEvent.click(within(banner).getByRole('button', { name: 'Reset demo data' }))

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
    expect(sessionStorage.getItem('splitwise-session')).toBeNull()
    expect(reload).toHaveBeenCalled()
  })
})
