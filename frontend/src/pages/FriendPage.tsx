import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useFriend } from '../api/queries'
import { Money } from '../components/Money'
import { Avatar, ErrorState, Loading } from '../components/States'
import { AddExpenseButton } from '../features/expenses/AddExpenseButton'
import { MyExpenseList } from '../features/expenses/MyExpenseList'
import { SettleUpDialog } from '../features/friends/SettleUpDialog'
import { NotFoundPage } from './NotFoundPage'

export function FriendPage() {
  const { friendId } = useParams()
  const id = Number(friendId)
  if (!Number.isSafeInteger(id) || id <= 0) return <NotFoundPage />
  return <FriendView id={id} />
}

/** One friend: where you stand with them (per group and outside groups), the expenses you share, settle up. */
function FriendView({ id }: { id: number }) {
  const friend = useFriend(id)
  const [settling, setSettling] = useState(false)

  if (friend.isPending) return <Loading label="Loading friend" />
  if (friend.isError) {
    return (
      <div className="grid gap-4">
        <Link to="/" className="text-sm font-bold text-primary">
          {'←'} Dashboard
        </Link>
        <ErrorState error={friend.error} />
      </div>
    )
  }

  const { name, balances, breakdown } = friend.data
  const first = name.split(/\s+/)[0] ?? name

  return (
    <div className="grid gap-5">
      <div>
        <Link to="/" className="text-sm font-bold text-primary">
          {'←'} Dashboard
        </Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <Avatar name={name} />
          <h1 className="m-0 text-2xl">{name}</h1>
          <span className="ml-auto flex gap-2">
            {balances.length > 0 && (
              <button type="button" className="btn btn-ghost" onClick={() => setSettling(true)}>
                Settle up
              </button>
            )}
            <AddExpenseButton withId={id} />
          </span>
        </div>
      </div>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)]">
        <section className="card p-5" aria-labelledby="standing-heading">
          <h2 id="standing-heading" className="mt-0 mb-3 text-base">
            Where you stand
          </h2>
          {balances.length === 0 ? (
            <p className="m-0 text-muted">You and {first} are settled up.</p>
          ) : (
            <>
              <ul className="m-0 mb-3 list-none p-0">
                {balances.map((b) => (
                  <li key={b.currency} className="text-xl">
                    <Money amount={b.amount} currency={b.currency} signed />{' '}
                    <span className="text-sm text-muted">{b.amount > 0 ? `${first} owes you` : `you owe ${first}`}</span>
                  </li>
                ))}
              </ul>
              <ul className="m-0 list-none p-0 text-sm">
                {breakdown.map((item) => (
                  <li key={`${item.groupId ?? 'none'}-${item.currency}`} className="flex justify-between gap-3 border-b border-line py-2 last:border-0">
                    {item.groupId === null ? (
                      <span className="text-muted">Outside groups</span>
                    ) : (
                      <Link to={`/groups/${item.groupId}`} className="font-semibold text-primary">
                        {item.groupName}
                      </Link>
                    )}
                    <Money amount={item.amount} currency={item.currency} signed />
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>

        <section className="card p-5" aria-labelledby="shared-heading">
          <h2 id="shared-heading" className="mt-0 mb-3 text-base">
            Expenses with {first}
          </h2>
          <MyExpenseList withId={id} />
        </section>
      </div>

      {settling && <SettleUpDialog friend={friend.data} onClose={() => setSettling(false)} />}
    </div>
  )
}
