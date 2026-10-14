import { ApiError } from '../../api/client'
import { useDeleteExpense, useExpense, useExpenseHistory, useRestoreExpense } from '../../api/queries'
import type { Expense } from '../../api/types'
import { useAuth } from '../../auth/AuthProvider'
import { Modal } from '../../components/Modal'
import { Avatar, ErrorState, Loading } from '../../components/States'
import { useToast } from '../../components/Toast'
import { categoryOf } from '../../lib/categories'
import { formatDate, relativeTime } from '../../lib/dates'
import { formatMoney } from '../../lib/money'

type Props = {
  expenseId: number
  /** The list's copy, shown at once while the latest version loads. */
  initial?: Expense
  /**
   * The group the screen is showing (null: a screen of non-group expenses). Edit is offered only for expenses that
   * belong there, since a link may point elsewhere.
   */
  groupId: number | null
  onEdit: (expense: Expense) => void
  onClose: () => void
}

/**
 * One expense, opened from the list or a link (?expense=id): who paid, who owes what, notes and its history.
 * Edit, delete with Undo, or restore one that was deleted.
 */
export function ExpenseDetail({ expenseId, initial, groupId, onEdit, onClose }: Props) {
  const expense = useExpense(expenseId, initial)

  if (expense.isPending) {
    return (
      <Modal title="Expense" onClose={onClose}>
        <Loading label="Loading expense" />
      </Modal>
    )
  }
  if (expense.isError) {
    return (
      <Modal title="Expense" onClose={onClose}>
        <ErrorState error={expense.error} onRetry={() => void expense.refetch()} />
      </Modal>
    )
  }
  const canEdit = expense.data.groupId === groupId
  return <ExpenseView expense={expense.data} onEdit={canEdit ? onEdit : undefined} onClose={onClose} />
}

function ExpenseView({ expense, onEdit, onClose }: { expense: Expense; onEdit?: (expense: Expense) => void; onClose: () => void }) {
  const { user } = useAuth()
  const toast = useToast()
  const remove = useDeleteExpense()
  const restore = useRestoreExpense()
  const busy = remove.isPending || restore.isPending

  function undoDelete() {
    // This dialog is gone by the time Undo is pressed, and per-call mutate() callbacks do not run for an unmounted
    // component; the promise from mutateAsync settles regardless
    restore
      .mutateAsync(expense.id)
      .then(() => toast.show(`"${expense.description}" restored`))
      .catch((error: unknown) => toast.show(error instanceof ApiError ? error.message : 'Could not restore the expense'))
  }

  async function onDelete() {
    try {
      await remove.mutateAsync(expense.id)
      onClose()
      toast.show(`"${expense.description}" deleted`, { label: 'Undo', onClick: undoDelete })
    } catch {
      // Shown below from remove.error
    }
  }

  async function onRestore() {
    try {
      await restore.mutateAsync(expense.id)
      toast.show(`"${expense.description}" restored`)
    } catch {
      // Shown below from restore.error
    }
  }

  const error = remove.error ?? restore.error

  return (
    <Modal title={expense.description} onClose={onClose} busy={busy}>
      {expense.deleted && (
        <p role="status" className="alert alert-error mt-0">
          This expense was deleted. It no longer counts in any balance until it is restored.
        </p>
      )}
      <p className="mt-0 font-head text-3xl font-bold tabular-nums">{formatMoney(expense.amount, expense.currency)}</p>
      <p className="text-sm text-muted">
        {categoryOf(expense.category).label} {'·'} {formatDate(expense.date)} {'·'} paid by{' '}
        {expense.paidBy.id === user?.id ? 'you' : expense.paidBy.name}
      </p>
      <h3 className="mb-2 text-sm">Who owes what</h3>
      <ul className="m-0 mb-4 list-none p-0">
        {expense.shares.map((share) => (
          <li key={share.userId} className="flex items-center gap-3 border-b border-line py-2 last:border-0">
            <Avatar name={share.name} size="sm" />
            <span className="flex-1">
              {share.name}
              {share.value !== null && expense.splitType !== 'EXACT' && (
                <span className="text-xs text-muted">
                  {' '}
                  ({expense.splitType === 'PERCENTAGE' ? `${share.value}%` : expense.splitType === 'SHARES' ? `${share.value} shares` : `+${share.value}`})
                </span>
              )}
            </span>
            <span className="font-head font-bold tabular-nums">{formatMoney(share.amount, expense.currency)}</span>
          </li>
        ))}
      </ul>
      {expense.notes && <p className="whitespace-pre-wrap rounded-xl bg-surface-2 p-3 text-sm">{expense.notes}</p>}
      <ExpenseHistory expenseId={expense.id} />
      {error && (
        <p role="alert" className="alert alert-error">
          {error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'}
        </p>
      )}
      <div className="flex justify-end gap-3">
        {expense.deleted ? (
          <button type="button" className="btn" onClick={onRestore} disabled={busy}>
            {restore.isPending ? 'Restoring...' : 'Restore'}
          </button>
        ) : (
          <>
            <button type="button" className="btn btn-ghost" onClick={onDelete} disabled={busy}>
              {remove.isPending ? 'Deleting...' : 'Delete'}
            </button>
            {onEdit && (
              <button type="button" className="btn" onClick={() => onEdit(expense)} disabled={busy}>
                Edit
              </button>
            )}
          </>
        )}
      </div>
    </Modal>
  )
}

/** Who added it, each edit with exactly what changed, deletes and restores; newest first. */
function ExpenseHistory({ expenseId }: { expenseId: number }) {
  const history = useExpenseHistory(expenseId)
  if (history.isPending) return <Loading label="Loading history" rows={1} />
  // The expense itself is on screen; a failed history is not worth an error box
  if (history.isError || history.data.length === 0) return null

  return (
    <section aria-label="History" className="mb-4">
      <h3 className="mb-2 text-sm">History</h3>
      <ol className="m-0 grid list-none gap-2 p-0 text-sm">
        {history.data.map((entry) => (
          <li key={entry.id}>
            <span className="font-semibold">{entry.summary}</span>{' '}
            <time className="text-xs text-muted" dateTime={entry.createdAt} title={new Date(entry.createdAt).toLocaleString()}>
              {relativeTime(entry.createdAt)}
            </time>
            {entry.details.length > 0 && (
              <ul className="m-0 mt-1 list-none border-l-[3px] border-accent pl-3 text-muted">
                {entry.details.map((line, i) => (
                  <li key={i}>{line}</li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}
