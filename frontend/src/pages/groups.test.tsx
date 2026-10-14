import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderApp, signedIn } from '../test/renderApp'
import { ALICE, BOB, CAROL, db, server } from '../test/server'

describe('groups list', () => {
  it("lists the user's groups with members and the user's balance", async () => {
    signedIn()
    renderApp('/groups')

    const goa = (await screen.findByRole('heading', { name: 'Goa Trip' })).closest('a')!
    expect(goa).toHaveAttribute('href', '/groups/10')
    expect(within(goa).getByText('Alice, Bob, Carol')).toBeInTheDocument()
    expect(await within(goa).findByText(/You are owed/)).toHaveTextContent('You are owed ₹3,800.00')
    expect(screen.getByRole('heading', { name: 'Paris' }).closest('a')).toHaveTextContent('EUR')
  })
})

describe('creating a group', () => {
  it('creates it in the chosen currency, adds people, and opens it', async () => {
    let created: unknown
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST' && new URL(request.url).pathname === '/api/groups') {
        created = await request.clone().json()
      }
    })
    signedIn()
    const { user, router } = renderApp('/groups')

    await user.click(await screen.findByRole('button', { name: '+ New group' }))
    const dialog = screen.getByRole('dialog', { name: 'New group' })
    expect(within(dialog).getByLabelText('Name')).toHaveFocus()
    await user.type(within(dialog).getByLabelText('Name'), '  Flat 4B ')
    await user.selectOptions(within(dialog).getByLabelText('Currency'), 'EUR')
    await user.type(within(dialog).getByLabelText('Add people (optional)'), 'BOB@example.com,\ncarol@example.com bob@example.com')
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Flat 4B' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/groups/100')
    expect(created).toEqual({ name: 'Flat 4B', currency: 'EUR' })
    expect(screen.getByText('Group "Flat 4B" created')).toBeInTheDocument()
    const members = screen.getByRole('region', { name: /Members/ })
    expect(within(members).getByText('Members (3)')).toBeInTheDocument()
    expect(within(members).getByText('Bob Jones')).toBeInTheDocument()
    expect(within(members).getByText('Carol White')).toBeInTheDocument()
    server.events.removeAllListeners()
  })

  it('keeps the group and lists the people who could not be added', async () => {
    signedIn()
    const { user, router } = renderApp('/groups')

    await user.click(await screen.findByRole('button', { name: '+ New group' }))
    await user.type(screen.getByLabelText('Name'), 'Book club')
    await user.type(screen.getByLabelText('Add people (optional)'), 'bob@example.com, nobody@example.com')
    await user.click(screen.getByRole('button', { name: 'Create group' }))

    const dialog = await screen.findByRole('dialog', { name: 'Group created' })
    expect(within(dialog).getByRole('listitem')).toHaveTextContent('nobody@example.com (no account with this email)')
    expect(db.groups.find((g) => g.name === 'Book club')?.members.map((m) => m.id)).toEqual([ALICE.id, BOB.id])

    await user.click(within(dialog).getByRole('button', { name: 'Open the group' }))
    expect(router.state.location.pathname).toBe('/groups/100')
    expect(await screen.findByRole('heading', { level: 1, name: 'Book club' })).toBeInTheDocument()
  })

  it('checks the form before creating anything', async () => {
    let calls = 0
    server.use(http.post('*/api/groups', () => void calls++))
    signedIn()
    const { user } = renderApp('/groups')

    await user.click(await screen.findByRole('button', { name: '+ New group' }))
    await user.type(screen.getByLabelText('Add people (optional)'), 'bob@example.com, not-an-email')
    await user.click(screen.getByRole('button', { name: 'Create group' }))

    expect(await screen.findByText('Give the group a name')).toBeInTheDocument()
    expect(screen.getByText('"not-an-email" is not a valid email address')).toBeInTheDocument()
    expect(calls).toBe(0)
  })

  it('closes on Escape without creating anything', async () => {
    signedIn()
    const { user } = renderApp('/groups')

    const open = await screen.findByRole('button', { name: '+ New group' })
    await user.click(open)
    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(open).toHaveFocus()
    expect(db.groups).toHaveLength(2)
  })
})

