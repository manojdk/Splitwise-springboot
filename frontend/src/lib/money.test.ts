import { describe, expect, it } from 'vitest'
import { balancesByGroup } from './balances'
import { formatMoney, isZero, roundCents } from './money'

describe('formatMoney', () => {
  it('formats with the currency symbol and two decimals', () => {
    expect(formatMoney(1200, 'INR')).toBe('₹1,200.00')
    expect(formatMoney(45.5, 'EUR')).toBe('€45.50')
  })

  it('shows a sign only when asked, and never on zero', () => {
    expect(formatMoney(10, 'USD', { signed: true })).toBe('+$10.00')
    expect(formatMoney(-10, 'USD', { signed: true })).toBe('-$10.00')
    expect(formatMoney(-10, 'USD')).toBe('-$10.00')
    expect(formatMoney(0, 'USD', { signed: true })).toBe('$0.00')
    expect(formatMoney(-0.001, 'USD', { signed: true })).toBe('$0.00')
  })

  it('treats float noise below half a cent as zero', () => {
    expect(isZero(0.1 + 0.2 - 0.3)).toBe(true)
    expect(isZero(0.01)).toBe(false)
    expect(roundCents(0.1 + 0.2)).toBe(0.3)
  })
})

describe('balancesByGroup', () => {
  it("adds up the user's balance per group across friends, skipping outside-group balances", () => {
    const balances = balancesByGroup([
      {
        userId: 2,
        name: 'Bob',
        balances: [],
        breakdown: [
          { groupId: 10, groupName: 'Goa', currency: 'INR', amount: 3000 },
          { groupId: null, groupName: null, currency: 'USD', amount: -10 },
        ],
      },
      {
        userId: 3,
        name: 'Carol',
        balances: [],
        breakdown: [
          { groupId: 10, groupName: 'Goa', currency: 'INR', amount: -200.1 },
          { groupId: 11, groupName: 'Paris', currency: 'EUR', amount: 45 },
        ],
      },
    ])

    expect(balances.get(10)).toEqual({ currency: 'INR', amount: 2799.9 })
    expect(balances.get(11)).toEqual({ currency: 'EUR', amount: 45 })
    expect(balances.size).toBe(2)
  })

  it('leaves out a group that nets to zero', () => {
    const balances = balancesByGroup([
      { userId: 2, name: 'Bob', balances: [], breakdown: [{ groupId: 10, groupName: 'Goa', currency: 'INR', amount: 0.1 }] },
      { userId: 3, name: 'Carol', balances: [], breakdown: [{ groupId: 10, groupName: 'Goa', currency: 'INR', amount: -0.1 }] },
    ])
    expect(balances.has(10)).toBe(false)
  })
})
