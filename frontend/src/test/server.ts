import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import type { FriendBalance, FriendTotals, Group, User } from '../api/types'

/**
 * A fake API with the same paths, shapes and error bodies as the Spring Boot backend, backed by a small
 * in-memory state that each test starts fresh (resetDb runs after every test).
 */
export const ALICE: User = { id: 1, name: 'Alice Smith', email: 'alice@example.com' }
export const BOB: User = { id: 2, name: 'Bob Jones', email: 'bob@example.com' }
export const CAROL: User = { id: 3, name: 'Carol White', email: 'carol@example.com' }
export const DAN: User = { id: 4, name: 'Dan Brown', email: 'dan@example.com' }
export const ALICE_PASSWORD = 'password123'
export const VALID_TOKEN = 'token-alice'

const USERS = [ALICE, BOB, CAROL, DAN]

type Db = { groups: Group[]; friends: FriendBalance[]; nextId: number }

function initialDb(): Db {
  return {
    nextId: 100,
    groups: [
      { id: 10, name: 'Goa Trip', currency: 'INR', createdById: ALICE.id, createdAt: '2026-09-01T10:00:00Z', members: [ALICE, BOB, CAROL] },
      { id: 11, name: 'Paris', currency: 'EUR', createdById: CAROL.id, createdAt: '2026-09-05T10:00:00Z', members: [CAROL, ALICE] },
    ],
    // Bob owes Alice INR 3,000 in Goa; Carol owes Alice EUR 45 in Paris and INR 800 in Goa; Alice owes Dan USD 10 outside groups
    friends: [
      {
        userId: BOB.id,
        name: BOB.name,
        balances: [{ currency: 'INR', amount: 3000 }],
        breakdown: [{ groupId: 10, groupName: 'Goa Trip', currency: 'INR', amount: 3000 }],
      },
      {
        userId: CAROL.id,
        name: CAROL.name,
        balances: [
          { currency: 'EUR', amount: 45 },
          { currency: 'INR', amount: 800 },
        ],
        breakdown: [
          { groupId: 10, groupName: 'Goa Trip', currency: 'INR', amount: 800 },
          { groupId: 11, groupName: 'Paris', currency: 'EUR', amount: 45 },
        ],
      },
      {
        userId: DAN.id,
        name: DAN.name,
        balances: [{ currency: 'USD', amount: -10 }],
        breakdown: [{ groupId: null, groupName: null, currency: 'USD', amount: -10 }],
      },
    ],
  }
}

export let db: Db = initialDb()

export function resetDb(): void {
  db = initialDb()
}

/** Totals per currency, computed like the backend does. */
function totals(friends: FriendBalance[]): FriendTotals[] {
  const byCurrency = new Map<string, FriendTotals>()
  for (const { currency, amount } of friends.flatMap((f) => f.balances)) {
    const t = byCurrency.get(currency) ?? { currency, youAreOwed: 0, youOwe: 0, net: 0 }
    if (amount > 0) t.youAreOwed += amount
    else t.youOwe -= amount
    t.net += amount
    byCurrency.set(currency, t)
  }
  return [...byCurrency.values()].sort((a, b) => a.currency.localeCompare(b.currency))
}

export function errorBody(status: number, message: string, path: string) {
  return HttpResponse.json({ timestamp: new Date().toISOString(), status, error: 'Error', message, path }, { status })
}

function memberGroup(id: number, path: string) {
  const group = db.groups.find((g) => g.id === id)
  if (!group) return { error: errorBody(404, `Group ${id} not found`, path) }
  if (!group.members.some((m) => m.id === ALICE.id)) {
    return { error: errorBody(403, `You are not a member of group ${id}`, path) }
  }
  return { group }
}

export const handlers = [
  http.post('*/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string }
    if (body.email === ALICE.email && body.password === ALICE_PASSWORD) {
      return HttpResponse.json({ accessToken: VALID_TOKEN, tokenType: 'Bearer', expiresInSeconds: 3600 })
    }
    return errorBody(401, 'Invalid email or password', '/api/auth/login')
  }),

  http.post('*/api/auth/register', async ({ request }) => {
    const body = (await request.json()) as { name: string; email: string; password: string }
    if (body.email === 'taken@example.com') return errorBody(409, 'Email is already registered', '/api/auth/register')
    return HttpResponse.json({ id: 2, name: body.name, email: body.email }, { status: 201 })
  }),

  http.get('*/api/users/me', ({ request }) => {
    if (request.headers.get('Authorization') === `Bearer ${VALID_TOKEN}`) return HttpResponse.json(ALICE)
    return errorBody(401, 'Missing or invalid access token', '/api/users/me')
  }),

  http.get('*/api/users/me/friends', () => HttpResponse.json({ totals: totals(db.friends), friends: db.friends })),

  http.post('*/api/users/me/friends/:friendId/settle-up', ({ params }) => {
    const friend = db.friends.find((f) => f.userId === Number(params.friendId))
    if (!friend) return errorBody(409, 'Nothing to settle with this user', '/api/users/me/friends/settle-up')
    db.friends = db.friends.filter((f) => f !== friend)
    const settlements = friend.breakdown.map((item, i) => ({
      id: 500 + i,
      fromUserId: item.amount > 0 ? friend.userId : ALICE.id,
      fromName: item.amount > 0 ? friend.name : ALICE.name,
      toUserId: item.amount > 0 ? ALICE.id : friend.userId,
      toName: item.amount > 0 ? ALICE.name : friend.name,
      amount: Math.abs(item.amount),
      currency: item.currency,
      groupId: item.groupId,
      date: '2026-10-12',
      note: null,
      version: 0,
      deleted: false,
    }))
    return HttpResponse.json(
      { settlements, balance: { userId: friend.userId, name: friend.name, balances: [], breakdown: [] } },
      { status: 201 },
    )
  }),

  http.get('*/api/groups', () =>
    HttpResponse.json(db.groups.filter((g) => g.members.some((m) => m.id === ALICE.id))),
  ),

  http.post('*/api/groups', async ({ request }) => {
    const body = (await request.json()) as { name: string; currency?: string }
    const group: Group = {
      id: db.nextId++,
      name: body.name,
      currency: body.currency ?? 'INR',
      createdById: ALICE.id,
      createdAt: '2026-10-12T10:00:00Z',
      members: [ALICE],
    }
    db.groups.push(group)
    return HttpResponse.json(group, { status: 201 })
  }),

  http.get('*/api/groups/:groupId', ({ params }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups')
    return error ?? HttpResponse.json(group)
  }),

  http.post('*/api/groups/:groupId/members', async ({ params, request }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups/members')
    if (error) return error
    const { email } = (await request.json()) as { email: string }
    const user = USERS.find((u) => u.email === email.toLowerCase())
    if (!user) return errorBody(404, `No user with email ${email}`, '/api/groups/members')
    if (group.members.some((m) => m.id === user.id)) {
      return errorBody(409, 'User is already a member of this group', '/api/groups/members')
    }
    group.members = [...group.members, user]
    return HttpResponse.json(group)
  }),

  http.delete('*/api/groups/:groupId/members/:userId', ({ params }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups/members')
    if (error) return error
    const userId = Number(params.userId)
    const owes = db.friends.some((f) => f.userId === userId && f.breakdown.some((i) => i.groupId === group.id))
    if (owes) return errorBody(409, `User ${userId} still has a balance in this group`, '/api/groups/members')
    group.members = group.members.filter((m) => m.id !== userId)
    return HttpResponse.json(group)
  }),
]

export const server = setupServer(...handlers)
