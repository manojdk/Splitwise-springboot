import { describe, expect, it } from 'vitest'
import type { User } from '../api/types'
import { DemoBackend, DemoError, nextOccurrence } from './backend'
import { DEMO_EMAIL, DEMO_PASSWORD, seedState } from './seed'

const NOW = new Date(2026, 9, 13, 12, 0)

function demo() {
  const backend = new DemoBackend(seedState(NOW), () => NOW)
  const who = (email: string): User => backend.caller(`Bearer ${backend.login(email, email === DEMO_EMAIL ? DEMO_PASSWORD : 'demo-only-password').accessToken}`)
  return { backend, alice: who(DEMO_EMAIL), bob: who('bob@example.com'), carol: who('carol@example.com'), dan: who('dan@example.com') }
}

function failure(action: () => unknown): { status: number; message: string } {
  try {
    action()
  } catch (error) {
    if (error instanceof DemoError) return { status: error.status, message: error.message }
    throw error
  }
  throw new Error('expected a DemoError')
}

/** A user's balance with a friend per currency (positive = the friend owes them). */
function balanceWith(backend: DemoBackend, me: User, friend: User): Record<string, number> {
  const entry = backend.friends(me).friends.find((f) => f.userId === friend.id)
  return Object.fromEntries((entry?.balances ?? []).map((b) => [b.currency, b.amount]))
}

