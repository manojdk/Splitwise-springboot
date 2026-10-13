import type {
  Activity,
  CreateExpenseRequest,
  CreateGroupRequest,
  CreateRecurringExpenseRequest,
  CreateSettlementRequest,
  Expense,
  FriendBalance,
  FriendBalanceItem,
  FriendBalances,
  FriendTotals,
  Group,
  GroupReport,
  MemberBalance,
  RecurringExpense,
  RegisterRequest,
  Settlement,
  SettleUpResult,
  TokenResponse,
  Transfer,
  UpdateExpenseRequest,
  User,
} from '../api/types'
import { today } from '../lib/dates'
import { computeSplit, paiseToString } from '../lib/split'

/**
 * The Splitwise API, in the browser, for the GitHub Pages demo: the same rules as the Spring Boot backend
 * (membership checks, split validation, balances from the group's simplified debts, optimistic locking, an
 * activity entry for every change) over a small state the caller persists. No network, no real accounts.
 */

export type DemoUser = User & { password: string }
type Feed = Activity & { involved: number[] }

export type DemoState = {
  version: 1
  nextId: number
  users: DemoUser[]
  groups: Group[]
  expenses: Expense[]
  settlements: Settlement[]
  recurring: RecurringExpense[]
  activity: Feed[]
}

export class DemoError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number; last: boolean }

export type ExpenseQuery = {
  page: number
  size: number
  q?: string
  category?: string
  paidBy?: number
  from?: string
  to?: string
}

const TOKEN_PREFIX = 'demo-token-'

const toPaise = (amount: number) => Math.round(amount * 100)
const fromPaise = (paise: number) => paise / 100
/** "INR 1200.00", as the backend writes amounts in activity text. */
const money = (currency: string, amount: number) => `${currency} ${amount.toFixed(2)}`
const plain = (amount: number) => amount.toFixed(2)

export function emptyState(): DemoState {
  return { version: 1, nextId: 1, users: [], groups: [], expenses: [], settlements: [], recurring: [], activity: [] }
}

export class DemoBackend {
  readonly state: DemoState
  private readonly now: () => Date

  constructor(state: DemoState, now: () => Date = () => new Date()) {
    this.state = state
    this.now = now
  }

  // ------------------------------------------------------------------ auth

  login(email: string, password: string): TokenResponse {
    const user = this.state.users.find((u) => u.email === email.trim().toLowerCase())
    if (!user || user.password !== password) throw new DemoError(401, 'Invalid email or password')
    return { accessToken: TOKEN_PREFIX + user.id, tokenType: 'Bearer', expiresInSeconds: 3600 }
  }

  register(body: RegisterRequest): User {
    const email = body.email.trim().toLowerCase()
    if (this.state.users.some((u) => u.email === email)) throw new DemoError(409, 'Email is already registered')
    const user: DemoUser = { id: this.id(), name: body.name.trim(), email, password: body.password }
    this.state.users.push(user)
    return publicUser(user)
  }

  /** The signed-in user from an Authorization header, or 401. */
  caller(authorization: string | null): User {
    const id = Number(authorization?.replace(`Bearer ${TOKEN_PREFIX}`, ''))
    const user = this.state.users.find((u) => u.id === id)
    if (!authorization?.startsWith(`Bearer ${TOKEN_PREFIX}`) || !user) {
      throw new DemoError(401, 'Missing or invalid access token')
    }
    return publicUser(user)
  }

  // ------------------------------------------------------------------ groups

  groupsOf(me: User): Group[] {
    return this.state.groups.filter((g) => isMember(g, me.id))
  }

  group(me: User, groupId: number): Group {
    const group = this.state.groups.find((g) => g.id === groupId)
    if (!group) throw new DemoError(404, `Group ${groupId} not found`)
    if (!isMember(group, me.id)) throw new DemoError(403, `You are not a member of group ${groupId}`)
    return group
  }

