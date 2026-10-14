import { useId, useMemo, useState } from 'react'
import { api, ApiError } from '../../api/client'
import { useCreateRecurring, useSaveExpense } from '../../api/queries'
import type { Category, Expense, Frequency, Group, User } from '../../api/types'
import { useAuth } from '../../auth/AuthProvider'
import { Modal } from '../../components/Modal'
import { Avatar } from '../../components/States'
import { useToast } from '../../components/Toast'
import { CATEGORIES } from '../../lib/categories'
import { CURRENCIES } from '../../lib/currencies'
import { today } from '../../lib/dates'
import { formatMoney } from '../../lib/money'
import type { SplitType } from '../../lib/split'
import {
  draftFromExpense,
  newDraft,
  previewSplit,
  toCreateRequest,
  toRecurringRequest,
  toUpdateRequest,
  type ExpenseDraft,
} from './expenseDraft'

const SPLIT_TYPES: { type: SplitType; label: string; hint: string; unit: string }[] = [
  { type: 'EQUAL', label: 'Equally', hint: 'Everyone pays the same. Leftover paise go one each to the first people.', unit: '' },
  { type: 'EXACT', label: 'Exact', hint: 'Type what each person owes. The amounts must add up to the total.', unit: 'currency' },
  { type: 'PERCENTAGE', label: 'Percent', hint: 'Percentages must add up to 100.', unit: '%' },
  { type: 'SHARES', label: 'Shares', hint: 'For example 2 for a couple and 1 for a single person.', unit: 'shares' },
  { type: 'ADJUSTMENT', label: 'Adjust', hint: 'Extra amounts for some people; the rest is split equally.', unit: '+ currency' },
]

const FREQUENCIES: { value: Frequency; label: string }[] = [
  { value: 'WEEKLY', label: 'Every week' },
  { value: 'MONTHLY', label: 'Every month' },
  { value: 'YEARLY', label: 'Every year' },
]

/**
 * Add or edit an expense, with each person's share shown live as the split is typed. In a group, the group's
 * members share it in the group's currency. Outside groups (group null), it is split with the user's contacts,
 * in any currency. With recurring (groups only), it sets up an expense that repeats instead.
 */
