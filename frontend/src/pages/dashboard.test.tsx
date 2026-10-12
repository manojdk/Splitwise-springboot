import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderApp, signedIn } from '../test/renderApp'
import { db, errorBody, server } from '../test/server'

function section(name: string) {
  return screen.getByRole('region', { name })
}

describe('dashboard', () => {
  it('shows what you are owed and what you owe per currency, never adding currencies together', async () => {
    signedIn()
    renderApp('/')

    const owed = await screen.findByRole('region', { name: 'You are owed' })
    expect(within(owed).getByText('€45.00')).toBeInTheDocument()
    expect(within(owed).getByText('₹3,800.00')).toBeInTheDocument()
    expect(within(section('You owe')).getByText('-$10.00')).toBeInTheDocument()
    const net = section('Net balance')
    expect(within(net).getByText('+₹3,800.00')).toBeInTheDocument()
    expect(within(net).getByText('+€45.00')).toBeInTheDocument()
    expect(within(net).getByText('-$10.00')).toBeInTheDocument()
  })

  it('lists friends with each balance and where it comes from', async () => {
    signedIn()
    renderApp('/')

    const friends = await screen.findByRole('region', { name: 'Friends' })
    const carol = within(friends).getByText('Carol White').closest('li')!
    expect(within(carol).getByText('Goa Trip · Paris')).toBeInTheDocument()
    expect(within(carol).getByText('+€45.00')).toBeInTheDocument()
    expect(within(carol).getByText('+₹800.00')).toBeInTheDocument()
    const dan = within(friends).getByText('Dan Brown').closest('li')!
    expect(within(dan).getByText('Outside groups')).toBeInTheDocument()
    expect(within(dan).getByText('you owe')).toBeInTheDocument()
  })

  it("shows the user's balance in each group", async () => {
    signedIn()
    renderApp('/')

    const groups = await screen.findByRole('region', { name: 'Your groups' })
    const goa = (await within(groups).findByText('Goa Trip')).closest('a')!
    expect(goa).toHaveAttribute('href', '/groups/10')
    expect(within(goa).getByText('+₹3,800.00')).toBeInTheDocument()
    expect(within(goa).getByText('3 members · INR')).toBeInTheDocument()
    expect(within(groups).getByText('Paris').closest('a')).toHaveTextContent('+€45.00')
  })

  it('settles up after confirming, one payment per group and currency', async () => {
    signedIn()
    const { user } = renderApp('/')

    await user.click(await screen.findByRole('button', { name: 'Settle up with Carol White' }))
    const dialog = screen.getByRole('dialog', { name: 'Settle up with Carol' })
    const payments = within(dialog).getAllByRole('listitem')
    expect(payments).toHaveLength(2)
    expect(payments[0]).toHaveTextContent('Carol pays you')
    expect(payments[0]).toHaveTextContent('Goa Trip')
    expect(payments[0]).toHaveTextContent('₹800.00')
    expect(payments[1]).toHaveTextContent('Paris')
    expect(payments[1]).toHaveTextContent('€45.00')

    await user.click(within(dialog).getByRole('button', { name: 'Record payments' }))

    expect(await screen.findByText('Settled up with Carol: 2 payments recorded')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Carol White')).not.toBeInTheDocument())
    expect(within(section('You are owed')).queryByText('€45.00')).not.toBeInTheDocument()
    expect(db.friends.map((f) => f.name)).toEqual(['Bob Jones', 'Dan Brown'])
  })

  it('records nothing when the user cancels', async () => {
    let calls = 0
    server.use(http.post('*/api/users/me/friends/:id/settle-up', () => void calls++))
    signedIn()
    const { user } = renderApp('/')

    await user.click(await screen.findByRole('button', { name: 'Settle up with Dan Brown' }))
    expect(within(screen.getByRole('dialog')).getByText('You pay Dan')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(calls).toBe(0)
    expect(screen.getByText('Dan Brown')).toBeInTheDocument()
  })

  it('keeps the dialog open with the reason when settling up fails', async () => {
    server.use(
      http.post('*/api/users/me/friends/:id/settle-up', () =>
        errorBody(409, 'The data was changed by someone else; reload and try again', '/x'),
      ),
    )
    signedIn()
    const { user } = renderApp('/')

    await user.click(await screen.findByRole('button', { name: 'Settle up with Bob Jones' }))
    await user.click(screen.getByRole('button', { name: 'Record payments' }))

    expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('changed by someone else')
    expect(screen.getByText('Bob Jones')).toBeInTheDocument()
  })

  it('says so when everything is settled', async () => {
    db.friends = []
    signedIn()
    renderApp('/')

    expect(await screen.findByText('All settled up')).toBeInTheDocument()
    expect(screen.getByText('No open balances with anyone.')).toBeInTheDocument()
    expect((await within(section('Your groups')).findByText('Goa Trip')).closest('a')).toHaveTextContent('settled up')
  })

  it('offers a retry when balances cannot be loaded', async () => {
    let fail = true
    server.use(
      http.get('*/api/users/me/friends', () =>
        fail ? errorBody(503, 'Service unavailable', '/api/users/me/friends') : HttpResponse.json({ totals: [], friends: [] }),
      ),
    )
    signedIn()
    const { user } = renderApp('/')

    expect(await screen.findByText('Service unavailable')).toBeInTheDocument()
    fail = false
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('All settled up')).toBeInTheDocument()
  })
})