  createGroup(me: User, body: CreateGroupRequest): Group {
    const others = (body.memberIds ?? []).filter((id) => id !== me.id).map((id) => this.user(id))
    const group: Group = {
      id: this.id(),
      name: body.name.trim(),
      currency: body.currency ?? 'INR',
      createdById: me.id,
      createdAt: this.timestamp(),
      members: [me, ...others],
    }
    this.state.groups.push(group)
    this.record(me, 'GROUP_CREATED', `${me.name} created the group "${group.name}"`, {
      groupId: group.id,
      details: others.length ? [`Members: ${others.map((o) => o.name).join(', ')}`] : [],
    })
    return group
  }

  addMember(me: User, groupId: number, email: string): Group {
    const group = this.group(me, groupId)
    const user = this.state.users.find((u) => u.email === email.trim().toLowerCase())
    if (!user) throw new DemoError(404, `No user with email ${email}`)
    if (isMember(group, user.id)) throw new DemoError(409, 'User is already a member of this group')
    group.members = [...group.members, publicUser(user)]
    this.record(me, 'MEMBER_ADDED', `${me.name} added ${user.name} to "${group.name}"`, {
      groupId,
      involved: [user.id],
    })
    return group
  }

  removeMember(me: User, groupId: number, userId: number): Group {
    const group = this.group(me, groupId)
    const member = group.members.find((m) => m.id === userId)
    if (userId !== me.id && group.createdById !== me.id) {
      throw new DemoError(403, 'Only the group creator can remove other members')
    }
    if (userId === me.id && group.createdById === me.id) {
      throw new DemoError(409, 'The group creator cannot leave the group; delete the group instead')
    }
    if (!member) throw new DemoError(404, `User ${userId} is not a member of group ${groupId}`)
    const balance = this.netBalances(group).get(userId) ?? 0
    if (balance !== 0) {
      throw new DemoError(
        409,
        `${member.name} still has a balance of ${money(group.currency, fromPaise(balance))} in this group; settle up first`,
      )
    }
    group.members = group.members.filter((m) => m.id !== userId)
    const left = userId === me.id
    this.record(
      me,
      left ? 'MEMBER_LEFT' : 'MEMBER_REMOVED',
      left ? `${member.name} left "${group.name}"` : `${me.name} removed ${member.name} from "${group.name}"`,
      { groupId, involved: [userId] },
    )
    return group
  }

  // ------------------------------------------------------------------ expenses