export function ExpenseForm({
  group,
  contacts = [],
  withIds = [],
  expense,
  recurring = false,
  onClose,
}: {
  group: Group | null
  /** Outside groups: who can be in the split. */
  contacts?: User[]
  /** Outside groups: who is in the split from the start (a friend's page passes the friend). */
  withIds?: number[]
  expense?: Expense
  recurring?: boolean
  onClose: () => void
}) {
  const { user } = useAuth()
  const toast = useToast()
  const saveExpense = useSaveExpense()
  const createRecurring = useCreateRecurring()
  const save = recurring ? createRecurring : saveExpense
  const formId = useId()
  const me: User = user!
  // Who can be in it: the group's members, or outside groups the user and their contacts
  const members = useMemo(() => (group ? group.members : [me, ...contacts.filter((c) => c.id !== me.id)]), [group, contacts, me])
  const [currency, setCurrency] = useState(group?.currency ?? expense?.currency ?? 'INR')
  const [draft, setDraft] = useState<ExpenseDraft>(() =>
    expense ? draftFromExpense(expense) : newDraft(members, me.id, group ? undefined : [me.id, ...withIds]),
  )
  const [version, setVersion] = useState(expense?.version)
  const [submitted, setSubmitted] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)

  // Members, plus anyone in the expense who has since left (so their share and payment stay visible)
  const people: User[] = useMemo(() => {
    const involved = [...(expense?.shares ?? []).map((s) => ({ id: s.userId, name: s.name })), ...(expense ? [expense.paidBy] : [])]
    const extra = involved
      .filter((p, i) => !members.some((m) => m.id === p.id) && involved.findIndex((q) => q.id === p.id) === i)
      .map((p) => ({ id: p.id, name: p.name, email: '' }))
    return [...members, ...extra]
  }, [members, expense])
  const isMember = (id: number) => group === null || group.members.some((m) => m.id === id)
  const ids = people.map((p) => p.id)

  const split = previewSplit(draft, people)
  const descriptionError = !draft.description.trim()
    ? 'Describe the expense'
    : draft.description.trim().length > 255
      ? 'Use at most 255 characters'
      : null
  const notesError = draft.notes.length > 1000 ? 'Use at most 1000 characters' : null
  const dateError = !draft.date
    ? 'Pick a date'
    : recurring && draft.date < today()
      ? 'The first one cannot be in the past'
      : null
  const endError = recurring && draft.endDate && draft.endDate < draft.date ? 'The end must be on or after the start' : null
  const valid = split.ok && !descriptionError && !notesError && !dateError && !endError

  function update(patch: Partial<ExpenseDraft>) {
    setDraft((d) => ({ ...d, ...patch }))
  }

  function chooseType(type: SplitType) {
    // Shares start at 1 each; other types start empty
    const values = type === 'SHARES' ? Object.fromEntries(ids.map((id) => [id, '1'])) : {}
    update({ splitType: type, values, convertedToExact: false })
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitted(true)
    setFormError(null)
    if (!valid) return
    try {
      if (recurring) {
        const template = await createRecurring.mutateAsync(toRecurringRequest(draft, ids, group!.id))
        const how = FREQUENCIES.find((f) => f.value === template.frequency)!.label.toLowerCase()
        toast.show(`"${template.description}" will repeat ${how}`)
        onClose()
        return
      }
      const saved = await saveExpense.mutateAsync(
        expense
          ? { id: expense.id, body: toUpdateRequest(draft, ids, version!, group ? undefined : currency) }
          : { body: toCreateRequest(draft, ids, group?.id ?? null, currency) },
      )
      toast.show(expense ? `"${saved.description}" updated` : `"${saved.description}" added`)
      onClose()
    } catch (error) {
      if (expense && error instanceof ApiError && error.status === 409 && /changed by someone else/.test(error.message)) {
        setConflict(true)
      } else {
        setFormError(error instanceof ApiError ? error.message : 'Could not save the expense. Please try again.')
      }
    }
  }

  async function loadLatest() {
    try {
      const latest = await api<Expense>(`/expenses/${expense!.id}`)
      setDraft(draftFromExpense(latest))
      setVersion(latest.version)
      setConflict(false)
      setSubmitted(false)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not load the latest version.')
    }
  }

  const current = SPLIT_TYPES.find((t) => t.type === draft.splitType)!
  const show = (error: string | null) => (submitted ? error : null)

  return (
    <Modal
      title={
        recurring ? `New recurring expense · ${group!.name}` : expense ? 'Edit expense' : `Add expense · ${group?.name ?? 'No group'}`
      }
      onClose={onClose}
      busy={save.isPending}
      size="lg"
    >
      <form noValidate onSubmit={submit} className="grid gap-4" aria-describedby={`${formId}-status`}>
        {conflict && (
          <div role="alert" className="alert alert-error flex flex-wrap items-center justify-between gap-3">
            Someone else changed this expense while you were editing.
            <button type="button" className="btn btn-ghost px-3 py-1.5 text-sm" onClick={loadLatest}>
              Load their version
            </button>
          </div>
        )}
        {formError && (
          <p role="alert" className="alert alert-error m-0">
            {formError}
          </p>
        )}

        <div className="grid gap-3 sm:grid-cols-[2fr_1fr]">
          <Field id={`${formId}-desc`} label="Description" error={show(descriptionError)}>
            <input
              id={`${formId}-desc`}
              className="input"
              value={draft.description}
              placeholder="Beach shack dinner"
              onChange={(e) => update({ description: e.target.value })}
              aria-invalid={show(descriptionError) ? true : undefined}
            />
          </Field>
          <Field id={`${formId}-amount`} label={`Amount (${currency})`}>
            <input
              id={`${formId}-amount`}
              className="input font-head tabular-nums"
              inputMode="decimal"
              value={draft.amount}
              placeholder="0.00"
              onChange={(e) => update({ amount: e.target.value })}
            />
          </Field>
        </div>

        {group === null && (
          <Field id={`${formId}-currency`} label="Currency">
            <select id={`${formId}-currency`} className="input" value={currency} onChange={(e) => setCurrency(e.target.value)}>
              {[...new Set([currency, ...CURRENCIES])].map((code) => (
                <option key={code} value={code}>
                  {code}
                </option>
              ))}
            </select>
          </Field>
        )}

        <div className="grid gap-3 sm:grid-cols-3">
          <Field id={`${formId}-paid`} label="Paid by">
            <select
              id={`${formId}-paid`}
              className="input"
              value={draft.paidById}
              onChange={(e) => update({ paidById: Number(e.target.value) })}
            >
              {people.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.id === user?.id ? `${m.name} (you)` : isMember(m.id) ? m.name : `${m.name} (left the group)`}
                </option>
              ))}
            </select>
          </Field>
          <Field id={`${formId}-date`} label={recurring ? 'Starts on' : 'Date'} error={show(dateError)}>
            <input
              id={`${formId}-date`}
              type="date"
              className="input"
              value={draft.date}
              min={recurring ? today() : undefined}
              onChange={(e) => update({ date: e.target.value })}
            />
          </Field>
          <Field id={`${formId}-cat`} label="Category">
            <select
              id={`${formId}-cat`}
              className="input"
              value={draft.category}
              onChange={(e) => update({ category: e.target.value as Category })}
            >
              {CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </Field>
        </div>

        {recurring && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field id={`${formId}-freq`} label="Repeats">
              <select
                id={`${formId}-freq`}
                className="input"
                value={draft.frequency}
                onChange={(e) => update({ frequency: e.target.value as Frequency })}
              >
                {FREQUENCIES.map((f) => (
                  <option key={f.value} value={f.value}>
                    {f.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field id={`${formId}-end`} label="Ends on (optional)" error={show(endError)}>
              <input
                id={`${formId}-end`}
                type="date"
                className="input"
                value={draft.endDate}
                min={draft.date}
                onChange={(e) => update({ endDate: e.target.value })}
              />
            </Field>
          </div>
        )}

        <fieldset className="m-0 grid gap-2 border-0 p-0">
          <legend className="label mb-1.5">Split</legend>
          <div role="radiogroup" aria-label="How to split" className="flex flex-wrap gap-1 rounded-2xl border border-line bg-surface p-1">
            {SPLIT_TYPES.map((t) => (
              <button
                key={t.type}
                type="button"
                role="radio"
                aria-checked={draft.splitType === t.type}
                onClick={() => chooseType(t.type)}
                className={
                  'min-w-20 flex-1 cursor-pointer rounded-xl px-2 py-2 font-head text-[11px] font-bold tracking-wide transition ' +
                  (draft.splitType === t.type ? 'bg-primary text-primary-ink shadow-[var(--glow)]' : 'text-muted hover:text-text')
                }
              >
                {t.label}
              </button>
            ))}
          </div>
          <p className="m-0 text-sm text-muted">{current.hint}</p>
          {draft.convertedToExact && (
            <p className="alert alert-info m-0 text-sm">
              This expense was saved before split details were kept, so it is shown as exact amounts.
            </p>
          )}

          <ul className="m-0 list-none p-0">
            {people.map((person) => {
              const on = !!draft.included[person.id]
              const owed = split.ok ? split.owed.get(person.id) : undefined
              const firstName = person.name.split(/\s+/)[0]
              const rowError = !split.ok && split.userId === person.id
              return (
                <li key={person.id} className="grid grid-cols-[auto_1fr_auto_auto] items-center gap-3 border-b border-dashed border-line py-2">
                  <input
                    type="checkbox"
                    className="h-[18px] w-[18px] accent-[var(--primary)]"
                    checked={on}
                    aria-label={`Include ${person.name}`}
                    onChange={(e) => update({ included: { ...draft.included, [person.id]: e.target.checked } })}
                  />
                  <span className="flex min-w-0 items-center gap-2">
                    <Avatar name={person.name} size="sm" />
                    <span className="truncate font-semibold">{person.name}</span>
                  </span>
                  {draft.splitType === 'EQUAL' ? (
                    <span className="w-24 text-xs text-muted">{on ? 'equal share' : ''}</span>
                  ) : (
                    <span className="flex w-28 items-center gap-1">
                      <input
                        className="input px-2 py-1.5 text-right tabular-nums"
                        inputMode="decimal"
                        disabled={!on}
                        value={draft.values[person.id] ?? ''}
                        placeholder={draft.splitType === 'ADJUSTMENT' ? '0' : ''}
                        aria-label={`${current.label} for ${firstName}`}
                        aria-invalid={rowError ? true : undefined}
                        onChange={(e) => update({ values: { ...draft.values, [person.id]: e.target.value } })}
                      />
                      <span className="w-12 text-xs text-muted" aria-hidden="true">
                        {current.unit.replace('currency', currency)}
                      </span>
                    </span>
                  )}
                  <output className="w-28 text-right font-head font-bold tabular-nums" aria-label={`${firstName} owes`}>
                    {on && owed !== undefined ? formatMoney(Number(owed) / 100, currency) : '–'}
                  </output>
                </li>
              )
            })}
          </ul>
          {/* Before an amount is typed this is a hint, not an error */}
          {!draft.amount.trim() && !submitted ? (
            <p id={`${formId}-status`} role="status" className="alert alert-info m-0">
              Enter the amount to see each person's share.
            </p>
          ) : (
            <p id={`${formId}-status`} role="status" className={`alert m-0 ${split.ok ? 'alert-info' : 'alert-error'}`}>
              {split.ok
                ? `Adds up to ${formatMoney(Number([...split.owed.values()].reduce((a, b) => a + b, 0n)) / 100, currency)} exactly`
                : split.error}
            </p>
          )}
        </fieldset>

        <Field id={`${formId}-notes`} label="Notes (optional)" error={show(notesError)}>
          <textarea
            id={`${formId}-notes`}
            className="input min-h-16"
            value={draft.notes}
            onChange={(e) => update({ notes: e.target.value })}
          />
        </Field>

        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={save.isPending}>
            Cancel
          </button>
          <button type="submit" className="btn" disabled={save.isPending || (submitted && !valid)}>
            {save.isPending ? 'Saving...' : recurring ? 'Set up' : expense ? 'Save changes' : 'Add expense'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function Field({ id, label, error, children }: { id: string; label: string; error?: string | null; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="label">
        {label}
      </label>
      {children}
      {error && (
        <p role="alert" className="m-0 text-sm font-semibold text-negative">
          {error}
        </p>
      )}
    </div>
  )
}
