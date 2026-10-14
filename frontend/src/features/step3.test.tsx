import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { renderApp, signedIn } from '../test/renderApp'
import { ALICE, CAROL, DAN, db, makeExpense, server } from '../test/server'

afterEach(() => server.events.removeAllListeners())

function captureBodies(method: string, path: RegExp) {
  const bodies: unknown[] = []
  server.events.on('request:start', async ({ request }) => {
    if (request.method === method && path.test(new URL(request.url).pathname)) bodies.push(await request.clone().json())
  })
  return bodies
}

/** A cab Dan paid outside any group, split with Alice, in dollars. */
function addCab() {
  db.expenses.push(
    makeExpense({ id: 80, groupId: null, description: 'Airport cab', amount: 60, paidBy: DAN, splitType: 'EQUAL', currency: 'USD', date: '2026-10-11', shares: [{ user: ALICE }, { user: DAN }] }),
  )
}

describe('all expenses', () => {
  it('lists expenses from every group and outside groups, and searches them', async () => {
    addCab()
    signedIn()
    const { user } = renderApp('/expenses')

    const cab = (await screen.findByText('Airport cab')).closest('button')!
    expect(cab).toHaveTextContent('Outside groups')
    expect(screen.getByText('Villa (3 nights)').closest('button')).toHaveTextContent('Goa Trip')

    await user.type(screen.getByRole('searchbox', { name: 'Search all expenses' }), 'villa')
    await waitFor(() => expect(screen.queryByText('Airport cab')).not.toBeInTheDocument())
    expect(screen.getByText('Villa (3 nights)')).toBeInTheDocument()
  })

  it('opens a group expense on its group page, and one outside groups right here', async () => {
    addCab()
    signedIn()
    const { user, router } = renderApp('/expenses')

    await user.click(await screen.findByText('Airport cab'))
    const dialog = await screen.findByRole('dialog', { name: 'Airport cab' })
    expect(router.state.location.pathname).toBe('/expenses')
    await user.click(within(dialog).getByRole('button', { name: 'Edit' }))
    const form = screen.getByRole('dialog', { name: 'Edit expense' })
    expect(within(form).getByLabelText('Currency')).toHaveValue('USD')
    await user.click(within(form).getByRole('button', { name: 'Close' }))

    await user.click(screen.getByText('Villa (3 nights)'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/groups/10'))
    expect(await screen.findByRole('dialog', { name: 'Villa (3 nights)' })).toBeInTheDocument()
  })

  it('adds an expense outside groups, with a contact, in another currency', async () => {
    const sent = captureBodies('POST', /^\/api\/expenses$/)
    signedIn()
    const { user } = renderApp('/expenses')

    await user.click(await screen.findByRole('button', { name: '+ Add expense' }))
    await user.click(await screen.findByRole('button', { name: /No group: split with friends/ }))
    const form = screen.getByRole('dialog', { name: 'Add expense · No group' })
    await user.selectOptions(within(form).getByLabelText('Currency'), 'USD')
    await user.type(within(form).getByLabelText('Description'), 'Concert')
    await user.type(within(form).getByLabelText('Amount (USD)'), '60')
    // Only the user is in it at first; Dan is a contact from earlier
    expect(within(form).getByRole('checkbox', { name: 'Include Dan Brown' })).not.toBeChecked()
    await user.click(within(form).getByRole('checkbox', { name: 'Include Dan Brown' }))
    expect(within(form).getByLabelText('Dan owes')).toHaveTextContent('$30.00')
    await user.click(within(form).getByRole('button', { name: 'Add expense' }))

    await waitFor(() => expect(sent).toHaveLength(1))
    expect(sent[0]).toMatchObject({ description: 'Concert', amount: 60, currency: 'USD', shares: [{ userId: ALICE.id }, { userId: DAN.id }] })
    expect(sent[0]).not.toHaveProperty('groupId')
  })
})

describe("a friend's page", () => {
  it('shows where you stand, per group, and the expenses you share', async () => {
    signedIn()
    renderApp(`/friends/${CAROL.id}`)

    expect(await screen.findByRole('heading', { level: 1, name: 'Carol White' })).toBeInTheDocument()
    const standing = screen.getByRole('region', { name: 'Where you stand' })
    // The euro total, and the one group it comes from
    expect(within(standing).getAllByText('+€45.00')).toHaveLength(2)
    expect(within(standing).getAllByText('Carol owes you')).toHaveLength(2) // EUR and INR
    expect(within(standing).getByRole('link', { name: 'Paris' })).toHaveAttribute('href', '/groups/11')
    const shared = screen.getByRole('region', { name: 'Expenses with Carol' })
    expect(await within(shared).findByText('Villa (3 nights)')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Settle up' })).toBeInTheDocument()
  })

  it('starts an expense outside groups with the friend already in it', async () => {
    signedIn()
    const { user } = renderApp(`/friends/${CAROL.id}`)

    await user.click(await screen.findByRole('button', { name: '+ Add expense' }))
    const form = await screen.findByRole('dialog', { name: 'Add expense · No group' })
    expect(within(form).getByRole('checkbox', { name: 'Include Carol White' })).toBeChecked()
    expect(within(form).getByRole('checkbox', { name: 'Include Alice Smith' })).toBeChecked()
  })

  it('says when you are settled up, and offers no settle up then', async () => {
    server.use(http.get('*/api/users/me/friends/:id', () => HttpResponse.json({ userId: CAROL.id, name: CAROL.name, balances: [], breakdown: [] })))
    signedIn()
    renderApp(`/friends/${CAROL.id}`)

    expect(await screen.findByText('You and Carol are settled up.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Settle up' })).not.toBeInTheDocument()
  })

  it('is reached from the dashboard', async () => {
    signedIn()
    const { user, router } = renderApp('/')

    await user.click(await screen.findByRole('link', { name: 'Carol White' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(`/friends/${CAROL.id}`))
  })
})
