import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { PAGE_SIZE, useGroupExpenses, type ExpenseFilters } from '../../api/queries'
import type { Expense, Group } from '../../api/types'
import { useAuth } from '../../auth/AuthProvider'
import { Money } from '../../components/Money'
import { ErrorState, Loading } from '../../components/States'
import { CATEGORIES, categoryOf } from '../../lib/categories'
import { formatDate } from '../../lib/dates'
import { formatMoney } from '../../lib/money'

const SPLIT_LABELS: Record<Expense['splitType'], string> = {
  EQUAL: 'Equally',
  EXACT: 'Exact amounts',
  PERCENTAGE: 'By percent',
  SHARES: 'By shares',
  ADJUSTMENT: 'With adjustments',
}

/** Filters live in the URL, so a filtered list survives a reload and the Back button. */
function useFilters(): [ExpenseFilters, (patch: Partial<ExpenseFilters>) => void] {
  const [params, setParams] = useSearchParams()
  const filters: ExpenseFilters = {
    page: Math.max(0, Number(params.get('page') ?? 0) || 0),
    q: params.get('q') ?? undefined,
    category: params.get('category') ?? undefined,
    paidBy: params.get('paidBy') ? Number(params.get('paidBy')) : undefined,
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
  }
  function update(patch: Partial<ExpenseFilters>) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        // Any filter change goes back to the first page
        const merged = { ...filters, page: 0, ...patch }
        for (const [key, value] of Object.entries(merged)) {
          if (value === undefined || value === '' || (key === 'page' && value === 0)) next.delete(key)
          else next.set(key, String(value))
        }
        return next
      },
      { replace: true },
    )
  }
  return [filters, update]
}

