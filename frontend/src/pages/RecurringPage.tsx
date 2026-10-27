import { useState } from 'react'
import { ApiError } from '../api/client'
import { useGroups, useRecurringExpenses, useResumeRecurring, useStopRecurring } from '../api/queries'
import type { Group, RecurringExpense } from '../api/types'
import { Modal } from '../components/Modal'
import { ErrorState, Loading } from '../components/States'
import { useToast } from '../components/Toast'
import { ExpenseForm } from '../features/expenses/ExpenseForm'
import { categoryOf } from '../lib/categories'
import { formatDate, today } from '../lib/dates'
import { formatMoney } from '../lib/money'

const EVERY: Record<RecurringExpense['frequency'], string> = { WEEKLY: 'Weekly', MONTHLY: 'Monthly', YEARLY: 'Yearly' }

/** Expenses that repeat: what, how much, how often and when next; stop or resume one, or set up a new one. */
export function RecurringPage() {
  const templates = useRecurringExpenses()
  const groups = useGroups()
  const [stopping, setStopping] = useState<RecurringExpense | null>(null)
  const [choosingGroup, setChoosingGroup] = useState(false)
  const [creatingIn, setCreatingIn] = useState<Group | null>(null)

  const groupName = (id: number | null) =>
    id === null ? 'Outside groups' : (groups.data?.find((g) => g.id === id)?.name ?? 'A group')
  // Running ones first; within each, the next one due first
  const sorted = [...(templates.data ?? [])].sort(
    (a, b) => Number(b.active) - Number(a.active) || (a.nextDate ?? '').localeCompare(b.nextDate ?? ''),
  )

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="m-0 text-2xl">Recurring</h1>
        <button type="button" className="btn btn-accent" onClick={() => setChoosingGroup(true)}>
          + New recurring expense
        </button>
      </div>

      {templates.isPending ? (
        <Loading label="Loading recurring expenses" />
      ) : templates.isError ? (
        <ErrorState error={templates.error} onRetry={() => void templates.refetch()} />
      ) : sorted.length === 0 ? (
        <section className="card p-6">
          <p className="m-0 font-semibold">Nothing repeats yet</p>
          <p className="mt-1 mb-0 text-muted">Rent, wifi or a subscription: set it up once and it is added on every due date.</p>
        </section>
      ) : (
        <ul className="m-0 grid list-none gap-4 p-0 sm:grid-cols-2 lg:grid-cols-3">
          {sorted.map((t) => (
            <li key={t.id} className={`card grid content-start gap-2 p-5 ${t.active ? '' : 'opacity-75'}`}>
              <div className="flex items-start justify-between gap-2">
                <span className="text-lg" aria-hidden="true">
                  {categoryOf(t.category).icon}
                </span>
                <span
                  className={`rounded-full px-2.5 py-0.5 text-[11px] font-bold ${t.active ? 'bg-primary text-primary-ink' : 'border border-line text-muted'}`}
                >
                  {t.active ? EVERY[t.frequency] : 'Stopped'}
                </span>
              </div>
              <h2 className="m-0 text-base">{t.description}</h2>
              <p className="m-0 font-head text-2xl font-bold tabular-nums">{formatMoney(t.amount, t.currency)}</p>
              <p className="m-0 text-sm text-muted">
                {groupName(t.groupId)}
                {t.active && t.nextDate && (
                  <>
                    {' '}
                    {'·'} next on <b className="text-text">{formatDate(t.nextDate)}</b>
                  </>
                )}
              </p>
              {t.endDate && t.active && <p className="m-0 text-xs text-muted">Until {formatDate(t.endDate)}</p>}
              {!t.active && t.stoppedReason && <p className="m-0 text-xs text-muted">{t.stoppedReason}</p>}
              {t.active && (
                <button
                  type="button"
                  className="btn btn-ghost mt-1 justify-self-start px-3 py-1.5 text-sm"
                  onClick={() => setStopping(t)}
                  aria-label={`Stop ${t.description}`}
                >
                  Stop
                </button>
              )}
              {/* One whose end date has passed cannot run again; the API would refuse it */}
              {!t.active && !(t.endDate && t.endDate < today()) && <ResumeButton template={t} />}
            </li>
          ))}
        </ul>
      )}

      {stopping && <StopDialog template={stopping} onClose={() => setStopping(null)} />}
      {choosingGroup && (
        <ChooseGroupDialog
          groups={groups.data ?? []}
          onClose={() => setChoosingGroup(false)}
          onChoose={(group) => {
            setChoosingGroup(false)
            setCreatingIn(group)
          }}
        />
      )}
      {creatingIn && <ExpenseForm group={creatingIn} recurring onClose={() => setCreatingIn(null)} />}
    </div>
  )
}

