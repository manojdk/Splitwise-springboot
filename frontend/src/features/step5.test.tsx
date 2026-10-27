import { screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { addYears, formatDate, today } from '../lib/dates'
import { renderApp, signedIn } from '../test/renderApp'
import { db, errorBody, server } from '../test/server'

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

describe('resuming a recurring expense', () => {
  it('starts a stopped one again from its next date', async () => {
    // No body to read, so count the requests
    const sent: string[] = []
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'POST' && new URL(request.url).pathname.endsWith('/resume')) sent.push(request.url)
    })
    signedIn()
    const { user } = renderApp('/recurring')

    const card = (await screen.findByRole('heading', { name: 'Netflix' }, { timeout: 5000 })).closest('li')!
    expect(within(card).getByText('Stopped by Carol White')).toBeInTheDocument()
    expect(within(card).getByText('Carries on from the next date; the dates missed are not added.')).toBeInTheDocument()
    await user.click(within(card).getByRole('button', { name: 'Resume Netflix' }))

    expect(await screen.findByText(`"Netflix" resumed, next on ${formatDate('2026-11-05')}`)).toBeInTheDocument()
    expect(sent).toHaveLength(1)
    await waitFor(() => expect(within(card).getByRole('button', { name: 'Stop Netflix' })).toBeInTheDocument())
    expect(within(card).queryByRole('button', { name: 'Resume Netflix' })).not.toBeInTheDocument()
  })

  it('says why when it cannot resume', async () => {
    signedIn()
    server.use(
      http.post('*/api/recurring-expenses/:id/resume', () =>
        errorBody(409, 'Carol White is no longer a member of the group; add them back first, or set up a new recurring expense without them', '/api/recurring-expenses/301/resume'),
      ),
    )
    const { user } = renderApp('/recurring')

    const card = (await screen.findByRole('heading', { name: 'Netflix' }, { timeout: 5000 })).closest('li')!
    await user.click(within(card).getByRole('button', { name: 'Resume Netflix' }))

    expect(await within(card).findByRole('alert')).toHaveTextContent('Carol White is no longer a member of the group')
  })

  it('is not offered once the end date has passed', async () => {
    signedIn()
    const netflix = db.recurring.find((r) => r.id === 301)!
    netflix.endDate = '2026-06-30'
    renderApp('/recurring')

    const card = (await screen.findByRole('heading', { name: 'Netflix' }, { timeout: 5000 })).closest('li')!
    expect(within(card).queryByRole('button', { name: 'Resume Netflix' })).not.toBeInTheDocument()
  })
})