  expenses(me: User, groupId: number, query: ExpenseQuery): Page<Expense> {
    this.group(me, groupId)
    const q = query.q?.toLowerCase()
    const matching = this.state.expenses
      .filter((e) => e.groupId === groupId && !e.deleted)
      .filter((e) => !q || e.description.toLowerCase().includes(q))
      .filter((e) => !query.category || e.category === query.category)
      .filter((e) => !query.paidBy || e.paidBy.id === query.paidBy)
      .filter((e) => (!query.from || e.date >= query.from) && (!query.to || e.date <= query.to))
      .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id)
    return page(matching, query.page, query.size)
  }

  expense(me: User, expenseId: number): Expense {
    const expense = this.state.expenses.find((e) => e.id === expenseId)
    if (!expense) throw new DemoError(404, `Expense ${expenseId} not found`)
    if (expense.groupId !== null) this.group(me, expense.groupId)
    else if (!involves(expense, me.id)) throw new DemoError(403, 'You are not part of this expense')
    return expense
  }

  addExpense(me: User, body: CreateExpenseRequest, fromRecurring: number | null = null): Expense {
    const group = body.groupId == null ? null : this.group(me, body.groupId)
    const expense = this.buildExpense(me, body, group, null)
    expense.recurringExpenseId = fromRecurring
    this.state.expenses.push(expense)
    this.record(me, 'EXPENSE_ADDED', `${me.name} added "${expense.description}" (${money(expense.currency, expense.amount)})`, {
      groupId: expense.groupId,
      expenseId: expense.id,
      involved: involvedIn(expense),
    })
    return expense
  }

  updateExpense(me: User, expenseId: number, body: UpdateExpenseRequest): Expense {
    const existing = this.expense(me, expenseId)
    if (existing.deleted) throw new DemoError(409, `Expense ${expenseId} is deleted; restore it before editing`)
    if (body.version !== existing.version) {
      throw new DemoError(
        409,
        `Expense ${expenseId} was changed by someone else (now version ${existing.version}); reload it and try again`,
      )
    }
    const group = existing.groupId === null ? null : this.group(me, existing.groupId)
    const updated = this.buildExpense(me, body, group, existing)
    const changes = diff(existing, updated)
    Object.assign(existing, updated)
    this.record(me, 'EXPENSE_UPDATED', `${me.name} updated "${existing.description}"`, {
      groupId: existing.groupId,
      expenseId,
      details: changes.length ? changes : ['No visible changes'],
      involved: involvedIn(existing),
    })
    return existing
  }

  deleteExpense(me: User, expenseId: number): void {
    const expense = this.expense(me, expenseId)
    if (expense.deleted) return
    expense.deleted = true
    this.record(me, 'EXPENSE_DELETED', `${me.name} deleted "${expense.description}" (${money(expense.currency, expense.amount)})`, {
      groupId: expense.groupId,
      expenseId,
      involved: involvedIn(expense),
    })
  }

  restoreExpense(me: User, expenseId: number): Expense {
    const expense = this.expense(me, expenseId)
    if (!expense.deleted) throw new DemoError(409, `Expense ${expenseId} is not deleted`)
    expense.deleted = false
    this.record(me, 'EXPENSE_RESTORED', `${me.name} restored "${expense.description}"`, {
      groupId: expense.groupId,
      expenseId,
      involved: involvedIn(expense),
    })
    return expense
  }

  // ------------------------------------------------------------------ balances and settlements

  balances(me: User, groupId: number): MemberBalance[] {
    const group = this.group(me, groupId)
    return [...this.netBalances(group)].map(([userId, paise]) => ({
      userId,
      name: this.user(userId).name,
      netBalance: fromPaise(paise),
      currency: group.currency,
    }))
  }

  simplifiedDebts(me: User, groupId: number): Transfer[] {
    const group = this.group(me, groupId)
    return simplify(this.netBalances(group)).map((t) => ({
      fromUserId: t.from,
      fromName: this.user(t.from).name,
      toUserId: t.to,
      toName: this.user(t.to).name,
      amount: fromPaise(t.paise),
      currency: group.currency,
    }))
  }

  addSettlement(me: User, body: CreateSettlementRequest): Settlement {
    if (body.fromUserId === body.toUserId) throw new DemoError(400, 'A settlement needs two different users')
    if (body.fromUserId !== me.id && body.toUserId !== me.id) {
      throw new DemoError(403, 'You can only record settlements you paid or received')
    }
    if (!(body.amount > 0)) throw new DemoError(400, 'amount: must be greater than 0')
    const group = body.groupId == null ? null : this.group(me, body.groupId)
    for (const id of [body.fromUserId, body.toUserId]) {
      if (group && !isMember(group, id)) throw new DemoError(400, `User ${id} is not a member of group ${group.id}`)
    }
    const settlement: Settlement = {
      id: this.id(),
      fromUserId: body.fromUserId,
      fromName: this.user(body.fromUserId).name,
      toUserId: body.toUserId,
      toName: this.user(body.toUserId).name,
      amount: toPaise(body.amount) / 100,
      currency: group?.currency ?? body.currency ?? 'INR',
      groupId: group?.id ?? null,
      date: body.date ?? today(this.now()),
      note: body.note?.trim() || null,
      version: 0,
      deleted: false,
    }
    this.state.settlements.push(settlement)
    this.record(
      me,
      'SETTLEMENT_ADDED',
      `${settlement.fromName} paid ${settlement.toName} ${money(settlement.currency, settlement.amount)}`,
      { groupId: settlement.groupId, settlementId: settlement.id, involved: [settlement.fromUserId, settlement.toUserId] },
    )
    return settlement
  }

  friends(me: User): FriendBalances {
    const items = this.friendItems(me.id)
    const byFriend = new Map<number, FriendBalanceItem[]>()
    for (const item of items) byFriend.set(item.friendId, [...(byFriend.get(item.friendId) ?? []), item.item])
    const friends = [...byFriend].map(([friendId, breakdown]) => this.friendBalance(friendId, breakdown))
    const largest = (f: FriendBalance) => Math.max(...f.balances.map((b) => Math.abs(b.amount)))
    friends.sort((a, b) => largest(b) - largest(a) || a.name.localeCompare(b.name))
    return { totals: totals(friends), friends }
  }

  settleUp(me: User, friendId: number): SettleUpResult {
    const friend = this.user(friendId)
    const open = this.friendItems(me.id).filter((i) => i.friendId === friendId)
    if (open.length === 0) throw new DemoError(409, `You and ${friend.name} are already settled up`)
    const settlements = open.map(({ item }) => {
      const friendOwes = item.amount > 0
      return this.addSettlement(me, {
        fromUserId: friendOwes ? friendId : me.id,
        toUserId: friendOwes ? me.id : friendId,
        amount: Math.abs(item.amount),
        currency: item.currency,
        groupId: item.groupId ?? undefined,
      })
    })
    return { settlements, balance: this.friendBalance(friendId, []) }
  }

  // ------------------------------------------------------------------ activity, recurring, reports

  activity(me: User, pageNumber: number, size: number, groupId?: number): Page<Activity> {
    if (groupId !== undefined) this.group(me, groupId)
    const myGroups = new Set(this.groupsOf(me).map((g) => g.id))
    const visible = this.state.activity
      .filter((a) =>
        groupId !== undefined
          ? a.groupId === groupId
          : (a.groupId !== null && myGroups.has(a.groupId)) || a.involved.includes(me.id),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
      .map(({ involved: _involved, ...entry }) => entry)
    return page(visible, pageNumber, size)
  }

  recurringOf(me: User): RecurringExpense[] {
    const myGroups = new Set(this.groupsOf(me).map((g) => g.id))
    return this.state.recurring
      .filter((r) => (r.groupId !== null && myGroups.has(r.groupId)) || r.shares.some((s) => s.userId === me.id))
      .sort((a, b) => Number(b.active) - Number(a.active) || b.id - a.id)
  }

  addRecurring(me: User, body: CreateRecurringExpenseRequest): RecurringExpense {
    const day = today(this.now())
    if (body.startDate < day) throw new DemoError(400, 'startDate cannot be in the past')
    if (body.endDate && body.endDate < body.startDate) throw new DemoError(400, 'endDate cannot be before startDate')
    const group = body.groupId == null ? null : this.group(me, body.groupId)
    // Validates the split the same way a single expense would be
    this.buildExpense(me, { ...body, date: body.startDate }, group, null)
    const template: RecurringExpense = {
      id: this.id(),
      description: body.description.trim(),
      amount: body.amount,
      currency: group?.currency ?? body.currency ?? 'INR',
      paidById: body.paidById,
      groupId: group?.id ?? null,
      splitType: body.splitType,
      category: body.category ?? 'GENERAL',
      notes: body.notes?.trim() || null,
      shares: body.shares,
      frequency: body.frequency,
      startDate: body.startDate,
      endDate: body.endDate ?? null,
      nextDate: body.startDate,
      active: true,
      stoppedReason: null,
      createdById: me.id,
      createdAt: this.timestamp(),
    }
    this.state.recurring.push(template)
    this.record(
      me,
      'RECURRING_CREATED',
      `${me.name} set up "${template.description}" (${money(template.currency, template.amount)}) to repeat ${template.frequency.toLowerCase()} from ${template.startDate}`,
      { groupId: template.groupId, involved: template.shares.map((s) => s.userId) },
    )
    this.runRecurring(me)
    return template
  }

  stopRecurring(me: User, id: number): void {
    const template = this.recurringOf(me).find((r) => r.id === id)
    if (!template) throw new DemoError(404, `Recurring expense ${id} not found`)
    if (!template.active) return
    Object.assign(template, { active: false, nextDate: null, stoppedReason: `Stopped by ${me.name}` })
    this.record(me, 'RECURRING_STOPPED', `${me.name} stopped the recurring "${template.description}"`, {
      groupId: template.groupId,
      involved: template.shares.map((s) => s.userId),
    })
  }

  /** Adds every occurrence that is due by today, like the backend's hourly job. */
  runRecurring(me: User): void {
    const day = today(this.now())
    for (const template of this.state.recurring) {
      while (template.active && template.nextDate && template.nextDate <= day) {
        if (template.endDate && template.nextDate > template.endDate) {
          Object.assign(template, { active: false, nextDate: null, stoppedReason: 'Ended' })
          break
        }
        const payer = this.user(template.paidById)
        this.addExpense(
          payer.id === me.id ? me : payer,
          { ...template, groupId: template.groupId ?? undefined, notes: template.notes ?? undefined, date: template.nextDate },
          template.id,
        )
        template.nextDate = nextOccurrence(template.startDate, template.nextDate, template.frequency)
      }
    }
  }

  report(me: User, groupId: number, from?: string, to?: string): GroupReport {
    const group = this.group(me, groupId)
    const expenses = this.state.expenses.filter(
      (e) => e.groupId === groupId && !e.deleted && (!from || e.date >= from) && (!to || e.date <= to),
    )
    const byCategory = new Map<Expense['category'], { total: number; count: number }>()
    for (const e of expenses) {
      const c = byCategory.get(e.category) ?? { total: 0, count: 0 }
      byCategory.set(e.category, { total: c.total + toPaise(e.amount), count: c.count + 1 })
    }
    return {
      groupId,
      groupName: group.name,
      from: from ?? null,
      to: to ?? null,
      currency: group.currency,
      expenseCount: expenses.length,
      totalSpent: fromPaise(expenses.reduce((sum, e) => sum + toPaise(e.amount), 0)),
      byCategory: [...byCategory]
        .map(([category, c]) => ({ category, total: fromPaise(c.total), count: c.count }))
        .sort((a, b) => b.total - a.total),
      byMember: group.members.map((m) => {
        const paid = expenses.filter((e) => e.paidBy.id === m.id).reduce((sum, e) => sum + toPaise(e.amount), 0)
        const share = expenses
          .flatMap((e) => e.shares)
          .filter((s) => s.userId === m.id)
          .reduce((sum, s) => sum + toPaise(s.amount), 0)
        return { userId: m.id, name: m.name, paid: fromPaise(paid), share: fromPaise(share), net: fromPaise(paid - share) }
      }),
    }
  }

  csv(me: User, groupId: number, from?: string, to?: string): { name: string; body: string } {
    const group = this.group(me, groupId)
    const rows = this.state.expenses
      .filter((e) => e.groupId === groupId && !e.deleted && (!from || e.date >= from) && (!to || e.date <= to))
      .sort((a, b) => a.date.localeCompare(b.date) || a.id - b.id)
      .map((e) =>
        [e.date, e.description, e.category, e.paidBy.name, plain(e.amount), e.currency, e.shares.map((s) => `${s.name} ${plain(s.amount)}`).join('; ')]
          .map(csvCell)
          .join(','),
      )
    const body = ['Date,Description,Category,Paid by,Amount,Currency,Shares', ...rows].join('\r\n') + '\r\n'
    return { name: `${group.name.replace(/[^\w-]+/g, '-')}-expenses.csv`, body }
  }

  // ------------------------------------------------------------------ internals

  private buildExpense(
    me: User,
    body: CreateExpenseRequest | UpdateExpenseRequest,
    group: Group | null,
    existing: Expense | null,
  ): Expense {
    const description = body.description?.trim() ?? ''
    if (!description) throw new DemoError(400, 'description: must not be blank')
    const people = [body.paidById, ...body.shares.map((s) => s.userId)]
    for (const id of people) {
      if (group && !isMember(group, id)) throw new DemoError(400, `User ${id} is not a member of group ${group.id}`)
      if (!group) this.user(id)
    }
    if (!group && !people.includes(me.id)) {
      throw new DemoError(403, 'You can only record non-group expenses that you paid for or share in')
    }
    const split = computeSplit(
      body.splitType,
      String(body.amount),
      body.shares.map((s) => ({ userId: s.userId, value: s.value == null ? null : String(s.value) })),
      (id) => this.user(id).name,
    )
    if (!split.ok) throw new DemoError(400, split.error)
    const paidBy = this.user(body.paidById)
    return {
      id: existing?.id ?? this.id(),
      description,
      amount: body.amount,
      currency: group?.currency ?? body.currency ?? existing?.currency ?? 'INR',
      paidBy: { id: paidBy.id, name: paidBy.name },
      groupId: group?.id ?? null,
      date: body.date ?? existing?.date ?? today(this.now()),
      splitType: body.splitType,
      category: body.category ?? 'GENERAL',
      notes: body.notes?.trim() || null,
      shares: body.shares.map((s) => ({
        userId: s.userId,
        name: this.user(s.userId).name,
        amount: Number(paiseToString(split.owed.get(s.userId)!)),
        value: body.splitType === 'EQUAL' || s.value == null ? null : Number(s.value),
      })),
      version: existing ? existing.version + 1 : 0,
      createdById: existing?.createdById ?? me.id,
      createdAt: existing?.createdAt ?? this.timestamp(),
      updatedById: existing ? me.id : null,
      updatedAt: existing ? this.timestamp() : null,
      deleted: false,
      recurringExpenseId: existing?.recurringExpenseId ?? null,
    }
  }

  /** Net balance in paise per member (positive = is owed money), from expenses and payments in the group. */
  private netBalances(group: Group): Map<number, number> {
    return net(
      group.members.map((m) => m.id),
      this.state.expenses.filter((e) => e.groupId === group.id && !e.deleted),
      this.state.settlements.filter((s) => s.groupId === group.id && !s.deleted),
    )
  }

  /**
   * What the user and each friend owe each other: per group from the group's simplified debts (so it matches the
   * group screen), and pair by pair outside groups. Positive = the friend owes the user.
   */
  private friendItems(userId: number): { friendId: number; item: FriendBalanceItem }[] {
    const items: { friendId: number; item: FriendBalanceItem }[] = []
    for (const group of this.state.groups) {
      for (const t of simplify(this.netBalances(group))) {
        const friendId = t.from === userId ? t.to : t.to === userId ? t.from : null
        if (friendId === null) continue
        const amount = fromPaise(t.from === userId ? -t.paise : t.paise)
        items.push({ friendId, item: { groupId: group.id, groupName: group.name, currency: group.currency, amount } })
      }
    }
    const direct = new Map<string, number>()
    const add = (friendId: number, currency: string, paise: number) => {
      const key = `${friendId}|${currency}`
      direct.set(key, (direct.get(key) ?? 0) + paise)
    }
    for (const e of this.state.expenses.filter((x) => x.groupId === null && !x.deleted)) {
      for (const s of e.shares) {
        if (s.userId === e.paidBy.id) continue
        if (e.paidBy.id === userId) add(s.userId, e.currency, toPaise(s.amount))
        else if (s.userId === userId) add(e.paidBy.id, e.currency, -toPaise(s.amount))
      }
    }
    for (const s of this.state.settlements.filter((x) => x.groupId === null && !x.deleted)) {
      if (s.fromUserId === userId) add(s.toUserId, s.currency, toPaise(s.amount))
      else if (s.toUserId === userId) add(s.fromUserId, s.currency, -toPaise(s.amount))
    }
    for (const [key, paise] of direct) {
      const [friendId, currency] = key.split('|')
      if (paise !== 0) {
        items.push({
          friendId: Number(friendId),
          item: { groupId: null, groupName: null, currency: currency!, amount: fromPaise(paise) },
        })
      }
    }
    return items
  }

  private friendBalance(friendId: number, breakdown: FriendBalanceItem[]): FriendBalance {
    const byCurrency = new Map<string, number>()
    for (const item of breakdown) byCurrency.set(item.currency, (byCurrency.get(item.currency) ?? 0) + toPaise(item.amount))
    return {
      userId: friendId,
      name: this.user(friendId).name,
      balances: [...byCurrency]
        .filter(([, paise]) => paise !== 0)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([currency, paise]) => ({ currency, amount: fromPaise(paise) })),
      breakdown,
    }
  }

  private record(
    actor: User,
    type: Activity['type'],
    summary: string,
    fields: { groupId?: number | null; expenseId?: number; settlementId?: number; details?: string[]; involved?: number[] },
  ): void {
    this.state.activity.push({
      id: this.id(),
      type,
      actorId: actor.id,
      actorName: actor.name,
      groupId: fields.groupId ?? null,
      expenseId: fields.expenseId ?? null,
      settlementId: fields.settlementId ?? null,
      summary,
      details: fields.details ?? [],
      createdAt: this.timestamp(),
      involved: [actor.id, ...(fields.involved ?? [])],
    })
  }

  private user(id: number): DemoUser {
    const user = this.state.users.find((u) => u.id === id)
    if (!user) throw new DemoError(404, `User ${id} not found`)
    return user
  }

  private id(): number {
    return this.state.nextId++
  }

  private timestamp(): string {
    return this.now().toISOString()
  }
}