describe('a group page', () => {
  it('adds a member by email, and explains why one cannot be added', async () => {
    signedIn()
    const { user } = renderApp('/groups/10')

    const field = await screen.findByLabelText('Add someone by email')
    await user.type(field, 'dan@example.com')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    expect(await screen.findByText('Dan Brown added to Goa Trip')).toBeInTheDocument()
    expect(screen.getByText('Members (4)')).toBeInTheDocument()
    expect(field).toHaveValue('')

    await user.type(field, 'bob@example.com')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    expect(await screen.findByText('User is already a member of this group')).toBeInTheDocument()

    await user.clear(field)
    await user.type(field, 'zed@example.com')
    await user.click(screen.getByRole('button', { name: 'Add' }))
    expect(await screen.findByText('No user with email zed@example.com')).toBeInTheDocument()
  })

  it('lets the creator remove a settled member, and refuses one with a balance', async () => {
    db.groups[0]!.members.push({ id: 4, name: 'Dan Brown', email: 'dan@example.com' })
    signedIn()
    const { user } = renderApp('/groups/10')

    await user.click(await screen.findByRole('button', { name: `Remove ${CAROL.name}` }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove' }))
    expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent(`${CAROL.name} still has a balance`)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    await user.click(screen.getByRole('button', { name: 'Remove Dan Brown' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove' }))
    expect(await screen.findByText('Dan Brown removed from Goa Trip')).toBeInTheDocument()
    expect(screen.getByText('Members (3)')).toBeInTheDocument()
    // The creator cannot leave; there is no button to try
    expect(screen.queryByRole('button', { name: 'Leave the group' })).not.toBeInTheDocument()
  })

  it('lets a member who is not the creator leave, but not remove others', async () => {
    db.groups.push({ id: 12, name: 'Book club', currency: 'INR', createdById: CAROL.id, createdAt: '2026-10-01T00:00:00Z', members: [CAROL, ALICE] })
    signedIn()
    const { user, router } = renderApp('/groups/12')

    expect(await screen.findByRole('heading', { name: 'Book club' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: `Remove ${CAROL.name}` })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Leave the group' }))
    await user.click(within(screen.getByRole('dialog', { name: 'Leave Book club?' })).getByRole('button', { name: 'Leave group' }))

    expect(await screen.findByText('You left Book club')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/groups')
    // Wait for the list itself, then check the group is gone from it
    expect(await screen.findByRole('heading', { name: 'Goa Trip' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Book club' })).not.toBeInTheDocument()
  })

  it('explains a group that does not exist or is not yours', async () => {
    db.groups.push({ id: 13, name: 'Secret', currency: 'INR', createdById: BOB.id, createdAt: '2026-10-01T00:00:00Z', members: [BOB] })
    signedIn()
    const first = renderApp('/groups/999')
    expect(await screen.findByText('Group 999 not found')).toBeInTheDocument()
    first.unmount()

    renderApp('/groups/13')
    expect(await screen.findByText('You are not a member of this group')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('shows the not-found page for a malformed id', async () => {
    signedIn()
    renderApp('/groups/abc')
    expect(await screen.findByText('This page does not exist')).toBeInTheDocument()
  })
})

describe('groups list while balances load', () => {
  it('does not claim "settled up" before the balances arrive', async () => {
    let release!: () => void
    const gate = new Promise<void>((resolve) => (release = resolve))
    server.use(
      http.get('*/api/users/me/friends', async () => {
        await gate
        return HttpResponse.json({ totals: [], friends: [] })
      }),
    )
    signedIn()
    renderApp('/groups')

    await screen.findByRole('heading', { name: 'Goa Trip' })
    expect(screen.queryByText('You are settled up')).not.toBeInTheDocument()
    release()
    expect(await screen.findAllByText('You are settled up')).toHaveLength(2)
  })
})
