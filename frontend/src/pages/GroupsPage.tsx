import { useState } from 'react'
import { Link } from 'react-router'
import { useFriendBalances, useGroups } from '../api/queries'
import { Money } from '../components/Money'
import { ErrorState, Loading } from '../components/States'
import { NewGroupDialog } from '../features/groups/NewGroupDialog'
import { balancesByGroup } from '../lib/balances'

export function GroupsPage() {
  const groups = useGroups()
  const friends = useFriendBalances()
  const [creating, setCreating] = useState(false)
  const balances = balancesByGroup(friends.data?.friends ?? [])

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="m-0 text-2xl">Groups</h1>
        <button type="button" className="btn btn-accent" onClick={() => setCreating(true)}>
          + New group
        </button>
      </div>

      {groups.isPending ? (
        <Loading label="Loading groups" />
      ) : groups.isError ? (
        <ErrorState error={groups.error} onRetry={() => void groups.refetch()} />
      ) : groups.data.length === 0 ? (
        <section className="card p-6">
          <p className="m-0 font-semibold">No groups yet</p>
          <p className="mt-1 mb-0 text-muted">Make one for a trip, a flat or anything you share costs on.</p>
        </section>
      ) : (
        <ul className="m-0 grid list-none gap-4 p-0 sm:grid-cols-2 lg:grid-cols-3">
          {groups.data.map((group) => {
            const balance = balances.get(group.id)
            return (
              <li key={group.id}>
                <Link to={`/groups/${group.id}`} className="card block p-5 text-inherit no-underline">
                  <div className="flex items-start justify-between gap-2">
                    <h2 className="m-0 text-base">{group.name}</h2>
                    <span className="rounded-full border border-line px-2 py-0.5 text-[11px] font-bold text-muted">
                      {group.currency}
                    </span>
                  </div>
                  <p className="mt-1 mb-3 text-sm text-muted">
                    {group.members.map((m) => m.name.split(/\s+/)[0]).join(', ')}
                  </p>
                  {/* Until balances arrive (or if they fail) say nothing, rather than a wrong "settled up" */}
                  {!friends.isSuccess ? null : balance ? (
                    <p className="m-0 text-sm">
                      {balance.amount > 0 ? 'You are owed ' : 'You owe '}
                      <Money amount={balance.amount} currency={balance.currency} absolute />
                    </p>
                  ) : (
                    <p className="m-0 text-sm text-muted">You are settled up</p>
                  )}
                </Link>
              </li>
            )
          })}
        </ul>
      )}

      {creating && <NewGroupDialog onClose={() => setCreating(false)} />}
    </div>
  )
}