export function ExpenseList({ group, onOpen }: { group: Group; onOpen: (expense: Expense) => void }) {
  const { user } = useAuth()
  const [filters, setFilters] = useFilters()
  const expenses = useGroupExpenses(group.id, filters)
  const [search, setSearch] = useState(filters.q ?? '')

  // Search as you type, but only once typing pauses
  useEffect(() => {
    const timer = setTimeout(() => {
      if ((filters.q ?? '') !== search.trim()) setFilters({ q: search.trim() || undefined })
    }, 300)
    return () => clearTimeout(timer)
  })

  const filtered = Boolean(filters.q || filters.category || filters.paidBy || filters.from || filters.to)

  // Deleting the last expense on a later page would leave an empty page with no way back: go to the last real page
  const emptiedPage = !expenses.isPlaceholderData && expenses.data?.content.length === 0 && filters.page > 0
  const lastPage = Math.max(0, (expenses.data?.totalPages ?? 1) - 1)
  useEffect(() => {
    if (emptiedPage) setFilters({ page: lastPage })
  })

  return (
    <div className="grid gap-4">
      <div className="grid gap-2 sm:grid-cols-[2fr_1fr_1fr]">
        <input
          type="search"
          className="input"
          placeholder="Search expenses"
          aria-label="Search expenses"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select
          className="input"
          aria-label="Category"
          value={filters.category ?? ''}
          onChange={(e) => setFilters({ category: e.target.value || undefined })}
        >
          <option value="">All categories</option>
          {CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
        <select
          className="input"
          aria-label="Paid by"
          value={filters.paidBy ?? ''}
          onChange={(e) => setFilters({ paidBy: e.target.value ? Number(e.target.value) : undefined })}
        >
          <option value="">Paid by anyone</option>
          {group.members.map((m) => (
            <option key={m.id} value={m.id}>
              {m.id === user?.id ? 'Paid by you' : `Paid by ${m.name}`}
            </option>
          ))}
        </select>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <label className="flex items-center gap-2 text-muted">
          From
          <input type="date" className="input py-1.5" value={filters.from ?? ''} max={filters.to} onChange={(e) => setFilters({ from: e.target.value || undefined })} />
        </label>
        <label className="flex items-center gap-2 text-muted">
          To
          <input type="date" className="input py-1.5" value={filters.to ?? ''} min={filters.from} onChange={(e) => setFilters({ to: e.target.value || undefined })} />
        </label>
        {filtered && (
          <button
            type="button"
            className="btn btn-ghost px-3 py-1.5 text-sm"
            onClick={() => {
              setSearch('')
              setFilters({ q: undefined, category: undefined, paidBy: undefined, from: undefined, to: undefined })
            }}
          >
            Clear filters
          </button>
        )}
      </div>

      {expenses.isPending ? (
        <Loading label="Loading expenses" />
      ) : expenses.isError ? (
        <ErrorState error={expenses.error} onRetry={() => void expenses.refetch()} />
      ) : expenses.data.content.length === 0 ? (
        <p className="m-0 text-muted">{filtered ? 'No expenses match these filters.' : 'No expenses yet. Add the first one.'}</p>
      ) : (
        <>
          <ul className="m-0 list-none p-0" aria-busy={expenses.isFetching}>
            {expenses.data.content.map((expense) => (
              <li key={expense.id} className="border-b border-line last:border-0">
                <ExpenseRow expense={expense} me={user?.id} onOpen={() => onOpen(expense)} />
              </li>
            ))}
          </ul>
          <nav aria-label="Pages" className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted">
            <span>
              Page {expenses.data.page + 1} of {Math.max(1, expenses.data.totalPages)} {'·'} {expenses.data.totalElements}{' '}
              {expenses.data.totalElements === 1 ? 'expense' : 'expenses'}
            </span>
            {expenses.data.totalElements > PAGE_SIZE && (
              <span className="flex gap-2">
                <button
                  type="button"
                  className="btn btn-ghost px-3 py-1.5"
                  disabled={filters.page === 0}
                  onClick={() => setFilters({ ...filters, page: filters.page - 1 })}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className="btn btn-ghost px-3 py-1.5"
                  disabled={expenses.data.last}
                  onClick={() => setFilters({ ...filters, page: filters.page + 1 })}
                >
                  Next
                </button>
              </span>
            )}
          </nav>
        </>
      )}
    </div>
  )
}

/** One expense, with what it means for the signed-in user: what they lent, or what they owe for it. */
function ExpenseRow({ expense, me, onOpen }: { expense: Expense; me: number | undefined; onOpen: () => void }) {
  const category = categoryOf(expense.category)
  const myShare = expense.shares.find((s) => s.userId === me)?.amount ?? 0
  const paidByMe = expense.paidBy.id === me
  const payer = paidByMe ? 'You' : expense.paidBy.name.split(/\s+/)[0]

  let effect: React.ReactNode
  if (paidByMe) {
    const lent = expense.amount - myShare
    effect = lent > 0.004 ? (
      <>
        <Money amount={lent} currency={expense.currency} />
        <small className="block text-[11px] text-muted">you lent</small>
      </>
    ) : (
      <small className="text-xs text-muted">just you</small>
    )
  } else if (myShare > 0) {
    effect = (
      <>
        <Money amount={-myShare} currency={expense.currency} absolute />
        <small className="block text-[11px] text-muted">you owe</small>
      </>
    )
  } else {
    effect = <small className="text-xs text-muted">not involved</small>
  }

  return (
    <button type="button" onClick={onOpen} className="flex w-full cursor-pointer items-center gap-3 bg-transparent py-3 text-left text-inherit">
      <span className="grid h-10 w-10 flex-none place-items-center rounded-xl border border-line bg-surface-2 text-lg" aria-hidden="true">
        {category.icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold">{expense.description}</span>
        <span className="block text-xs text-muted">
          {payer} paid {formatMoney(expense.amount, expense.currency)} {'·'} {SPLIT_LABELS[expense.splitType]} {'·'}{' '}
          {formatDate(expense.date)}
        </span>
      </span>
      <span className="text-right">{effect}</span>
    </button>
  )
}
