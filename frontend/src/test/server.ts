import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import type {
  Activity,
  CreateExpenseRequest,
  CreateRecurringExpenseRequest,
  RecurringExpense,
  Expense,
  FriendBalance,
  FriendTotals,
  Group,
  Settlement,
  UpdateExpenseRequest,
  User,
} from '../api/types'
import { today } from '../lib/dates'
import { computeSplit, paiseToString } from '../lib/split'

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

type Db = {
  groups: Group[]
  friends: FriendBalance[]
  expenses: Expense[]
  settlements: Settlement[]
  activity: Activity[]
  recurring: RecurringExpense[]
  nextId: number
}

/** An expense as the API would return it, with shares from the same split rules the API uses. */
export function makeExpense(fields: {
  id: number
  groupId: number | null
  description: string
  amount: number
  paidBy: User
  splitType: Expense['splitType']
  shares: { user: User; value?: string }[]
  date?: string
  category?: Expense['category']
  currency?: string
  /** false: as saved before split values were kept */
  storeValues?: boolean
}): Expense {
  const split = computeSplit(
    fields.splitType,
    fields.amount.toFixed(2),
    fields.shares.map((s) => ({ userId: s.user.id, value: s.value ?? null })),
  )
  if (!split.ok) throw new Error(split.error)
  return {
    id: fields.id,
    description: fields.description,
    amount: fields.amount,
    currency: fields.currency ?? 'INR',
    paidBy: { id: fields.paidBy.id, name: fields.paidBy.name },
    groupId: fields.groupId,
    date: fields.date ?? '2026-09-10',
    splitType: fields.splitType,
    category: fields.category ?? 'GENERAL',
    notes: null,
    shares: fields.shares.map((s) => ({
      userId: s.user.id,
      name: s.user.name,
      amount: Number(paiseToString(split.owed.get(s.user.id)!)),
      value: fields.storeValues === false || fields.splitType === 'EQUAL' || !s.value ? null : Number(s.value),
    })),
    version: 0,
    createdById: fields.paidBy.id,
    createdAt: '2026-09-10T10:00:00Z',
    updatedById: null,
    updatedAt: null,
    deleted: false,
    recurringExpenseId: null,
  }
}

