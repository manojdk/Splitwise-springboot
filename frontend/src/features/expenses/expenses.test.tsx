import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { CreateExpenseRequest, UpdateExpenseRequest } from '../../api/types'
import { renderApp, signedIn } from '../../test/renderApp'
import { ALICE, BOB, CAROL, db, makeExpense, server } from '../../test/server'

type User = ReturnType<typeof renderApp>['user']

/** Records the JSON bodies sent to a path, without replacing the fake API's behaviour. */
function captureBodies(method: string, path: RegExp) {
  const bodies: unknown[] = []
  server.events.on('request:start', async ({ request }) => {
    if (request.method === method && path.test(new URL(request.url).pathname)) bodies.push(await request.clone().json())
  })
  return bodies
}

async function openGoa(path = '/groups/10') {
  signedIn()
  const app = renderApp(path)
  await screen.findByRole('heading', { level: 1, name: 'Goa Trip' })
  return app
}

function owes(name: string) {
  return screen.getByLabelText(`${name} owes`)
}

async function openAddForm(user: User) {
  await user.click(screen.getByRole('button', { name: '+ Add expense' }))
  return screen.getByRole('dialog', { name: 'Add expense · Goa Trip' })
}

describe('the expense list', () => {
  it('shows each expense with what it means for you', async () => {
    await openGoa()

    const villa = (await screen.findByText('Villa (3 nights)')).closest('button')!
    expect(villa).toHaveTextContent('You paid ₹9,000.00 · By shares')
    expect(villa).toHaveTextContent('₹4,500.00you lent')
    const dinner = screen.getByText('Beach shack dinner').closest('button')!
    expect(dinner).toHaveTextContent('Bob paid ₹1,200.00 · Equally')
    expect(dinner).toHaveTextContent('₹400.00you owe')
    // Newest first
    const rows = screen.getAllByRole('listitem').map((li) => li.textContent)
    expect(rows.findIndex((t) => t?.includes('dinner'))).toBeLessThan(rows.findIndex((t) => t?.includes('Villa')))
  })

  it('filters by text, category and payer, keeping the filters in the address', async () => {
    const { user, router } = await openGoa()
    await screen.findByText('Villa (3 nights)')

    await user.type(screen.getByRole('searchbox', { name: 'Search expenses' }), 'villa')
    await waitFor(() => expect(screen.queryByText('Beach shack dinner')).not.toBeInTheDocument())
    expect(router.state.location.search).toBe('?q=villa')

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(await screen.findByText('Beach shack dinner')).toBeInTheDocument()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Category' }), 'FOOD_AND_DRINK')
    await waitFor(() => expect(screen.queryByText('Villa (3 nights)')).not.toBeInTheDocument())
    await user.selectOptions(screen.getByRole('combobox', { name: 'Paid by' }), String(ALICE.id))
    expect(await screen.findByText('No expenses match these filters.')).toBeInTheDocument()
    expect(router.state.location.search).toBe(`?category=FOOD_AND_DRINK&paidBy=${ALICE.id}`)
  })

  it('pages through long lists', async () => {
    for (let i = 0; i < 23; i++) {
      db.expenses.push(
        makeExpense({ id: 200 + i, groupId: 10, description: `Snack ${i}`, amount: 30, paidBy: ALICE, splitType: 'EQUAL', shares: [{ user: ALICE }, { user: BOB }], date: '2026-08-01' }),
      )
    }
    const { user } = await openGoa()

    expect(await screen.findByText('Page 1 of 2 · 25 expenses')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByText('Page 2 of 2 · 25 expenses')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    expect(screen.queryByText('Beach shack dinner')).not.toBeInTheDocument()
  })
})

describe('adding an expense', () => {
  it('shows every share live and saves an equal split', async () => {
    const sent = captureBodies('POST', /^\/api\/expenses$/)
    const { user } = await openGoa()
    const form = await openAddForm(user)
    expect(within(form).getByText("Enter the amount to see each person's share.")).toBeInTheDocument()

    await user.type(within(form).getByLabelText('Description'), 'Ferry tickets')
    await user.type(within(form).getByLabelText('Amount (INR)'), '100')
    // 100.00 / 3: the first person gets the extra paisa
    expect(owes('Alice')).toHaveTextContent('₹33.34')
    expect(owes('Bob')).toHaveTextContent('₹33.33')
    expect(owes('Carol')).toHaveTextContent('₹33.33')
    expect(within(form).getByText('Adds up to ₹100.00 exactly')).toBeInTheDocument()

    await user.click(within(form).getByRole('checkbox', { name: 'Include Carol White' }))
    expect(owes('Alice')).toHaveTextContent('₹50.00')
    expect(owes('Carol')).toHaveTextContent('–')

    await user.click(within(form).getByRole('button', { name: 'Add expense' }))

    expect(await screen.findByText('"Ferry tickets" added')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(await screen.findByText('Ferry tickets')).toBeInTheDocument()
    expect(sent[0]).toMatchObject({
      description: 'Ferry tickets',
      amount: 100,
      paidById: ALICE.id,
      groupId: 10,
      splitType: 'EQUAL',
      category: 'GENERAL',
      shares: [{ userId: ALICE.id }, { userId: BOB.id }],
    } satisfies Partial<CreateExpenseRequest>)
    server.events.removeAllListeners()
  })

  it('splits by shares and by percent, and explains a split that does not add up', async () => {
    const sent = captureBodies('POST', /^\/api\/expenses$/)
    const { user } = await openGoa()
    const form = await openAddForm(user)
    await user.type(within(form).getByLabelText('Description'), 'Hotel')
    await user.type(within(form).getByLabelText('Amount (INR)'), '1000')

    await user.click(within(form).getByRole('radio', { name: 'Shares' }))
    await user.clear(within(form).getByLabelText('Shares for Alice'))
    await user.type(within(form).getByLabelText('Shares for Alice'), '2')
    expect(owes('Alice')).toHaveTextContent('₹500.00')
    expect(owes('Bob')).toHaveTextContent('₹250.00')

    await user.click(within(form).getByRole('radio', { name: 'Percent' }))
    await user.type(within(form).getByLabelText('Percent for Alice'), '50')
    await user.type(within(form).getByLabelText('Percent for Bob'), '30')
    await user.type(within(form).getByLabelText('Percent for Carol'), '10')
    expect(within(form).getByText('The percentages add up to 90%, they must add up to 100%')).toBeInTheDocument()

    await user.click(within(form).getByRole('button', { name: 'Add expense' }))
    expect(within(form).getByRole('button', { name: 'Add expense' })).toBeDisabled()
    expect(sent).toHaveLength(0)

    await user.clear(within(form).getByLabelText('Percent for Carol'))
    await user.type(within(form).getByLabelText('Percent for Carol'), '20')
    expect(owes('Carol')).toHaveTextContent('₹200.00')
    await user.click(within(form).getByRole('button', { name: 'Add expense' }))

    expect(await screen.findByText('"Hotel" added')).toBeInTheDocument()
    expect((sent[0] as CreateExpenseRequest).shares).toEqual([
      { userId: ALICE.id, value: 50 },
      { userId: BOB.id, value: 30 },
      { userId: CAROL.id, value: 20 },
    ])
    server.events.removeAllListeners()
  })

  it('tells how much is left to assign in an exact split', async () => {
    const { user } = await openGoa()
    const form = await openAddForm(user)
    await user.type(within(form).getByLabelText('Amount (INR)'), '90')
    await user.click(within(form).getByRole('radio', { name: 'Exact' }))
    await user.type(within(form).getByLabelText('Exact for Alice'), '40')
    await user.type(within(form).getByLabelText('Exact for Bob'), '30')

    expect(within(form).getByText('Enter an amount for Carol')).toBeInTheDocument()
    await user.type(within(form).getByLabelText('Exact for Carol'), '10')
    expect(within(form).getByText('10.00 still to assign (the amounts add up to 80.00)')).toBeInTheDocument()
    expect(within(form).getByLabelText('Exact for Carol')).not.toHaveAttribute('aria-invalid')
  })

  it('asks for a description before saving', async () => {
    const sent = captureBodies('POST', /^\/api\/expenses$/)
    const { user } = await openGoa()
    const form = await openAddForm(user)
    await user.type(within(form).getByLabelText('Amount (INR)'), '50')
    await user.click(within(form).getByRole('button', { name: 'Add expense' }))

    expect(within(form).getByText('Describe the expense')).toBeInTheDocument()
    expect(within(form).getByLabelText('Description')).toHaveAttribute('aria-invalid', 'true')
    expect(sent).toHaveLength(0)
    server.events.removeAllListeners()
  })
})

describe('editing and deleting', () => {
  it('opens a shares expense as 2 : 1 : 1 and saves with the version it was loaded at', async () => {
    const sent = captureBodies('PUT', /^\/api\/expenses\/50$/)
    const { user } = await openGoa()

    await user.click(await screen.findByText('Villa (3 nights)'))
    const detail = screen.getByRole('dialog', { name: 'Villa (3 nights)' })
    expect(within(detail).getByText('(2 shares)')).toBeInTheDocument()
    await user.click(within(detail).getByRole('button', { name: 'Edit' }))

    const form = screen.getByRole('dialog', { name: 'Edit expense' })
    expect(within(form).getByRole('radio', { name: 'Shares' })).toHaveAttribute('aria-checked', 'true')
    expect(within(form).getByLabelText('Shares for Alice')).toHaveValue('2')
    expect(within(form).getByLabelText('Shares for Bob')).toHaveValue('1')
    expect(within(form).getByLabelText('Amount (INR)')).toHaveValue('9000.00')

    await user.clear(within(form).getByLabelText('Shares for Bob'))
    await user.type(within(form).getByLabelText('Shares for Bob'), '2')
    expect(owes('Bob')).toHaveTextContent('₹3,600.00')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('"Villa (3 nights)" updated')).toBeInTheDocument()
    expect(sent[0]).toMatchObject({ version: 0, splitType: 'SHARES' } satisfies Partial<UpdateExpenseRequest>)
    expect(db.expenses.find((e) => e.id === 50)?.shares.find((s) => s.userId === BOB.id)?.amount).toBe(3600)
    server.events.removeAllListeners()
  })

  it('offers the other version when someone else edited the expense first', async () => {
    const { user } = await openGoa()
    await user.click(await screen.findByText('Beach shack dinner'))
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const form = screen.getByRole('dialog', { name: 'Edit expense' })

    // Meanwhile Bob changes the amount
    const dinner = db.expenses.find((e) => e.id === 51)!
    Object.assign(dinner, makeExpense({ id: 51, groupId: 10, description: 'Beach shack dinner', amount: 1500, paidBy: BOB, splitType: 'EQUAL', shares: [{ user: ALICE }, { user: BOB }, { user: CAROL }], date: '2026-09-12' }), { version: 3 })

    await user.clear(within(form).getByLabelText('Description'))
    await user.type(within(form).getByLabelText('Description'), 'Dinner at the shack')
    await user.click(within(form).getByRole('button', { name: 'Save changes' }))

    expect(await within(form).findByText('Someone else changed this expense while you were editing.')).toBeInTheDocument()
    await user.click(within(form).getByRole('button', { name: 'Load their version' }))
    await waitFor(() => expect(within(form).getByLabelText('Amount (INR)')).toHaveValue('1500.00'))
    expect(within(form).getByLabelText('Description')).toHaveValue('Beach shack dinner')

    await user.click(within(form).getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText('"Beach shack dinner" updated')).toBeInTheDocument()
    expect(db.expenses.find((e) => e.id === 51)?.version).toBe(4)
  })

  it('shows an expense saved before split values were kept as exact amounts', async () => {
    db.expenses.push(
      makeExpense({ id: 60, groupId: 10, description: 'Old taxi', amount: 100, paidBy: ALICE, splitType: 'PERCENTAGE', shares: [{ user: ALICE, value: '70' }, { user: BOB, value: '30' }], storeValues: false, date: '2026-09-20' }),
    )
    const { user } = await openGoa()
    await user.click(await screen.findByText('Old taxi'))
    await user.click(screen.getByRole('button', { name: 'Edit' }))

    const form = screen.getByRole('dialog', { name: 'Edit expense' })
    expect(within(form).getByRole('radio', { name: 'Exact' })).toHaveAttribute('aria-checked', 'true')
    expect(within(form).getByText(/saved before split details were kept/)).toBeInTheDocument()
    expect(within(form).getByLabelText('Exact for Alice')).toHaveValue('70.00')
    expect(within(form).getByRole('checkbox', { name: 'Include Carol White' })).not.toBeChecked()
  })

  it('deletes an expense, and Undo brings it back', async () => {
    const { user } = await openGoa()
    await user.click(await screen.findByText('Beach shack dinner'))
    await user.click(screen.getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText('"Beach shack dinner" deleted')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Beach shack dinner')).not.toBeInTheDocument())
    expect(db.expenses.find((e) => e.id === 51)?.deleted).toBe(true)

    await user.click(screen.getByRole('button', { name: 'Undo' }))
    expect(await screen.findByText('"Beach shack dinner" restored')).toBeInTheDocument()
    expect(await screen.findByText('Beach shack dinner')).toBeInTheDocument()
  })
})

describe('balances and settling up in a group', () => {
  it('shows who is up and who is down, and refreshes after a new expense', async () => {
    const { user } = await openGoa('/groups/10?tab=balances')

    // Villa 9000 (2:1:1) paid by Alice, dinner 1200 (equal) paid by Bob
    const panel = screen.getByRole('tabpanel')
    const alice = (await within(panel).findByText('Alice Smith')).closest('li')!
    expect(alice).toHaveTextContent('+₹4,100.00')
    expect(within(panel).getByText('Bob Jones').closest('li')).toHaveTextContent('-₹1,450.00')

    const form = await openAddForm(user)
    await user.type(within(form).getByLabelText('Description'), 'Fuel')
    await user.type(within(form).getByLabelText('Amount (INR)'), '300')
    await user.click(within(form).getByRole('button', { name: 'Add expense' }))

    await waitFor(() => expect(within(panel).getByText('Alice Smith').closest('li')).toHaveTextContent('+₹4,300.00'))
  })

  it('lists the fewest payments, and records one that involves you', async () => {
    const { user } = await openGoa('/groups/10?tab=settle')

    const panel = screen.getByRole('tabpanel')
    await within(panel).findByText(/Carol White/)
    const payments = within(panel).getAllByRole('listitem')
    const carolPays = payments.find((li) => li.textContent?.includes('Carol White'))!
    expect(carolPays).toHaveTextContent('Carol White pays you₹2,650.00')
    const bobPays = payments.find((li) => li.textContent?.includes('Bob Jones'))!
    expect(bobPays).toHaveTextContent('₹1,450.00')

    await user.click(within(carolPays).getByRole('button', { name: 'Record payment' }))
    const dialog = screen.getByRole('dialog', { name: 'Record a payment' })
    expect(within(dialog).getByText('Carol White paid you:')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Amount (INR)')).toHaveValue('2650.00')
    await user.click(within(dialog).getByRole('button', { name: 'Record payment' }))

    expect(await screen.findByText('Payment of ₹2,650.00 recorded')).toBeInTheDocument()
    // The suggestion is gone, and the payment is listed as recorded
    await waitFor(() => expect(within(panel).queryByText(/Carol White pays/)).not.toBeInTheDocument())
    expect(await within(panel).findByText('Carol White paid you ₹2,650.00')).toBeInTheDocument()
    expect(db.settlements[0]).toMatchObject({ fromUserId: CAROL.id, toUserId: ALICE.id, amount: 2650, groupId: 10 })
  })

  it('does not offer to record payments between other people', async () => {
    server.use(
      http.get('*/api/groups/10/debts/simplified', () =>
        HttpResponse.json([{ fromUserId: BOB.id, fromName: BOB.name, toUserId: CAROL.id, toName: CAROL.name, amount: 50, currency: 'INR' }]),
      ),
    )
    await openGoa('/groups/10?tab=settle')

    expect(await screen.findByText(/pays/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Record payment' })).not.toBeInTheDocument()
  })
})
