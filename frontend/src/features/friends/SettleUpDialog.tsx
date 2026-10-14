import { ApiError } from '../../api/client'
import { useSettleUp } from '../../api/queries'
import type { FriendBalance } from '../../api/types'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/Toast'
import { formatMoney } from '../../lib/money'

/**
 * Confirms a settle-up before recording it: shows each payment that will be recorded, one per group and one
 * per currency outside groups, because money moves in each group's own currency.
 */
export function SettleUpDialog({ friend, onClose }: { friend: FriendBalance; onClose: () => void }) {
  const settleUp = useSettleUp()
  const toast = useToast()
  const first = friend.name.split(/\s+/)[0] ?? friend.name

  async function confirm() {
    try {
      const result = await settleUp.mutateAsync(friend.userId)
      const count = result.settlements.length
      toast.show(`Settled up with ${first}: ${count} ${count === 1 ? 'payment' : 'payments'} recorded`)
      onClose()
    } catch {
      // Shown below from settleUp.error
    }
  }

  return (
    <Modal title={`Settle up with ${first}`} onClose={onClose} busy={settleUp.isPending}>
      <p className="mt-0 text-muted">This records the following payments as made:</p>
      <ul className="m-0 mb-4 grid list-none gap-2 p-0">
        {friend.breakdown.map((item) => (
          <li
            key={`${item.groupId ?? 'none'}-${item.currency}`}
            className="flex items-center justify-between gap-3 rounded-xl border border-line bg-surface-2 px-3 py-2"
          >
            <span>
              {item.amount > 0 ? `${first} pays you` : `You pay ${first}`}
              <span className="block text-xs text-muted">{item.groupName ?? 'Outside groups'}</span>
            </span>
            {/* The words say who pays whom, so the amount is shown without a sign */}
            <span className="font-head font-bold tabular-nums">{formatMoney(Math.abs(item.amount), item.currency)}</span>
          </li>
        ))}
      </ul>
      {settleUp.error && (
        <p role="alert" className="alert alert-error">
          {settleUp.error instanceof ApiError ? settleUp.error.message : 'Could not settle up. Please try again.'}
        </p>
      )}
      <div className="flex justify-end gap-3">
        <button type="button" className="btn btn-ghost" onClick={onClose} disabled={settleUp.isPending}>
          Cancel
        </button>
        <button type="button" className="btn" onClick={confirm} disabled={settleUp.isPending}>
          {settleUp.isPending ? 'Recording...' : 'Record payments'}
        </button>
      </div>
    </Modal>
  )
}
