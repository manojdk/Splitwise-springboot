import { ApiError } from '../../api/client'
import { useDeleteExpense, useRestoreExpense } from '../../api/queries'
import type { Expense } from '../../api/types'
import { useAuth } from '../../auth/AuthProvider'
import { Modal } from '../../components/Modal'
import { Avatar } from '../../components/States'
import { useToast } from '../../components/Toast'
import { categoryOf } from '../../lib/categories'
import { formatDate } from '../../lib/dates'
import { formatMoney } from '../../lib/money'

/** One expense: who paid, who owes what, notes; edit or delete (with undo). */
export function ExpenseDetail({ expense, onEdit, onClose }: { expense: Expense; onEdit: () => void; onClose: () => void }) {
  const { user } = useAuth()
  const toast = useToast()
  const remove = useDeleteExpense()
  const restore = useRestoreExpense()

  async function onDelete() {
    try {
      await remove.mutateAsync(expense.id)
      onClose()
      toast.show(`"${expense.description}" deleted`, {
        label: 'Undo',
        // This dialog is gone by the time Undo is pressed, and per-call mutate() callbacks do not run for an
        // unmounted component; the promise from mutateAsync settles regardless
        onClick: () =>
          restore
            .mutateAsync(expense.id)
            .then(() => toast.show(`"${expense.description}" restored`))
            .catch((error: unknown) =>
              toast.show(error instanceof ApiError ? error.message : 'Could not restore the expense'),
            ),
      })
    } catch {
      // Shown below from remove.error
    }
  }

  return (
    <Modal title={expense.description} onClose={onClose} busy={remove.isPending}>
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
      {expense.updatedAt && <p className="text-xs text-muted">Edited {new Date(expense.updatedAt).toLocaleString()}</p>}
      {remove.error && (
        <p role="alert" className="alert alert-error">
          {remove.error instanceof ApiError ? remove.error.message : 'Could not delete the expense.'}
        </p>
      )}
      <div className="flex justify-end gap-3">
        <button type="button" className="btn btn-ghost" onClick={onDelete} disabled={remove.isPending}>
          {remove.isPending ? 'Deleting...' : 'Delete'}
        </button>
        <button type="button" className="btn" onClick={onEdit} disabled={remove.isPending}>
          Edit
        </button>
      </div>
    </Modal>
  )
}
