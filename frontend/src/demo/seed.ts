import type { CreateExpenseRequest, User } from '../api/types'
import { today } from '../lib/dates'
import { DemoBackend, emptyState, type DemoState } from './backend'

export const DEMO_EMAIL = 'alice@example.com'
export const DEMO_PASSWORD = 'password123'

const DAY = 86_400_000

/**
 * The sample data a visitor starts with, made through the same operations they use (so balances and the activity
 * feed are what the app would really show), dated relative to today so it always looks recent.
 */
export function seedState(now: Date = new Date()): DemoState {
  let clock = new Date(now.getTime() - 30 * DAY)
  const backend = new DemoBackend(emptyState(), () => clock)
  /** Moves the clock to `days` ago (at a set hour, so the feed reads naturally) and returns that date. */
  const at = (days: number, hour = 19) => {
    clock = new Date(now.getTime() - days * DAY)
    clock.setHours(hour, 0, 0, 0)
    return today(clock)
  }

  const person = (name: string, email: string) =>
    backend.register({ name, email, password: email === DEMO_EMAIL ? DEMO_PASSWORD : 'demo-only-password' })
  const alice = person('Alice Smith', DEMO_EMAIL)
  const bob = person('Bob Jones', 'bob@example.com')
  const carol = person('Carol White', 'carol@example.com')
  const dan = person('Dan Brown', 'dan@example.com')

  const spend = (by: User, groupId: number, days: number, fields: Omit<CreateExpenseRequest, 'groupId' | 'date'>) =>
    backend.addExpense(by, { ...fields, groupId, date: at(days) })

  at(28, 10)
  const goa = backend.createGroup(alice, { name: 'Goa Trip', currency: 'INR', memberIds: [bob.id, carol.id] })
  spend(alice, goa.id, 26, {
    description: 'Villa (3 nights)',
    amount: 9000,
    paidById: alice.id,
    splitType: 'SHARES',
    category: 'RENT',
    notes: 'Alice and her partner took the big room',
    shares: [
      { userId: alice.id, value: 2 },
      { userId: bob.id, value: 1 },
      { userId: carol.id, value: 1 },
    ],
  })
  spend(carol, goa.id, 25, {
    description: 'Scooter rental',
    amount: 1800,
    paidById: carol.id,
    splitType: 'EXACT',
    category: 'TRANSPORT',
    shares: [
      { userId: alice.id, value: 600 },
      { userId: bob.id, value: 700 },
      { userId: carol.id, value: 500 },
    ],
  })
  const dinner = spend(bob, goa.id, 25, {
    description: 'Beach shack dinner',
    amount: 1000,
    paidById: bob.id,
    splitType: 'EQUAL',
    category: 'FOOD_AND_DRINK',
    shares: [{ userId: alice.id }, { userId: bob.id }],
  })
  at(24, 9)
  backend.updateExpense(bob, dinner.id, {
    description: 'Beach shack dinner',
    amount: 1200,
    paidById: bob.id,
    splitType: 'EQUAL',
    category: 'FOOD_AND_DRINK',
    date: dinner.date,
    version: dinner.version,
    shares: [{ userId: alice.id }, { userId: bob.id }, { userId: carol.id }],
  })
  spend(alice, goa.id, 24, {
    description: 'Parasailing',
    amount: 4500,
    paidById: alice.id,
    splitType: 'PERCENTAGE',
    category: 'ENTERTAINMENT',
    shares: [
      { userId: alice.id, value: 40 },
      { userId: bob.id, value: 30 },
      { userId: carol.id, value: 30 },
    ],
  })
  at(20, 12)
  backend.addSettlement(bob, { fromUserId: bob.id, toUserId: alice.id, amount: 2000, groupId: goa.id })

  at(18, 11)
  const flat = backend.createGroup(dan, { name: 'Flat 4B', currency: 'INR', memberIds: [alice.id, bob.id] })
  spend(dan, flat.id, 15, {
    description: 'Electricity bill',
    amount: 2340,
    paidById: dan.id,
    splitType: 'EQUAL',
    category: 'UTILITIES',
    shares: [{ userId: alice.id }, { userId: bob.id }, { userId: dan.id }],
  })
  spend(alice, flat.id, 9, {
    description: 'Groceries',
    amount: 1875,
    paidById: alice.id,
    splitType: 'ADJUSTMENT',
    category: 'GROCERIES',
    notes: 'Bob added the snacks for his party',
    shares: [
      { userId: alice.id, value: 0 },
      { userId: bob.id, value: 300 },
      { userId: dan.id, value: 0 },
    ],
  })
  at(6, 20)
  backend.addRecurring(dan, {
    description: 'Wifi',
    amount: 999,
    paidById: dan.id,
    groupId: flat.id,
    splitType: 'EQUAL',
    category: 'UTILITIES',
    frequency: 'MONTHLY',
    startDate: at(6, 20),
    shares: [{ userId: alice.id }, { userId: bob.id }, { userId: dan.id }],
  })

  at(12, 15)
  const paris = backend.createGroup(carol, { name: 'Paris Weekend', currency: 'EUR', memberIds: [alice.id] })
  spend(carol, paris.id, 10, {
    description: 'Louvre tickets',
    amount: 44,
    paidById: carol.id,
    splitType: 'EQUAL',
    category: 'ENTERTAINMENT',
    shares: [{ userId: alice.id }, { userId: carol.id }],
  })
  spend(alice, paris.id, 9, {
    description: 'Croissants and coffee',
    amount: 18.4,
    paidById: alice.id,
    splitType: 'EQUAL',
    category: 'FOOD_AND_DRINK',
    shares: [{ userId: alice.id }, { userId: carol.id }],
  })

  at(3, 18)
  const netflix = backend.addRecurring(carol, {
    description: 'Netflix',
    amount: 649,
    paidById: carol.id,
    groupId: goa.id,
    splitType: 'EQUAL',
    category: 'ENTERTAINMENT',
    frequency: 'MONTHLY',
    startDate: at(3, 18),
    shares: [{ userId: alice.id }, { userId: carol.id }],
  })
  at(1, 21)
  backend.stopRecurring(carol, netflix.id)

  // A non-group expense between Alice and Dan, in another currency
  at(2, 13)
  backend.addExpense(dan, {
    description: 'Concert tickets',
    amount: 120,
    currency: 'USD',
    paidById: dan.id,
    splitType: 'EQUAL',
    category: 'ENTERTAINMENT',
    date: at(2, 13),
    shares: [{ userId: alice.id }, { userId: dan.id }],
  })

  return backend.state
}