function StopDialog({ template, onClose }: { template: RecurringExpense; onClose: () => void }) {
  const stop = useStopRecurring()
  const toast = useToast()

  async function confirm() {
    try {
      await stop.mutateAsync(template.id)
      toast.show(`"${template.description}" stopped`)
      onClose()
    } catch {
      // Shown below from stop.error
    }
  }

  return (
    <Modal title={`Stop "${template.description}"?`} onClose={onClose} busy={stop.isPending}>
      <p className="mt-0 text-muted">No more expenses will be added from it. The ones already added stay.</p>
      {stop.error && (
        <p role="alert" className="alert alert-error">
          {stop.error instanceof ApiError ? stop.error.message : 'Could not stop it. Please try again.'}
        </p>
      )}
      <div className="flex justify-end gap-3">
        <button type="button" className="btn btn-ghost" onClick={onClose} disabled={stop.isPending}>
          Keep it
        </button>
        <button type="button" className="btn btn-accent" onClick={confirm} disabled={stop.isPending}>
          {stop.isPending ? 'Stopping...' : 'Stop'}
        </button>
      </div>
    </Modal>
  )
}

/** No confirmation needed: resuming adds nothing for the dates missed, and it can be stopped again. */
function ResumeButton({ template }: { template: RecurringExpense }) {
  const resume = useResumeRecurring()
  const toast = useToast()

  async function onResume() {
    try {
      const resumed = await resume.mutateAsync(template.id)
      toast.show(
        resumed.nextDate
          ? `"${template.description}" resumed, next on ${formatDate(resumed.nextDate)}`
          : `"${template.description}" resumed`,
      )
    } catch {
      // Shown below from resume.error
    }
  }

  return (
    <>
      <button
        type="button"
        className="btn btn-ghost mt-1 justify-self-start px-3 py-1.5 text-sm"
        onClick={onResume}
        disabled={resume.isPending}
        aria-label={`Resume ${template.description}`}
      >
        {resume.isPending ? 'Resuming...' : 'Resume'}
      </button>
      {resume.error && (
        <p role="alert" className="alert alert-error m-0 text-sm">
          {resume.error instanceof ApiError ? resume.error.message : 'Could not resume it. Please try again.'}
        </p>
      )}
      <p className="m-0 text-xs text-muted">Carries on from the next date; the dates missed are not added.</p>
    </>
  )
}

function ChooseGroupDialog({ groups, onChoose, onClose }: { groups: Group[]; onChoose: (group: Group) => void; onClose: () => void }) {
  return (
    <Modal title="Which group?" onClose={onClose}>
      {groups.length === 0 ? (
        <p className="m-0 text-muted">Create a group first; recurring expenses are set up in a group.</p>
      ) : (
        <ul className="m-0 grid list-none gap-2 p-0">
          {groups.map((g) => (
            <li key={g.id}>
              <button
                type="button"
                className="flex w-full cursor-pointer items-center justify-between rounded-xl border border-line bg-surface-2 px-4 py-3 text-left text-inherit"
                onClick={() => onChoose(g)}
              >
                <span className="font-semibold">{g.name}</span>
                <span className="text-xs text-muted">{g.currency}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
