import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import type { Activity, Settlement } from '../api/types'
import { renderApp, signedIn } from '../test/renderApp'
import { ALICE, BOB, CAROL, DAN, db, makeExpense, server } from '../test/server'

afterEach(() => server.events.removeAllListeners())

/** Records the JSON bodies sent to a path, without replacing the fake API's behaviour. */
function captureBodies(method: string, path: RegExp) {
  const bodies: unknown[] = []
  server.events.on('request:start', async ({ request }) => {
    if (request.method === method && path.test(new URL(request.url).pathname)) bodies.push(await request.clone().json())
  })
  return bodies
}

function payment(fields: Partial<Settlement> & Pick<Settlement, 'id'>): Settlement {
  return {
    fromUserId: BOB.id,
    fromName: BOB.name,
    toUserId: ALICE.id,
    toName: ALICE.name,
    amount: 500,
    currency: 'INR',
    groupId: 10,
    date: '2026-10-01',
    note: null,
    version: 0,
    deleted: false,
    ...fields,
  }
}

describe('an expense, opened from its own address', () => {
  it('shows who did what to it, newest first', async () => {
    signedIn()
    renderApp('/groups/10?expense=50')

    const dialog = await screen.findByRole('dialog', { name: 'Villa (3 nights)' })
    const history = await within(dialog).findByRole('region', { name: 'History' })
    expect(within(history).getAllByRole('listitem')[0]).toHaveTextContent('Entry 1')
    expect(within(dialog).getByRole('button', { name: 'Edit' })).toBeInTheDocument()
  })

  it('says a deleted expense was deleted and restores it', async () => {
    db.expenses[1]!.deleted = true
    signedIn()
    const { user } = renderApp('/groups/10?expense=51')

    const dialog = await screen.findByRole('dialog', { name: 'Beach shack dinner' })
    expect(within(dialog).getByText(/This expense was deleted/)).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Restore' }))
    expect(await screen.findByText('"Beach shack dinner" restored')).toBeInTheDocument()
    expect(db.expenses[1]!.deleted).toBe(false)
    await waitFor(() => expect(within(dialog).queryByText(/This expense was deleted/)).not.toBeInTheDocument())
  })

  it('opens from its Activity entry, and closing it leaves the address clean', async () => {
    signedIn()
    const { user, router } = renderApp('/activity')

    await user.click(await screen.findByRole('link', { name: 'Entry 1' }))
    const dialog = await screen.findByRole('dialog', { name: 'Villa (3 nights)' })
    expect(router.state.location.pathname).toBe('/groups/10')

    await user.click(within(dialog).getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(router.state.location.search).toBe(''))
  })

  it("is shown but not offered for editing when the link points at another group's expense", async () => {
    db.expenses.push(makeExpense({ id: 70, groupId: 11, description: 'Louvre', amount: 40, paidBy: CAROL, splitType: 'EQUAL', currency: 'EUR', shares: [{ user: ALICE }, { user: CAROL }] }))
    signedIn()
    renderApp('/groups/10?expense=70')

    const dialog = await screen.findByRole('dialog', { name: 'Louvre' })
    expect(within(dialog).queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
  })
})

describe('recorded payments', () => {
  it('lists them in Settle up and deletes one, with Undo', async () => {
    db.settlements.push(payment({ id: 600, note: 'UPI' }))
    signedIn()
    const { user } = renderApp('/groups/10?tab=settle')

    const recorded = await screen.findByRole('region', { name: 'Recorded payments' })
    expect(within(recorded).getByText('Bob Jones paid you ₹500.00')).toBeInTheDocument()
    expect(within(recorded).getByText(/UPI/)).toBeInTheDocument()

    await user.click(within(recorded).getByRole('button', { name: 'Delete payment: Bob Jones paid you ₹500.00' }))
    expect(await screen.findByText('Payment deleted')).toBeInTheDocument()
    await waitFor(() => expect(within(recorded).getByText('No payments recorded yet.')).toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'Undo' }))
    expect(await within(recorded).findByText('Bob Jones paid you ₹500.00')).toBeInTheDocument()
    expect(db.settlements[0]!.deleted).toBe(false)
  })

  it('records a payment with a note, and will not send one without a date', async () => {
    const sent = captureBodies('POST', /^\/api\/settlements$/)
    signedIn()
    const { user } = renderApp('/groups/10?tab=settle')

    await user.click((await screen.findAllByRole('button', { name: 'Record payment' }))[0]!)
    const dialog = screen.getByRole('dialog', { name: 'Record a payment' })
    await user.clear(within(dialog).getByLabelText('Date'))
    await user.type(within(dialog).getByLabelText('Note (optional)'), 'Cash at the airport')
    await user.click(within(dialog).getByRole('button', { name: 'Record payment' }))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Pick the date of the payment')
    expect(sent).toHaveLength(0)

    await user.type(within(dialog).getByLabelText('Date'), '2026-10-10')
    await user.click(within(dialog).getByRole('button', { name: 'Record payment' }))
    await waitFor(() => expect(sent).toHaveLength(1))
    expect(sent[0]).toMatchObject({ date: '2026-10-10', note: 'Cash at the airport' })
  })

  it('offers Restore in Activity only for a payment whose latest event is its deletion', async () => {
    const entry = (id: number, type: Activity['type'], settlementId: number, summary: string): Activity => ({
      id, type, actorId: ALICE.id, actorName: ALICE.name, groupId: 10, expenseId: null, settlementId, summary, details: [],
      createdAt: new Date(Date.UTC(2026, 9, 12, 10, 0) - id * 60_000).toISOString(),
    })
    // Newest first: payment 601 was deleted and then restored; payment 602 was deleted, restored, deleted again
    const feed = [
      entry(1, 'SETTLEMENT_RESTORED', 601, 'Alice Smith restored a payment'),
      entry(2, 'SETTLEMENT_DELETED', 601, 'Alice Smith deleted a payment: first'),
      entry(3, 'SETTLEMENT_DELETED', 602, 'Alice Smith deleted a payment: second'),
      entry(4, 'SETTLEMENT_RESTORED', 602, 'Alice Smith restored a payment'),
      entry(5, 'SETTLEMENT_DELETED', 602, 'Alice Smith deleted a payment: second, earlier'),
    ]
    server.use(
      http.get('*/api/users/me/activity', () =>
        HttpResponse.json({ content: feed, page: 0, size: 20, totalElements: feed.length, totalPages: 1, last: true }),
      ),
    )
    db.settlements.push(payment({ id: 602, deleted: true }))
    signedIn()
    const { user } = renderApp('/activity')

    const restore = await screen.findAllByRole('button', { name: 'Restore payment' })
    expect(restore).toHaveLength(1)
    // Only on the newest entry of a payment, not on an older deletion of it
    expect(restore[0]!.closest('li')).toHaveTextContent('deleted a payment: second')
    expect(restore[0]!.closest('li')).not.toHaveTextContent('earlier')
    await user.click(restore[0]!)
    expect(await screen.findByText('Payment restored')).toBeInTheDocument()
    expect(db.settlements.find((p) => p.id === 602)!.deleted).toBe(false)
  })
})

