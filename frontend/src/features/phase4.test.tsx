import { screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CreateRecurringExpenseRequest } from '../api/types'
import { today } from '../lib/dates'
import { renderApp, signedIn } from '../test/renderApp'
import { ALICE, BOB, CAROL, db, errorBody, server } from '../test/server'

afterEach(() => server.events.removeAllListeners())

function captureBodies(method: string, path: RegExp) {
  const bodies: unknown[] = []
  server.events.on('request:start', async ({ request }) => {
    if (request.method === method && path.test(new URL(request.url).pathname)) bodies.push(await request.clone().json())
  })
  return bodies
}

describe('activity', () => {
  it('lists what happened, with what changed in an edit, twenty at a time', async () => {
    signedIn()
    const { user } = renderApp('/activity')

    const edit = (await screen.findByText('Bob Jones updated "Beach shack dinner"')).closest('li')!
    expect(within(edit).getByText('Amount: 1,000.00 -> 1,200.00')).toBeInTheDocument()
    expect(within(edit).getByText('Carol White added to the split')).toBeInTheDocument()
    expect(within(edit).getByText(/Goa Trip/)).toBeInTheDocument()
    expect(screen.queryByText('Entry 20')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Show more' }))
    expect(await screen.findByText('Entry 24')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument()
  })

  it("shows only a group's own activity on its page", async () => {
    signedIn()
    renderApp('/groups/11?tab=activity')

    expect(await screen.findByText('Entry 4')).toBeInTheDocument()
    expect(screen.queryByText('Entry 1')).not.toBeInTheDocument()
  })
})

describe('recurring expenses', () => {
  it('lists running ones first with the next date, and stopped ones with why', async () => {
    signedIn()
    renderApp('/recurring')

    const rent = (await screen.findByRole('heading', { name: 'Rent' })).closest('li')!
    expect(rent).toHaveTextContent('Monthly')
    expect(rent).toHaveTextContent('₹24,000.00')
    expect(rent).toHaveTextContent(/Goa Trip · next on 31 Oct 2026/)
    const netflix = screen.getByRole('heading', { name: 'Netflix' }).closest('li')!
    expect(netflix).toHaveTextContent('Stopped')
    expect(netflix).toHaveTextContent('Stopped by Carol White')
    expect(within(netflix).queryByRole('button')).not.toBeInTheDocument()
    const cards = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(cards.indexOf('Rent')).toBeLessThan(cards.indexOf('Netflix'))
  })

  it('stops one after confirming', async () => {
    signedIn()
    const { user } = renderApp('/recurring')

    await user.click(await screen.findByRole('button', { name: 'Stop Rent' }))
    const dialog = screen.getByRole('dialog', { name: 'Stop "Rent"?' })
    expect(within(dialog).getByText(/The ones already added stay/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Stop' }))

    expect(await screen.findByText('"Rent" stopped')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Rent' }).closest('li')).toHaveTextContent('Stopped by Alice Smith'))
  })

  it('sets one up in a group with the same live split, how often, and from when', async () => {
    const sent = captureBodies('POST', /^\/api\/recurring-expenses$/)
    signedIn()
    const { user } = renderApp('/recurring')

    await user.click(await screen.findByRole('button', { name: '+ New recurring expense' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Which group?' })).getByRole('button', { name: /Goa Trip/ }))
    const form = screen.getByRole('dialog', { name: 'New recurring expense · Goa Trip' })
    expect(within(form).getByLabelText('Starts on')).toHaveValue(today())

    await user.type(within(form).getByLabelText('Description'), 'Wifi')
    await user.type(within(form).getByLabelText('Amount (INR)'), '999')
    expect(within(form).getByLabelText('Alice owes')).toHaveTextContent('₹333.00')
    await user.selectOptions(within(form).getByLabelText('Repeats'), 'YEARLY')
    await user.type(within(form).getByLabelText('Ends on (optional)'), '2030-12-31')
    await user.click(within(form).getByRole('button', { name: 'Set up' }))

    expect(await screen.findByText('"Wifi" will repeat every year')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Wifi' })).toBeInTheDocument()
    expect(sent[0]).toMatchObject({
      description: 'Wifi',
      amount: 999,
      groupId: 10,
      frequency: 'YEARLY',
      startDate: today(),
      endDate: '2030-12-31',
      splitType: 'EQUAL',
      shares: [{ userId: ALICE.id }, { userId: BOB.id }, { userId: CAROL.id }],
    } satisfies Partial<CreateRecurringExpenseRequest>)
    expect(sent[0]).not.toHaveProperty('date')
  })

  it('refuses a start in the past or an end before the start, before sending', async () => {
    const sent = captureBodies('POST', /^\/api\/recurring-expenses$/)
    signedIn()
    const { user } = renderApp('/recurring')
    await user.click(await screen.findByRole('button', { name: '+ New recurring expense' }))
    await user.click(screen.getByRole('button', { name: /Goa Trip/ }))
    const form = screen.getByRole('dialog', { name: /New recurring expense/ })

    await user.type(within(form).getByLabelText('Description'), 'Gym')
    await user.type(within(form).getByLabelText('Amount (INR)'), '1500')
    await user.clear(within(form).getByLabelText('Starts on'))
    await user.type(within(form).getByLabelText('Starts on'), '2020-01-01')
    await user.type(within(form).getByLabelText('Ends on (optional)'), '2019-01-01')
    await user.click(within(form).getByRole('button', { name: 'Set up' }))

    expect(within(form).getByText('The first one cannot be in the past')).toBeInTheDocument()
    expect(within(form).getByText('The end must be on or after the start')).toBeInTheDocument()
    expect(sent).toHaveLength(0)
  })
})

describe('group report', () => {
  it('shows spending by category and by member', async () => {
    signedIn()
    renderApp('/groups/10?tab=report')

    expect(await screen.findByRole('img', { name: 'Total spent ₹10,200.00 across 2 expenses' })).toBeInTheDocument()
    const categories = within(screen.getByRole('region', { name: 'By category' })).getAllByRole('listitem')
    expect(categories[0]).toHaveTextContent('Rent (1 expense, 88%)₹9,000.00')
    expect(categories[1]).toHaveTextContent('Food & drink (1 expense, 12%)₹1,200.00')

    const rows = within(screen.getByRole('table')).getAllByRole('row')
    expect(rows[1]).toHaveTextContent('Alice Smith₹9,000.00₹4,900.00+₹4,100.00')
    expect(rows[2]).toHaveTextContent('Bob Jones₹1,200.00₹2,650.00-₹1,450.00')
  })

  it('narrows to a date range', async () => {
    signedIn()
    const { user } = renderApp('/groups/10?tab=report')
    await screen.findByRole('img', { name: /across 2 expenses/ })

    await user.type(screen.getByLabelText('From'), '2026-09-11')
    expect(await screen.findByRole('img', { name: 'Total spent ₹1,200.00 across 1 expense' })).toBeInTheDocument()

    await user.type(screen.getByLabelText('To'), '2026-09-11')
    expect(await screen.findByText('No expenses in these dates.')).toBeInTheDocument()
  })

  it('exports the expenses as a CSV file', async () => {
    const created: Blob[] = []
    const createObjectURL = vi.fn((blob: Blob) => {
      created.push(blob)
      return 'blob:csv'
    })
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }))
    const clicked: string[] = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push(this.download)
    })
    signedIn()
    const { user } = renderApp('/groups/10?tab=report')

    await user.click(await screen.findByRole('button', { name: 'Export CSV' }))

    await waitFor(() => expect(clicked).toEqual(['group-10-expenses.csv']))
    expect(await created[0]!.text()).toContain('2026-09-10,Villa,9000.00')
    vi.unstubAllGlobals()
  })

  it('says why an export failed', async () => {
    server.use(http.get('*/api/groups/10/expenses.csv', () => errorBody(500, 'Export failed', '/x')))
    signedIn()
    const { user } = renderApp('/groups/10?tab=report')

    await user.click(await screen.findByRole('button', { name: 'Export CSV' }))
    expect(await screen.findByText('Export failed')).toBeInTheDocument()
    expect(db.expenses).toHaveLength(2)
  })
})
