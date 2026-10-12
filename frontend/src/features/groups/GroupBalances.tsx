import { useState } from 'react'
import { ApiError } from '../../api/client'
import { useGroupBalances, useRecordPayment, useSimplifiedDebts } from '../../api/queries'
import type { Group, Transfer } from '../../api/types'
import { useAuth } from '../../auth/AuthProvider'
import { Modal } from '../../components/Modal'
import { Money } from '../../components/Money'
import { Avatar, ErrorState, Loading } from '../../components/States'
import { useToast } from '../../components/Toast'
import { today } from '../../lib/dates'
import { formatMoney, isZero } from '../../lib/money'

/** Each member's net balance in the group, as bars either side of zero. */
export function GroupBalances({ group }: { group: Group }) {
  const balances = useGroupBalances(group.id)
  if (balances.isPending) return <Loading label="Loading balances" />
  if (balances.isError) return <ErrorState error={balances.error} onRetry={() => void balances.refetch()} />

  const largest = Math.max(...balances.data.map((b) => Math.abs(b.netBalance)), 0)
  return (
    <div className="grid gap-1">
      <ul className="m-0 list-none p-0">
        {balances.data.map((b) => {
          const width = largest === 0 ? 0 : (Math.abs(b.netBalance) / largest) * 50
          return (
            <li key={b.userId} className="grid grid-cols-[minmax(0,7rem)_1fr_auto] items-center gap-3 py-2">
              <span className="truncate font-semibold">{b.name}</span>
              <span className="relative h-2.5 overflow-hidden rounded-full bg-surface-2" aria-hidden="true">
                <span className="absolute inset-y-0 left-1/2 w-0.5 bg-[var(--border)]" />
                {!isZero(b.netBalance) && (
                  <span
                    className={`absolute inset-y-0 rounded-full ${b.netBalance > 0 ? 'left-1/2 bg-positive' : 'right-1/2 bg-negative'}`}
                    style={{ width: `${width}%` }}
                  />
                )}
              </span>
              {isZero(b.netBalance) ? (
                <span className="text-sm text-muted">settled up</span>
              ) : (
                <Money amount={b.netBalance} currency={b.currency} signed />
              )}
            </li>
          )
        })}
      </ul>
      <p className="m-0 text-xs text-muted">Green is owed money, red owes money. Together they always add up to zero.</p>
    </div>
  )
}

/** The fewest payments that settle the group; the people involved can record one as made. */
export function SettleUpPanel({ group }: { group: Group }) {
  const { user } = useAuth()
  const debts = useSimplifiedDebts(group.id)
  const [recording, setRecording] = useState<Transfer | null>(null)

  if (debts.isPending) return <Loading label="Loading payments" />
  if (debts.isError) return <ErrorState error={debts.error} onRetry={() => void debts.refetch()} />
  if (debts.data.length === 0) return <p className="m-0 text-muted">Everyone is settled up.</p>

  return (
    <div className="grid gap-3">
      <ul className="m-0 grid list-none gap-2 p-0">
        {debts.data.map((t) => {
          const mine = t.fromUserId === user?.id || t.toUserId === user?.id
          const from = t.fromUserId === user?.id ? 'You' : t.fromName
          const to = t.toUserId === user?.id ? 'you' : t.toName
          return (
            <li key={`${t.fromUserId}-${t.toUserId}`} className="flex flex-wrap items-center gap-3 rounded-xl border border-line bg-surface-2 p-3">
              <Avatar name={t.fromName} size="sm" />
              <span className="min-w-0 flex-1">
                <b>{from}</b> {t.fromUserId === user?.id ? 'pay' : 'pays'} <b>{to}</b>
              </span>
              <span className="font-head font-bold tabular-nums">{formatMoney(t.amount, t.currency)}</span>
              {mine && (
                <button type="button" className="btn px-3 py-1.5 text-sm" onClick={() => setRecording(t)}>
                  Record payment
                </button>
              )}
            </li>
          )
        })}
      </ul>
      <p className="m-0 text-xs text-muted">
        {debts.data.length} {debts.data.length === 1 ? 'payment settles' : 'payments settle'} the whole group. A payment can be
        recorded by the person who paid or the one who received it.
      </p>
      {recording && <RecordPaymentDialog group={group} transfer={recording} onClose={() => setRecording(null)} />}
    </div>
  )
}

function RecordPaymentDialog({ group, transfer, onClose }: { group: Group; transfer: Transfer; onClose: () => void }) {
  const { user } = useAuth()
  const toast = useToast()
  const record = useRecordPayment()
  const [amount, setAmount] = useState(transfer.amount.toFixed(2))
  const [date, setDate] = useState(today())
  const [touched, setTouched] = useState(false)

  const amountError = !/^\d+(\.\d{1,2})?$/.test(amount.trim()) || Number(amount) <= 0 ? 'Enter an amount, like 500 or 99.50' : null
  const who = transfer.fromUserId === user?.id ? `You paid ${transfer.toName}` : `${transfer.fromName} paid you`

  async function confirm() {
    setTouched(true)
    if (amountError) return
    try {
      await record.mutateAsync({
        fromUserId: transfer.fromUserId,
        toUserId: transfer.toUserId,
        amount: Number(amount),
        groupId: group.id,
        date,
      })
      toast.show(`Payment of ${formatMoney(Number(amount), transfer.currency)} recorded`)
      onClose()
    } catch {
      // Shown below from record.error
    }
  }

  return (
    <Modal title="Record a payment" onClose={onClose} busy={record.isPending}>
      <p className="mt-0">{who}:</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1.5">
          <span className="label">Amount ({transfer.currency})</span>
          <input
            className="input font-head tabular-nums"
            inputMode="decimal"
            value={amount}
            aria-invalid={touched && amountError ? true : undefined}
            onChange={(e) => setAmount(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="label">Date</span>
          <input type="date" className="input" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
      </div>
      {touched && amountError && (
        <p role="alert" className="mb-0 text-sm font-semibold text-negative">
          {amountError}
        </p>
      )}
      {record.error && (
        <p role="alert" className="alert alert-error">
          {record.error instanceof ApiError ? record.error.message : 'Could not record the payment.'}
        </p>
      )}
      <div className="mt-4 flex justify-end gap-3">
        <button type="button" className="btn btn-ghost" onClick={onClose} disabled={record.isPending}>
          Cancel
        </button>
        <button type="button" className="btn" onClick={confirm} disabled={record.isPending}>
          {record.isPending ? 'Recording...' : 'Record payment'}
        </button>
      </div>
    </Modal>
  )
}