describe('demo backend', () => {
  it('signs in the demo account and refuses a wrong password or a made-up token', () => {
    const { backend } = demo()
    expect(backend.login('  Alice@Example.com ', DEMO_PASSWORD).accessToken).toMatch(/^demo-token-\d+$/)
    expect(failure(() => backend.login(DEMO_EMAIL, 'wrong'))).toEqual({ status: 401, message: 'Invalid email or password' })
    expect(failure(() => backend.caller('Bearer demo-token-999')).status).toBe(401)
    expect(failure(() => backend.caller(null)).status).toBe(401)
  })

  it('starts with sample groups, expenses and a feed that tells their story', () => {
    const { backend, alice, dan } = demo()
    expect(backend.groupsOf(alice).map((g) => g.name)).toEqual(['Goa Trip', 'Flat 4B', 'Paris Weekend'])
    expect(backend.groupsOf(dan).map((g) => g.name)).toEqual(['Flat 4B'])

    const feed = backend.activity(alice, 0, 50).content
    expect(feed[0]!.summary).toBe('Carol White stopped the recurring "Netflix"')
    const edit = feed.find((a) => a.type === 'EXPENSE_UPDATED')!
    expect(edit.summary).toBe('Bob Jones updated "Beach shack dinner"')
    expect(edit.details).toEqual([
      'Amount: 1000.00 -> 1200.00',
      "Alice Smith's share: 500.00 -> 400.00",
      "Bob Jones's share: 500.00 -> 400.00",
      'Carol White added to the split (400.00)',
    ])
    // Dan is not in Paris, so he does not see it
    expect(backend.activity(dan, 0, 50).content.some((a) => a.summary.includes('Louvre'))).toBe(false)
  })

  it('keeps friend balances consistent: each pair agrees, and they add up to each group', () => {
    const { backend, alice, bob, carol, dan } = demo()
    const people = [alice, bob, carol, dan]
    for (const me of people) {
      for (const friend of people.filter((p) => p !== me)) {
        const mine = balanceWith(backend, me, friend)
        const theirs = balanceWith(backend, friend, me)
        for (const currency of new Set([...Object.keys(mine), ...Object.keys(theirs)])) {
          expect(mine[currency] ?? 0).toBeCloseTo(-(theirs[currency] ?? 0), 2)
        }
      }
    }
    // Alice's friend balances in Goa add up to her net balance in Goa
    const goa = backend.groupsOf(alice).find((g) => g.name === 'Goa Trip')!
    const net = backend.balances(alice, goa.id).find((b) => b.userId === alice.id)!.netBalance
    const fromFriends = backend
      .friends(alice)
      .friends.flatMap((f) => f.breakdown)
      .filter((i) => i.groupId === goa.id)
      .reduce((sum, i) => sum + i.amount, 0)
    expect(fromFriends).toBeCloseTo(net, 2)
  })

  it('moves balances when an expense is added, and settling up clears them', () => {
    const { backend, alice, dan } = demo()
    const flat = backend.groupsOf(alice).find((g) => g.name === 'Flat 4B')!
    // Group balances come from the group's simplified debts, so who owes Alice may shift; her total moves by 300
    const owedToAlice = () => backend.friends(alice).totals.find((t) => t.currency === 'INR')!.net
    const before = owedToAlice()

    backend.addExpense(alice, {
      description: 'Plumber',
      amount: 600,
      groupId: flat.id,
      paidById: alice.id,
      splitType: 'EQUAL',
      shares: [{ userId: alice.id }, { userId: dan.id }],
    })
    expect(owedToAlice()).toBeCloseTo(before + 300, 2)

    const result = backend.settleUp(alice, dan.id)
    expect(result.settlements.length).toBeGreaterThan(0)
    expect(result.balance.balances).toEqual([])
    expect(backend.friends(alice).friends.some((f) => f.userId === dan.id)).toBe(false)
    expect(failure(() => backend.settleUp(alice, dan.id))).toEqual({
      status: 409,
      message: 'You and Dan Brown are already settled up',
    })
  })

  it('applies the API rules: membership, split validation, edits by version, removing a member who owes', () => {
    const { backend, alice, bob, dan } = demo()
    const goa = backend.groupsOf(alice).find((g) => g.name === 'Goa Trip')!
    expect(failure(() => backend.group(dan, goa.id))).toEqual({ status: 403, message: `You are not a member of group ${goa.id}` })
    expect(failure(() => backend.addExpense(alice, { description: 'X', amount: 100, groupId: goa.id, paidById: alice.id, splitType: 'EQUAL', shares: [{ userId: dan.id }] })).status).toBe(400)
    expect(failure(() => backend.addExpense(alice, { description: 'X', amount: 100, groupId: goa.id, paidById: alice.id, splitType: 'PERCENTAGE', shares: [{ userId: alice.id, value: 50 }, { userId: bob.id, value: 40 }] })).status).toBe(400)

    const villa = backend.expenses(alice, goa.id, { page: 0, size: 20, q: 'villa' }).content[0]!
    const edit = { ...villa, paidById: villa.paidBy.id, notes: villa.notes ?? undefined, shares: villa.shares.map((s) => ({ userId: s.userId, value: s.value ?? undefined })) }
    backend.updateExpense(alice, villa.id, { ...edit, description: 'Villa' })
    expect(failure(() => backend.updateExpense(bob, villa.id, { ...edit, description: 'Stale' })).status).toBe(409)

    expect(failure(() => backend.removeMember(alice, goa.id, bob.id)).message).toMatch(/^Bob Jones still has a balance of INR -?\d+\.\d\d in this group; settle up first$/)
  })

  it('adds a recurring expense now when it starts today, then on schedule', () => {
    const { backend, alice } = demo()
    const goa = backend.groupsOf(alice).find((g) => g.name === 'Goa Trip')!
    const template = backend.addRecurring(alice, {
      description: 'Gym',
      amount: 1500,
      groupId: goa.id,
      paidById: alice.id,
      splitType: 'EQUAL',
      frequency: 'MONTHLY',
      startDate: '2026-10-13',
      shares: [{ userId: alice.id }],
    })
    expect(template.nextDate).toBe('2026-11-13')
    const gym = backend.expenses(alice, goa.id, { page: 0, size: 20, q: 'gym' }).content
    expect(gym).toHaveLength(1)
    expect(gym[0]).toMatchObject({ date: '2026-10-13', recurringExpenseId: template.id })
    expect(failure(() => backend.addRecurring(alice, { ...template, groupId: goa.id, notes: undefined, endDate: undefined, startDate: '2026-10-12' })).message).toBe('startDate cannot be in the past')
  })

  it('counts months from the start date, so the 31st stays the end of short months', () => {
    expect(nextOccurrence('2026-01-31', '2026-01-31', 'MONTHLY')).toBe('2026-02-28')
    expect(nextOccurrence('2026-01-31', '2026-02-28', 'MONTHLY')).toBe('2026-03-31')
    expect(nextOccurrence('2024-02-29', '2024-02-29', 'YEARLY')).toBe('2025-02-28')
    expect(nextOccurrence('2026-12-28', '2026-12-28', 'WEEKLY')).toBe('2027-01-04')
  })

  it('reports and exports a group', () => {
    const { backend, alice } = demo()
    const goa = backend.groupsOf(alice).find((g) => g.name === 'Goa Trip')!
    const report = backend.report(alice, goa.id)
    // Netflix started 3 days ago, so its first month is already in
    expect(report.totalSpent).toBe(9000 + 1800 + 1200 + 4500 + 649)
    expect(report.byMember.reduce((sum, m) => sum + m.net, 0)).toBeCloseTo(0, 2)
    const csv = backend.csv(alice, goa.id)
    expect(csv.name).toBe('Goa-Trip-expenses.csv')
    expect(csv.body.split('\r\n')[1]).toBe(`2026-09-17,Villa (3 nights),RENT,Alice Smith,9000.00,INR,Alice Smith 4500.00; Bob Jones 2250.00; Carol White 2250.00`)
  })
})
