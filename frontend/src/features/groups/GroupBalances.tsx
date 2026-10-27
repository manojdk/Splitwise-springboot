import { useState } from 'react'
import { ApiError } from '../../api/client'
import {
  useDeleteSettlement,
  useGroupBalances,
  useGroupSettlements,
  useRecordPayment,
  useRestoreSettlement,
  useSimplifiedDebts,
} from '../../api/queries'
import type { Group, Settlement, Transfer } from '../../api/types'
import { useAuth } from '../../auth/AuthProvider'
import { Modal } from '../../components/Modal'
import { Money } from '../../components/Money'
import { Avatar, ErrorState, Loading } from '../../components/States'
import { useToast } from '../../components/Toast'
import { currencyDecimals } from '../../lib/currencies'
import { dateWindowError, EARLIEST_DATE, formatDate, today } from '../../lib/dates'
import { formatMoney, isZero } from '../../lib/money'
import { amountText } from '../expenses/expenseDraft'

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

/** What is left to pay, then the payments already recorded (each can be deleted, with Undo). */
export function SettleUpPanel({ group }: { group: Group }) {
  return (
    <div className="grid gap-6">
      <SuggestedPayments group={group} />
      <RecordedPayments group={group} />
    </div>
  )
}

/** The fewest payments that settle the group; the people involved can record one as made. */
function SuggestedPayments({ group }: { group: Group }) {
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

/** Payments recorded in the group, newest first. A mistake can be deleted, and the toast offers Undo. */
function RecordedPayments({ group }: { group: Group }) {
  const { user } = useAuth()
  const toast = useToast()
  const payments = useGroupSettlements(group.id)
  const remove = useDeleteSettlement()
  const restore = useRestoreSettlement()

  if (payments.isPending) return <Loading label="Loading recorded payments" rows={2} />
  if (payments.isError) return <ErrorState error={payments.error} onRetry={() => void payments.refetch()} />
  const items = payments.data.pages.flatMap((page) => page.content)

  const name = (id: number, fallback: string) => (id === user?.id ? 'you' : fallback)
  const describe = (p: Settlement) =>
    `${p.fromUserId === user?.id ? 'You' : p.fromName} paid ${name(p.toUserId, p.toName)} ${formatMoney(p.amount, p.currency)}`

  function onDelete(payment: Settlement) {
    remove
      .mutateAsync(payment.id)
      .then(() =>
        toast.show('Payment deleted', {
          label: 'Undo',
          onClick: () =>
            restore
              .mutateAsync(payment.id)
              .then(() => toast.show('Payment restored'))
              .catch((error: unknown) => toast.show(error instanceof ApiError ? error.message : 'Could not restore the payment')),
        }),
      )
      .catch((error: unknown) => toast.show(error instanceof ApiError ? error.message : 'Could not delete the payment'))
  }

  return (
    <section aria-labelledby="recorded-heading" className="grid gap-2">
      <h3 id="recorded-heading" className="m-0 text-sm">
        Recorded payments
      </h3>
      {items.length === 0 ? (
        <p className="m-0 text-sm text-muted">No payments recorded yet.</p>
      ) : (
        <ul className="m-0 list-none p-0">
          {items.map((payment) => (
            <li key={payment.id} className="flex flex-wrap items-center gap-3 border-b border-line py-2.5 last:border-0">
              <Avatar name={payment.fromName} size="sm" />
              <span className="min-w-0 flex-1">
                <span className="font-semibold">{describe(payment)}</span>
                <span className="block text-xs text-muted">
                  {formatDate(payment.date)}
                  {payment.note && <> {'·'} {payment.note}</>}
                </span>
              </span>
              <button
                type="button"
                className="btn btn-ghost px-3 py-1.5 text-xs"
                aria-label={`Delete payment: ${describe(payment)}`}
                onClick={() => onDelete(payment)}
                disabled={remove.isPending}
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}
      {payments.hasNextPage && (
        <button
          type="button"
          className="btn btn-ghost justify-self-start"
          onClick={() => void payments.fetchNextPage()}
          disabled={payments.isFetchingNextPage}
        >
          {payments.isFetchingNextPage ? 'Loading...' : 'Show more'}
        </button>
      )}
    </section>
  )
}

function RecordPaymentDialog({ group, transfer, onClose }: { group: Group; transfer: Transfer; onClose: () => void }) {
  const { user } = useAuth()
  const toast = useToast()
  const record = useRecordPayment()
  const [amount, setAmount] = useState(amountText(transfer.amount, group.currency))
  const [date, setDate] = useState(today())
  const [note, setNote] = useState('')
  const [touched, setTouched] = useState(false)

  const wholeOnly = currencyDecimals(group.currency) === 0
  const amountError =
    !(wholeOnly ? /^\d+$/ : /^\d+(\.\d{1,2})?$/).test(amount.trim()) || Number(amount) <= 0
      ? wholeOnly
        ? `Enter a whole amount, like 500 (${group.currency} has no decimals)`
        : 'Enter an amount, like 500 or 99.50'
      : null
  const dateError = date ? dateWindowError(date) : 'Pick the date of the payment'
  const noteError = note.length > 255 ? 'Use at most 255 characters' : null
  const who = transfer.fromUserId === user?.id ? `You paid ${transfer.toName}` : `${transfer.fromName} paid you`

  async function confirm() {
    setTouched(true)
    if (amountError || dateError || noteError) return
    try {
      await record.mutateAsync({
        fromUserId: transfer.fromUserId,
        toUserId: transfer.toUserId,
        amount: Number(amount),
        groupId: group.id,
        date,
        ...(note.trim() ? { note: note.trim() } : {}),
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
          <input
            type="date"
            className="input"
            value={date}
            min={EARLIEST_DATE}
            max={today()}
            aria-invalid={touched && dateError ? true : undefined}
            onChange={(e) => setDate(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1.5 sm:col-span-2">
          <span className="label">Note (optional)</span>
          <input
            className="input"
            value={note}
            placeholder="Bank transfer, cash, UPI..."
            aria-invalid={touched && noteError ? true : undefined}
            onChange={(e) => setNote(e.target.value)}
          />
        </label>
      </div>
      {touched && (amountError ?? dateError ?? noteError) && (
        <p role="alert" className="mb-0 text-sm font-semibold text-negative">
          {amountError ?? dateError ?? noteError}
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
