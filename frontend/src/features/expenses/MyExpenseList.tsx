import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { PAGE_SIZE, useContacts, useGroups, useMyExpenses } from '../../api/queries'
import type { Expense } from '../../api/types'
import { useAuth } from '../../auth/AuthProvider'
import { ErrorState, Loading } from '../../components/States'
import { CATEGORIES } from '../../lib/categories'
import { ExpenseDetail } from './ExpenseDetail'
import { ExpenseForm } from './ExpenseForm'
import { ExpenseRow } from './ExpenseList'

/**
 * The user's expenses across all groups and outside them, newest first: everything, with search and filters, or
 * only those shared with one person (`withId`, a friend's page). A group expense opens on its group's page; one
 * outside groups opens here.
 */
export function MyExpenseList({ withId, filters: showFilters = false }: { withId?: number; filters?: boolean }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const groups = useGroups()
  const contacts = useContacts()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const expenses = useMyExpenses({ page, q: q || undefined, category: category || undefined, with: withId })
  const [viewing, setViewing] = useState<Expense | null>(null)
  const [editing, setEditing] = useState<Expense | null>(null)

  // Search as you type, once typing pauses; a new search starts on the first page
  useEffect(() => {
    const timer = setTimeout(() => {
      if (search.trim() !== q) {
        setQ(search.trim())
        setPage(0)
      }
    }, 300)
    return () => clearTimeout(timer)
  }, [search, q])

  const groupName = (id: number | null) => (id === null ? 'Outside groups' : (groups.data?.find((g) => g.id === id)?.name ?? 'A group'))

  function open(expense: Expense) {
    if (expense.groupId === null) setViewing(expense)
    else navigate(`/groups/${expense.groupId}?expense=${expense.id}`)
  }

  return (
    <div className="grid gap-4">
      {showFilters && (
        <div className="grid gap-2 sm:grid-cols-[2fr_1fr]">
          <input
            type="search"
            className="input"
            placeholder="Search all expenses"
            aria-label="Search all expenses"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <select
            className="input"
            aria-label="Category"
            value={category}
            onChange={(e) => {
              setCategory(e.target.value)
              setPage(0)
            }}
          >
            <option value="">All categories</option>
            {CATEGORIES.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </select>
        </div>
      )}

      {expenses.isPending ? (
        <Loading label="Loading expenses" />
      ) : expenses.isError ? (
        <ErrorState error={expenses.error} onRetry={() => void expenses.refetch()} />
      ) : expenses.data.content.length === 0 ? (
        <p className="m-0 text-muted">{q || category ? 'No expenses match.' : 'No expenses yet.'}</p>
      ) : (
        <>
          <ul className="m-0 list-none p-0" aria-busy={expenses.isFetching}>
            {expenses.data.content.map((expense) => (
              <li key={expense.id} className="border-b border-line last:border-0">
                <ExpenseRow expense={expense} me={user?.id} onOpen={() => open(expense)} where={groupName(expense.groupId)} />
              </li>
            ))}
          </ul>
          {expenses.data.totalElements > PAGE_SIZE && (
            <nav aria-label="Pages" className="flex items-center justify-between gap-2 text-sm text-muted">
              <span>
                Page {expenses.data.page + 1} of {expenses.data.totalPages}
              </span>
              <span className="flex gap-2">
                <button type="button" className="btn btn-ghost px-3 py-1.5" disabled={page === 0} onClick={() => setPage(page - 1)}>
                  Previous
                </button>
                <button type="button" className="btn btn-ghost px-3 py-1.5" disabled={expenses.data.last} onClick={() => setPage(page + 1)}>
                  Next
                </button>
              </span>
            </nav>
          )}
        </>
      )}

      {viewing && (
        <ExpenseDetail
          expenseId={viewing.id}
          initial={viewing}
          groupId={null}
          onClose={() => setViewing(null)}
          onEdit={(expense) => {
            setEditing(expense)
            setViewing(null)
          }}
        />
      )}
      {editing && <ExpenseForm group={null} contacts={contacts.data ?? []} expense={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
