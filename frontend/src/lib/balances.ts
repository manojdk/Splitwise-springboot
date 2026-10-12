import type { FriendBalance } from '../api/types'
import { isZero, roundCents } from './money'

export type GroupBalance = { currency: string; amount: number }

/**
 * The signed-in user's balance in each group, from the friends breakdown: a group's items are the user's
 * simplified debts there, so they add up to the user's net balance in that group (positive = owed money).
 * Groups where the user is settled up are absent.
 */
export function balancesByGroup(friends: FriendBalance[]): Map<number, GroupBalance> {
  const byGroup = new Map<number, GroupBalance>()
  for (const friend of friends) {
    for (const item of friend.breakdown) {
      if (item.groupId === null) continue
      const current = byGroup.get(item.groupId)
      byGroup.set(item.groupId, {
        currency: item.currency,
        amount: roundCents((current?.amount ?? 0) + item.amount),
      })
    }
  }
  for (const [groupId, balance] of byGroup) {
    if (isZero(balance.amount)) byGroup.delete(groupId)
  }
  return byGroup
}
