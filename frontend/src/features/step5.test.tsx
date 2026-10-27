import { screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { addYears, today } from '../lib/dates'
import { renderApp, signedIn } from '../test/renderApp'
import { server } from '../test/server'

afterEach(() => server.events.removeAllListeners())

function captureBodies(method: string, path: RegExp) {
  const bodies: unknown[] = []
  server.events.on('request:start', async ({ request }) => {
    if (request.method === method && path.test(new URL(request.url).pathname)) bodies.push(await request.clone().json())
  })
  return bodies
}

describe('date limits, as the API has them', () => {
  it('catches a mistyped year on an expense before sending it', async () => {
    const sent = captureBodies('POST', /^\/api\/expenses$/)
    signedIn()
    const { user } = renderApp('/groups/10')
    // The first test loads the group page's code; give it longer than the usual second
    await user.click(await screen.findByRole('button', { name: '+ Add expense' }, { timeout: 5000 }))
    const form = screen.getByRole('dialog', { name: 'Add expense · Goa Trip' })

    await user.type(within(form).getByLabelText('Description'), 'Dinner')
    await user.type(within(form).getByLabelText('Amount (INR)'), '300')
    const date = within(form).getByLabelText('Date')
    expect(date).toHaveAttribute('min', '2000-01-01')
    expect(date).toHaveAttribute('max', addYears(today(), 1))
    await user.clear(date)
    await user.type(date, '2099-10-10')
    await user.click(within(form).getByRole('button', { name: 'Add expense' }))

    expect(within(form).getByText('Use a date at most a year from today')).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('catches a recurring expense that ends more than 50 years after it starts', async () => {
    const sent = captureBodies('POST', /^\/api\/recurring-expenses$/)
    signedIn()
    const { user } = renderApp('/recurring')
    await user.click(await screen.findByRole('button', { name: '+ New recurring expense' }))
    await user.click(screen.getByRole('button', { name: /Goa Trip/ }))
    const form = screen.getByRole('dialog', { name: /New recurring expense/ })

    await user.type(within(form).getByLabelText('Description'), 'Rent')
    await user.type(within(form).getByLabelText('Amount (INR)'), '15000')
    await user.type(within(form).getByLabelText('Ends on (optional)'), addYears(today(), 51))
    await user.click(within(form).getByRole('button', { name: 'Set up' }))

    expect(within(form).getByText('The end can be at most 50 years after the start')).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })

  it('catches a payment dated before 2000', async () => {
    const sent = captureBodies('POST', /^\/api\/settlements$/)
    signedIn()
    const { user } = renderApp('/groups/10?tab=settle')

    await user.click((await screen.findAllByRole('button', { name: 'Record payment' }))[0]!)
    const dialog = screen.getByRole('dialog', { name: 'Record a payment' })
    await user.clear(within(dialog).getByLabelText('Date'))
    await user.type(within(dialog).getByLabelText('Date'), '1999-12-31')
    await user.click(within(dialog).getByRole('button', { name: 'Record payment' }))

    await waitFor(() => expect(within(dialog).getByRole('alert')).toHaveTextContent('Use a date on or after 1 Jan 2000'))
    expect(sent).toHaveLength(0)
  })
})