describe('group settings', () => {
  it('renames the group', async () => {
    signedIn()
    const { user } = renderApp('/groups/10')
    await user.click(await screen.findByRole('button', { name: 'Settings' }))
    const dialog = screen.getByRole('dialog', { name: 'Group settings' })

    await user.clear(within(dialog).getByLabelText('Name'))
    await user.type(within(dialog).getByLabelText('Name'), 'Goa 2026')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Goa 2026' })).toBeInTheDocument()
    expect(screen.getByText('Renamed to Goa 2026')).toBeInTheDocument()
  })

  it('tells a member who did not create the group who can delete it', async () => {
    signedIn()
    const { user } = renderApp('/groups/11')
    await user.click(await screen.findByRole('button', { name: 'Settings' }))

    expect(screen.getByText('Only Carol White can delete this group.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete group' })).not.toBeInTheDocument()
  })

  it('explains why a group with payments cannot be deleted', async () => {
    db.expenses = db.expenses.filter((e) => e.groupId !== 10)
    db.settlements.push(payment({ id: 603 }))
    signedIn()
    const { user } = renderApp('/groups/10')
    await user.click(await screen.findByRole('button', { name: 'Settings' }))
    await user.click(screen.getByRole('button', { name: 'Delete group' }))
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Group has expenses or settlements and cannot be deleted')
    expect(db.groups.some((g) => g.id === 10)).toBe(true)
  })

  it('deletes an empty group and goes back to the list', async () => {
    db.expenses = db.expenses.filter((e) => e.groupId !== 10)
    signedIn()
    const { user, router } = renderApp('/groups/10')
    await user.click(await screen.findByRole('button', { name: 'Settings' }))
    await user.click(screen.getByRole('button', { name: 'Delete group' }))
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/groups'))
    expect(await screen.findByText('Goa Trip deleted')).toBeInTheDocument()
    expect(db.groups.some((g) => g.id === 10)).toBe(false)
  })
})

describe('smaller fixes', () => {
  it('steps back from a page that no longer has expenses', async () => {
    signedIn()
    const { router } = renderApp('/groups/10?page=3')

    expect(await screen.findByText('Villa (3 nights)')).toBeInTheDocument()
    expect(router.state.location.search).toBe('')
  })

  it('keeps a payer who has left selectable when editing their expense', async () => {
    db.expenses.push(makeExpense({ id: 71, groupId: 10, description: 'Taxi', amount: 300, paidBy: DAN, splitType: 'EQUAL', storeValues: false, shares: [{ user: ALICE }, { user: DAN }] }))
    signedIn()
    const { user } = renderApp('/groups/10?expense=71')
    await user.click(within(await screen.findByRole('dialog', { name: 'Taxi' })).getByRole('button', { name: 'Edit' }))

    const paidBy = within(screen.getByRole('dialog', { name: /Edit expense/ })).getByLabelText('Paid by')
    expect(paidBy).toHaveValue(String(DAN.id))
    expect(within(paidBy).getByRole('option', { name: 'Dan Brown (left the group)' })).toBeInTheDocument()
  })
})