function publicUser({ id, name, email }: User): User {
  return { id, name, email }
}

function isMember(group: Group, userId: number): boolean {
  return group.members.some((m) => m.id === userId)
}

function involvedIn(expense: Expense): number[] {
  return [expense.paidBy.id, ...expense.shares.map((s) => s.userId)]
}

function involves(expense: Expense, userId: number): boolean {
  return involvedIn(expense).includes(userId)
}

function net(memberIds: number[], expenses: Expense[], settlements: Settlement[]): Map<number, number> {
  const balances = new Map<number, number>(memberIds.map((id) => [id, 0]))
  const add = (id: number, paise: number) => balances.set(id, (balances.get(id) ?? 0) + paise)
  for (const e of expenses) {
    add(e.paidBy.id, toPaise(e.amount))
    for (const s of e.shares) add(s.userId, -toPaise(s.amount))
  }
  for (const s of settlements) {
    add(s.fromUserId, toPaise(s.amount))
    add(s.toUserId, -toPaise(s.amount))
  }
  return balances
}

/** Largest debtor pays largest creditor until everyone is even (ties by user id), as the backend's DebtSimplifier. */
export function simplify(balances: Map<number, number>): { from: number; to: number; paise: number }[] {
  const largestFirst = (a: { id: number; paise: number }, b: { id: number; paise: number }) =>
    b.paise - a.paise || a.id - b.id
  const creditors = [...balances].filter(([, p]) => p > 0).map(([id, paise]) => ({ id, paise }))
  const debtors = [...balances].filter(([, p]) => p < 0).map(([id, paise]) => ({ id, paise: -paise }))
  const transfers = []
  for (;;) {
    creditors.sort(largestFirst)
    debtors.sort(largestFirst)
    const creditor = creditors[0]
    const debtor = debtors[0]
    if (!creditor || !debtor) break
    const paise = Math.min(creditor.paise, debtor.paise)
    transfers.push({ from: debtor.id, to: creditor.id, paise })
    creditor.paise -= paise
    debtor.paise -= paise
    if (creditor.paise === 0) creditors.shift()
    if (debtor.paise === 0) debtors.shift()
  }
  return transfers
}