function initialDb(): Db {
  return {
    nextId: 100,
    settlements: [],
    // 25 entries, newest first: more than one page of 20
    activity: Array.from({ length: 25 }, (_, i): Activity => ({
      id: 1000 - i,
      type: i === 0 ? 'EXPENSE_UPDATED' : i % 2 ? 'EXPENSE_ADDED' : 'SETTLEMENT_ADDED',
      actorId: i === 0 ? BOB.id : ALICE.id,
      actorName: i === 0 ? BOB.name : ALICE.name,
      groupId: i % 5 === 4 ? 11 : 10,
      expenseId: i % 2 ? 50 : null,
      settlementId: null,
      summary: i === 0 ? 'Bob Jones updated "Beach shack dinner"' : `Entry ${i}`,
      details: i === 0 ? ['Amount: 1,000.00 -> 1,200.00', 'Carol White added to the split'] : [],
      createdAt: new Date(Date.UTC(2026, 9, 12, 10, 0) - i * 3_600_000).toISOString(),
    })),
    recurring: [
      {
        id: 300, description: 'Rent', amount: 24000, currency: 'INR', paidById: ALICE.id, groupId: 10, splitType: 'EQUAL',
        category: 'RENT', notes: null, shares: [{ userId: ALICE.id }, { userId: BOB.id }, { userId: CAROL.id }],
        frequency: 'MONTHLY', startDate: '2026-09-30', endDate: null, nextDate: '2026-10-31', active: true,
        stoppedReason: null, createdById: ALICE.id, createdAt: '2026-09-01T10:00:00Z',
      },
      {
        id: 301, description: 'Netflix', amount: 649, currency: 'INR', paidById: CAROL.id, groupId: 10, splitType: 'EQUAL',
        category: 'ENTERTAINMENT', notes: null, shares: [{ userId: ALICE.id }, { userId: CAROL.id }],
        frequency: 'MONTHLY', startDate: '2026-05-05', endDate: null, nextDate: null, active: false,
        stoppedReason: 'Stopped by Carol White', createdById: CAROL.id, createdAt: '2026-05-01T10:00:00Z',
      },
    ],
    // Goa Trip: Alice paid the villa (shares 2:1:1), Bob paid dinner (equal)
    expenses: [
      makeExpense({
        id: 50,
        groupId: 10,
        description: 'Villa (3 nights)',
        amount: 9000,
        paidBy: ALICE,
        splitType: 'SHARES',
        shares: [
          { user: ALICE, value: '2' },
          { user: BOB, value: '1' },
          { user: CAROL, value: '1' },
        ],
        date: '2026-09-10',
        category: 'RENT',
      }),
      makeExpense({
        id: 51,
        groupId: 10,
        description: 'Beach shack dinner',
        amount: 1200,
        paidBy: BOB,
        splitType: 'EQUAL',
        shares: [{ user: ALICE }, { user: BOB }, { user: CAROL }],
        date: '2026-09-12',
        category: 'FOOD_AND_DRINK',
      }),
    ],
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
    return { error: errorBody(403, 'You are not a member of this group', path) }
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

  http.post('*/api/auth/refresh', ({ request }) => {
    if (request.headers.get('Authorization') === `Bearer ${VALID_TOKEN}`) {
      return HttpResponse.json({ accessToken: VALID_TOKEN, tokenType: 'Bearer', expiresInSeconds: 3600 })
    }
    return errorBody(403, 'Your session has ended; please sign in again', '/api/auth/refresh')
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
    if (owes) {
      return errorBody(409, `${nameOf(userId)} still has a balance of ${group.currency} 3000.00 in this group; settle up first`, '/api/groups/members')
    }
    group.members = group.members.filter((m) => m.id !== userId)
    return HttpResponse.json(group)
  }),
]

/** Net balance in paise per member of a group, from its expenses and payments (positive = is owed money). */
function groupBalances(group: Group): Map<number, number> {
  const net = new Map<number, number>(group.members.map((m) => [m.id, 0]))
  const addTo = (id: number, paise: number) => net.set(id, (net.get(id) ?? 0) + paise)
  for (const e of db.expenses.filter((x) => x.groupId === group.id && !x.deleted)) {
    addTo(e.paidBy.id, Math.round(e.amount * 100))
    for (const share of e.shares) addTo(share.userId, -Math.round(share.amount * 100))
  }
  for (const p of db.settlements.filter((x) => x.groupId === group.id)) {
    addTo(p.fromUserId, Math.round(p.amount * 100))
    addTo(p.toUserId, -Math.round(p.amount * 100))
  }
  return net
}

const nameOf = (id: number) => USERS.find((u) => u.id === id)?.name ?? `User ${id}`

function saveExpense(body: CreateExpenseRequest | UpdateExpenseRequest, existing?: Expense): Expense | Response {
  const split = computeSplit(
    body.splitType,
    String(body.amount),
    body.shares.map((s) => ({ userId: s.userId, value: s.value === undefined ? null : String(s.value) })),
  )
  if (!split.ok) return errorBody(400, split.error, '/api/expenses')
  const groupId = existing ? existing.groupId : 'groupId' in body ? (body.groupId ?? null) : null
  return {
    id: existing?.id ?? db.nextId++,
    description: body.description,
    amount: body.amount,
    currency: db.groups.find((g) => g.id === groupId)?.currency ?? body.currency ?? existing?.currency ?? 'INR',
    paidBy: { id: body.paidById, name: nameOf(body.paidById) },
    groupId,
    date: body.date ?? '2026-10-12',
    splitType: body.splitType,
    category: body.category ?? 'GENERAL',
    notes: body.notes ?? null,
    shares: body.shares.map((s) => ({
      userId: s.userId,
      name: nameOf(s.userId),
      amount: Number(paiseToString(split.owed.get(s.userId)!)),
      value: body.splitType === 'EQUAL' ? null : (s.value ?? null),
    })),
    version: existing ? existing.version + 1 : 0,
    createdById: existing?.createdById ?? ALICE.id,
    createdAt: existing?.createdAt ?? '2026-10-12T10:00:00Z',
    updatedById: existing ? ALICE.id : null,
    updatedAt: existing ? '2026-10-12T11:00:00Z' : null,
    deleted: false,
    recurringExpenseId: null,
  }
}

handlers.push(
  http.get('*/api/groups/:groupId/expenses', ({ params, request }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups/expenses')
    if (error) return error
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = Number(url.searchParams.get('size') ?? 20)
    const q = url.searchParams.get('q')?.toLowerCase()
    const category = url.searchParams.get('category')
    const paidBy = url.searchParams.get('paidBy')
    const from = url.searchParams.get('from')
    const to = url.searchParams.get('to')
    const matching = db.expenses
      .filter((e) => e.groupId === group.id && !e.deleted)
      .filter((e) => !q || e.description.toLowerCase().includes(q))
      .filter((e) => !category || e.category === category)
      .filter((e) => !paidBy || e.paidBy.id === Number(paidBy))
      .filter((e) => (!from || e.date >= from) && (!to || e.date <= to))
      .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id)
    const totalPages = Math.ceil(matching.length / size)
    return HttpResponse.json({
      content: matching.slice(page * size, page * size + size),
      page,
      size,
      totalElements: matching.length,
      totalPages,
      last: page >= totalPages - 1,
    })
  }),

  http.post('*/api/expenses', async ({ request }) => {
    const saved = saveExpense((await request.json()) as CreateExpenseRequest)
    if (saved instanceof Response) return saved
    db.expenses.push(saved)
    return HttpResponse.json(saved, { status: 201 })
  }),

  http.get('*/api/expenses/:id', ({ params }) => {
    const expense = db.expenses.find((e) => e.id === Number(params.id))
    return expense ? HttpResponse.json(expense) : errorBody(404, `Expense ${params.id} not found`, '/api/expenses')
  }),

  http.put('*/api/expenses/:id', async ({ params, request }) => {
    const index = db.expenses.findIndex((e) => e.id === Number(params.id))
    const existing = db.expenses[index]
    if (!existing) return errorBody(404, `Expense ${params.id} not found`, '/api/expenses')
    const body = (await request.json()) as UpdateExpenseRequest
    if (body.version !== existing.version) {
      return errorBody(
        409,
        `Expense ${existing.id} was changed by someone else (now version ${existing.version}); reload it and try again`,
        '/api/expenses',
      )
    }
    const saved = saveExpense(body, existing)
    if (saved instanceof Response) return saved
    db.expenses[index] = saved
    return HttpResponse.json(saved)
  }),

  http.delete('*/api/expenses/:id', ({ params }) => {
    const expense = db.expenses.find((e) => e.id === Number(params.id))
    if (!expense) return errorBody(404, `Expense ${params.id} not found`, '/api/expenses')
    expense.deleted = true
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('*/api/expenses/:id/restore', ({ params }) => {
    const expense = db.expenses.find((e) => e.id === Number(params.id))
    if (!expense) return errorBody(404, `Expense ${params.id} not found`, '/api/expenses')
    expense.deleted = false
    return HttpResponse.json(expense)
  }),

  http.get('*/api/groups/:groupId/balances', ({ params }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups/balances')
    if (error) return error
    return HttpResponse.json(
      [...groupBalances(group)].map(([userId, paise]) => ({
        userId,
        name: nameOf(userId),
        netBalance: paise / 100,
        currency: group.currency,
      })),
    )
  }),

  http.get('*/api/groups/:groupId/debts/simplified', ({ params }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups/debts')
    if (error) return error
    // Greedy: the biggest debtor pays the biggest creditor until everyone is at zero
    const net = [...groupBalances(group)].map(([id, paise]) => ({ id, paise })).filter((x) => x.paise !== 0)
    const transfers = []
    for (;;) {
      const debtor = net.filter((x) => x.paise < 0).sort((a, b) => a.paise - b.paise)[0]
      const creditor = net.filter((x) => x.paise > 0).sort((a, b) => b.paise - a.paise)[0]
      if (!debtor || !creditor) break
      const paise = Math.min(-debtor.paise, creditor.paise)
      debtor.paise += paise
      creditor.paise -= paise
      transfers.push({
        fromUserId: debtor.id,
        fromName: nameOf(debtor.id),
        toUserId: creditor.id,
        toName: nameOf(creditor.id),
        amount: paise / 100,
        currency: group.currency,
      })
    }
    return HttpResponse.json(transfers)
  }),

  http.post('*/api/settlements', async ({ request }) => {
    const body = (await request.json()) as { fromUserId: number; toUserId: number; amount: number; groupId?: number; date?: string }
    if (body.fromUserId !== ALICE.id && body.toUserId !== ALICE.id) {
      return errorBody(403, 'You can only record settlements you paid or received', '/api/settlements')
    }
    const settlement: Settlement = {
      id: db.nextId++,
      fromUserId: body.fromUserId,
      fromName: nameOf(body.fromUserId),
      toUserId: body.toUserId,
      toName: nameOf(body.toUserId),
      amount: body.amount,
      currency: 'INR',
      groupId: body.groupId ?? null,
      date: body.date ?? '2026-10-12',
      note: null,
      version: 0,
      deleted: false,
    }
    db.settlements.push(settlement)
    return HttpResponse.json(settlement, { status: 201 })
  }),
)

/** One page of a list, as the API pages activity, expenses and payments. */
function activityPage<T>(items: T[], request: Request) {
  const url = new URL(request.url)
  const page = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 20)
  const totalPages = Math.ceil(items.length / size)
  return HttpResponse.json({
    content: items.slice(page * size, page * size + size),
    page,
    size,
    totalElements: items.length,
    totalPages,
    last: page >= totalPages - 1,
  })
}

handlers.push(
  http.get('*/api/users/me/activity', ({ request }) => activityPage(db.activity, request)),

  http.get('*/api/groups/:groupId/activity', ({ params, request }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups/activity')
    return error ?? activityPage(db.activity.filter((a) => a.groupId === group.id), request)
  }),

  http.get('*/api/recurring-expenses', () => HttpResponse.json(db.recurring)),

  http.post('*/api/recurring-expenses', async ({ request }) => {
    const body = (await request.json()) as CreateRecurringExpenseRequest
    if (body.startDate < today()) {
      return errorBody(400, 'startDate cannot be in the past', '/api/recurring-expenses')
    }
    const template: RecurringExpense = {
      id: db.nextId++,
      description: body.description,
      amount: body.amount,
      currency: db.groups.find((g) => g.id === body.groupId)?.currency ?? 'INR',
      paidById: body.paidById,
      groupId: body.groupId ?? null,
      splitType: body.splitType,
      category: body.category ?? 'GENERAL',
      notes: body.notes ?? null,
      shares: body.shares,
      frequency: body.frequency,
      startDate: body.startDate,
      endDate: body.endDate ?? null,
      nextDate: body.startDate,
      active: true,
      stoppedReason: null,
      createdById: ALICE.id,
      createdAt: '2026-10-12T10:00:00Z',
    }
    db.recurring.unshift(template)
    return HttpResponse.json(template, { status: 201 })
  }),

  http.delete('*/api/recurring-expenses/:id', ({ params }) => {
    const template = db.recurring.find((r) => r.id === Number(params.id))
    if (!template) return errorBody(404, `Recurring expense ${params.id} not found`, '/api/recurring-expenses')
    Object.assign(template, { active: false, nextDate: null, stoppedReason: `Stopped by ${ALICE.name}` })
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('*/api/recurring-expenses/:id/resume', ({ params }) => {
    const template = db.recurring.find((r) => r.id === Number(params.id))
    if (!template) return errorBody(404, `Recurring expense ${params.id} not found`, '/api/recurring-expenses')
    Object.assign(template, { active: true, nextDate: '2026-11-05', stoppedReason: null })
    return HttpResponse.json(template)
  }),

  http.get('*/api/groups/:groupId/report', ({ params, request }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups/report')
    if (error) return error
    const url = new URL(request.url)
    const from = url.searchParams.get('from')
    const to = url.searchParams.get('to')
    const expenses = db.expenses.filter(
      (e) => e.groupId === group.id && !e.deleted && (!from || e.date >= from) && (!to || e.date <= to),
    )
    const byCategory = new Map<string, { total: number; count: number }>()
    for (const e of expenses) {
      const c = byCategory.get(e.category) ?? { total: 0, count: 0 }
      byCategory.set(e.category, { total: c.total + e.amount, count: c.count + 1 })
    }
    const byMember = group.members.map((m) => {
      const paid = expenses.filter((e) => e.paidBy.id === m.id).reduce((a, e) => a + e.amount, 0)
      const share = expenses.flatMap((e) => e.shares).filter((s) => s.userId === m.id).reduce((a, s) => a + s.amount, 0)
      return { userId: m.id, name: m.name, paid, share, net: paid - share }
    })
    return HttpResponse.json({
      groupId: group.id,
      groupName: group.name,
      from,
      to,
      currency: group.currency,
      expenseCount: expenses.length,
      totalSpent: expenses.reduce((a, e) => a + e.amount, 0),
      byCategory: [...byCategory].map(([category, c]) => ({ category, ...c })).sort((a, b) => b.total - a.total),
      byMember,
    })
  }),

  http.get('*/api/groups/:groupId/expenses.csv', ({ params }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups/expenses.csv')
    if (error) return error
    return new HttpResponse('Date,Description,Amount\n2026-09-10,Villa,9000.00\n', {
      headers: {
        'Content-Type': 'text/csv',
        'Content-Disposition': `attachment; filename="group-${group.id}-expenses.csv"`,
      },
    })
  }),
)

handlers.push(
  http.get('*/api/expenses/:id/activity', ({ params }) =>
    HttpResponse.json(db.activity.filter((a) => a.expenseId === Number(params.id))),
  ),

  http.get('*/api/groups/:groupId/settlements', ({ params, request }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups/settlements')
    if (error) return error
    const payments = db.settlements.filter((x) => x.groupId === group.id && !x.deleted).sort((a, b) => b.id - a.id)
    return activityPage(payments, request)
  }),

  http.delete('*/api/settlements/:id', ({ params }) => {
    const payment = db.settlements.find((x) => x.id === Number(params.id))
    if (!payment) return errorBody(404, `Settlement ${params.id} not found`, '/api/settlements')
    payment.deleted = true
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('*/api/settlements/:id/restore', ({ params }) => {
    const payment = db.settlements.find((x) => x.id === Number(params.id))
    if (!payment) return errorBody(404, `Settlement ${params.id} not found`, '/api/settlements')
    if (!payment.deleted) return errorBody(409, `Settlement ${payment.id} is not deleted`, '/api/settlements')
    payment.deleted = false
    return HttpResponse.json(payment)
  }),

  http.put('*/api/groups/:groupId', async ({ params, request }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups')
    if (error) return error
    group.name = ((await request.json()) as { name: string }).name
    return HttpResponse.json(group)
  }),

  http.delete('*/api/groups/:groupId', ({ params }) => {
    const { group, error } = memberGroup(Number(params.groupId), '/api/groups')
    if (error) return error
    if (group.createdById !== ALICE.id) return errorBody(403, 'Only the group creator can delete the group', '/api/groups')
    if (db.expenses.some((e) => e.groupId === group.id) || db.settlements.some((x) => x.groupId === group.id)) {
      return errorBody(409, 'Group has expenses or settlements and cannot be deleted', '/api/groups')
    }
    db.groups = db.groups.filter((g) => g !== group)
    return new HttpResponse(null, { status: 204 })
  }),
)

const involves = (e: Expense, id: number) => e.paidBy.id === id || e.shares.some((s) => s.userId === id)

handlers.push(
  http.patch('*/api/users/me', async ({ request }) => {
    const body = (await request.json()) as { name: string }
    return HttpResponse.json({ ...ALICE, name: body.name.trim() })
  }),

  // Like the API: the current password must be right; the answer is a fresh token for this device
  http.post('*/api/users/me/password', async ({ request }) => {
    const body = (await request.json()) as { currentPassword: string; newPassword: string }
    if (body.currentPassword !== ALICE_PASSWORD) {
      return errorBody(403, 'Current password is incorrect', '/api/users/me/password')
    }
    return HttpResponse.json({ accessToken: VALID_TOKEN, tokenType: 'Bearer', expiresInSeconds: 3600 })
  }),

  http.post('*/api/auth/logout-all', () => new HttpResponse(null, { status: 204 })),

  // Alice's contacts: everyone in her groups, plus Dan from a non-group expense
  http.get('*/api/users/me/contacts', () => {
    const inGroups = db.groups.filter((g) => g.members.some((m) => m.id === ALICE.id)).flatMap((g) => g.members)
    const people = [...inGroups, DAN].filter((u, i, all) => u.id !== ALICE.id && all.findIndex((v) => v.id === u.id) === i)
    return HttpResponse.json(people.sort((a, b) => a.name.localeCompare(b.name)))
  }),

  http.get('*/api/users/me/expenses', ({ request }) => {
    const url = new URL(request.url)
    const q = url.searchParams.get('q')?.toLowerCase()
    const category = url.searchParams.get('category')
    const withId = Number(url.searchParams.get('with')) || null
    const matching = db.expenses
      .filter((e) => !e.deleted && involves(e, ALICE.id) && (withId === null || involves(e, withId)))
      .filter((e) => (!q || e.description.toLowerCase().includes(q)) && (!category || e.category === category))
      .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id)
    return activityPage(matching, request)
  }),

  http.get('*/api/users/me/friends/:friendId', ({ params }) => {
    const id = Number(params.friendId)
    const friend = db.friends.find((f) => f.userId === id)
    if (friend) return HttpResponse.json(friend)
    const user = USERS.find((u) => u.id === id)
    if (!user) return errorBody(404, `No shared groups or expenses with user ${id}`, '/api/users/me/friends')
    return HttpResponse.json({ userId: id, name: user.name, balances: [], breakdown: [] })
  }),
)

export const server = setupServer(...handlers)

