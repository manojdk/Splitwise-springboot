import { useState } from 'react'
import { Link } from 'react-router'
import { useFriendBalances, useGroups } from '../api/queries'
import type { FriendBalance, FriendTotals } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { Money } from '../components/Money'
import { Avatar, ErrorState, Loading } from '../components/States'
import { SettleUpDialog } from '../features/friends/SettleUpDialog'
import { balancesByGroup } from '../lib/balances'
import { formatMoney, isZero } from '../lib/money'

export function DashboardPage() {
  const { user } = useAuth()
  const friends = useFriendBalances()
  const firstName = user?.name.trim().split(/\s+/)[0] ?? ''

  return (
    <div className="grid gap-5">
      <h1 className="m-0 text-2xl">Welcome, {firstName}</h1>
      {friends.isPending ? (
        <Loading label="Loading balances" rows={2} />
      ) : friends.isError ? (
        <ErrorState error={friends.error} onRetry={() => void friends.refetch()} />
      ) : (
        <>
          <TotalsCards totals={friends.data.totals} />
          <div className="grid gap-5 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
            <FriendsCard friends={friends.data.friends} />
            <GroupsCard friends={friends.data.friends} />
          </div>
        </>
      )}
    </div>
  )
}

/** One figure per currency; currencies are never added together. */
function TotalsCards({ totals }: { totals: FriendTotals[] }) {
  const owed = totals.filter((t) => !isZero(t.youAreOwed))
  const owe = totals.filter((t) => !isZero(t.youOwe))

  if (totals.length === 0) {
    return (
      <section className="card hero-panel border-0 p-6">
        <p className="m-0 text-xs font-semibold tracking-[0.14em] uppercase opacity-80">Your balances</p>
        <p className="mt-2 mb-0 font-head text-2xl font-bold">All settled up</p>
        <p className="mt-2 mb-0 opacity-85">Add a group or an expense, and who owes whom shows up here.</p>
      </section>
    )
  }

  return (
    <div className="grid gap-4 md:grid-cols-3">
      <section className="card hero-panel border-0 p-5" aria-label="You are owed">
        <h2 className="m-0 text-xs font-semibold tracking-[0.14em] uppercase opacity-80">You are owed</h2>
        <AmountList entries={owed.map((t) => ({ currency: t.currency, amount: t.youAreOwed }))} plain />
      </section>
      <section className="card p-5" aria-label="You owe">
        <h2 className="m-0 text-xs font-semibold tracking-[0.14em] text-muted uppercase">You owe</h2>
        <AmountList entries={owe.map((t) => ({ currency: t.currency, amount: -t.youOwe }))} />
      </section>
      <section className="card p-5" aria-label="Net balance">
        <h2 className="m-0 text-xs font-semibold tracking-[0.14em] text-muted uppercase">Net balance</h2>
        <AmountList entries={totals.map((t) => ({ currency: t.currency, amount: t.net }))} signed />
        <p className="mt-2 mb-0 text-xs text-muted">Each currency is kept separately.</p>
      </section>
    </div>
  )
}

function AmountList({ entries, plain = false, signed = false }: {
  entries: { currency: string; amount: number }[]
  plain?: boolean
  signed?: boolean
}) {
  if (entries.length === 0) return <p className="mt-3 mb-0 font-head text-2xl font-bold opacity-70">Nothing</p>
  return (
    <ul className="m-0 mt-3 grid list-none gap-1 p-0">
      {entries.map((e, i) => (
        <li key={e.currency} className={i === 0 ? 'text-2xl' : 'text-sm'}>
          {plain ? (
            <span className="font-head font-bold tabular-nums">{formatMoney(e.amount, e.currency)}</span>
          ) : (
            <Money amount={e.amount} currency={e.currency} signed={signed} />
          )}
        </li>
      ))}
    </ul>
  )
}

function FriendsCard({ friends }: { friends: FriendBalance[] }) {
  const [settling, setSettling] = useState<FriendBalance | null>(null)

  return (
    <section className="card p-5" aria-labelledby="friends-heading">
      <h2 id="friends-heading" className="mt-0 mb-3 text-base">
        Friends
      </h2>
      {friends.length === 0 ? (
        <p className="m-0 text-muted">No open balances with anyone.</p>
      ) : (
        <ul className="m-0 list-none p-0">
          {friends.map((friend) => (
            <li key={friend.userId} className="flex flex-wrap items-center gap-3 border-b border-line py-3 last:border-0">
              <Avatar name={friend.name} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="m-0 font-semibold">{friend.name}</p>
                <p className="m-0 mt-0.5 text-xs text-muted">
                  {friend.breakdown.map((item) => item.groupName ?? 'Outside groups').join(' · ')}
                </p>
              </div>
              <div className="text-right">
                {friend.balances.map((b) => (
                  <div key={b.currency}>
                    <Money amount={b.amount} currency={b.currency} signed />
                    <span className="block text-[11px] text-muted">{b.amount > 0 ? 'owes you' : 'you owe'}</span>
                  </div>
                ))}
              </div>
              <button
                type="button"
                className="btn btn-ghost px-3 py-2 text-sm"
                onClick={() => setSettling(friend)}
                aria-label={`Settle up with ${friend.name}`}
              >
                Settle up
              </button>
            </li>
          ))}
        </ul>
      )}
      {settling && <SettleUpDialog friend={settling} onClose={() => setSettling(null)} />}
    </section>
  )
}

function GroupsCard({ friends }: { friends: FriendBalance[] }) {
  const groups = useGroups()
  const balances = balancesByGroup(friends)

  return (
    <section className="card p-5" aria-labelledby="groups-heading">
      <div className="mb-3 flex items-center justify-between">
        <h2 id="groups-heading" className="m-0 text-base">
          Your groups
        </h2>
        <Link to="/groups" className="text-sm font-bold text-primary">
          All groups
        </Link>
      </div>
      {groups.isPending ? (
        <Loading label="Loading groups" rows={2} />
      ) : groups.isError ? (
        <ErrorState error={groups.error} onRetry={() => void groups.refetch()} />
      ) : groups.data.length === 0 ? (
        <p className="m-0 text-muted">
          No groups yet.{' '}
          <Link to="/groups" className="font-bold text-primary">
            Create one
          </Link>
        </p>
      ) : (
        <ul className="m-0 list-none p-0">
          {groups.data.map((group) => {
            const balance = balances.get(group.id)
            return (
              <li key={group.id} className="border-b border-line last:border-0">
                <Link to={`/groups/${group.id}`} className="flex items-center gap-3 py-3 text-inherit no-underline">
                  <div className="min-w-0 flex-1">
                    <p className="m-0 font-semibold">{group.name}</p>
                    <p className="m-0 text-xs text-muted">
                      {group.members.length} {group.members.length === 1 ? 'member' : 'members'} {'·'} {group.currency}
                    </p>
                  </div>
                  {balance ? (
                    <Money amount={balance.amount} currency={balance.currency} signed />
                  ) : (
                    <span className="text-sm text-muted">settled up</span>
                  )}
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