function totals(friends: FriendBalance[]): FriendTotals[] {
  const byCurrency = new Map<string, { owed: number; owe: number }>()
  for (const { currency, amount } of friends.flatMap((f) => f.balances)) {
    const t = byCurrency.get(currency) ?? { owed: 0, owe: 0 }
    if (amount > 0) t.owed += toPaise(amount)
    else t.owe -= toPaise(amount)
    byCurrency.set(currency, t)
  }
  return [...byCurrency]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([currency, t]) => ({
      currency,
      youAreOwed: fromPaise(t.owed),
      youOwe: fromPaise(t.owe),
      net: fromPaise(t.owed - t.owe),
    }))
}

function page<T>(items: T[], pageNumber: number, size: number): Page<T> {
  const totalPages = Math.ceil(items.length / size)
  return {
    content: items.slice(pageNumber * size, pageNumber * size + size),
    page: pageNumber,
    size,
    totalElements: items.length,
    totalPages,
    last: pageNumber >= totalPages - 1,
  }
}

/** The lines of an expense edit, worded like the backend's ExpenseDiff. */
function diff(before: Expense, after: Expense): string[] {
  const changes: string[] = []
  if (before.description !== after.description) changes.push(`Description: "${before.description}" -> "${after.description}"`)
  if (before.amount !== after.amount) changes.push(`Amount: ${plain(before.amount)} -> ${plain(after.amount)}`)
  if (before.paidBy.id !== after.paidBy.id) changes.push(`Paid by: ${before.paidBy.name} -> ${after.paidBy.name}`)
  if (before.date !== after.date) changes.push(`Date: ${before.date} -> ${after.date}`)
  if (before.splitType !== after.splitType) changes.push(`Split: ${before.splitType} -> ${after.splitType}`)
  if (before.category !== after.category) changes.push(`Category: ${before.category} -> ${after.category}`)
  if (before.notes !== after.notes) {
    changes.push(before.notes === null ? 'Notes added' : after.notes === null ? 'Notes removed' : 'Notes changed')
  }
  // Person by person, in user id order
  const ids = [...new Set([...before.shares, ...after.shares].map((s) => s.userId))].sort((a, b) => a - b)
  for (const id of ids) {
    const old = before.shares.find((s) => s.userId === id)
    const now = after.shares.find((s) => s.userId === id)
    if (!old && now) changes.push(`${now.name} added to the split (${plain(now.amount)})`)
    else if (old && !now) changes.push(`${old.name} removed from the split (was ${plain(old.amount)})`)
    else if (old && now && old.amount !== now.amount) {
      changes.push(`${now.name}'s share: ${plain(old.amount)} -> ${plain(now.amount)}`)
    }
  }
  return changes
}

/** The occurrence after `current`, counted from the start date so the 31st stays the last day of short months. */
export function nextOccurrence(start: string, current: string, frequency: RecurringExpense['frequency']): string {
  const [y, m, d] = start.split('-').map(Number) as [number, number, number]
  const [cy, cm, cd] = current.split('-').map(Number) as [number, number, number]
  if (frequency === 'WEEKLY') {
    const next = new Date(Date.UTC(cy, cm - 1, cd + 7))
    return next.toISOString().slice(0, 10)
  }
  const months = frequency === 'MONTHLY' ? (cy - y) * 12 + (cm - m) + 1 : (cy - y + 1) * 12
  const target = new Date(Date.UTC(y, m - 1 + months, 1))
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate()
  target.setUTCDate(Math.min(d, lastDay))
  return target.toISOString().slice(0, 10)
}

function csvCell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value
}
