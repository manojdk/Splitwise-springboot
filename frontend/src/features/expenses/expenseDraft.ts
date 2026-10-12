import type { Category, CreateExpenseRequest, Expense, Group, UpdateExpenseRequest, User } from '../../api/types'
import { today } from '../../lib/dates'
import { computeSplit, paiseToString, type SplitResult, type SplitType } from '../../lib/split'

/** What the expense form holds: raw text, exactly as typed. */
export type ExpenseDraft = {
  description: string
  amount: string
  date: string
  paidById: number
  category: Category
  notes: string
  splitType: SplitType
  /** Who shares the expense. */
  included: Record<number, boolean>
  /** What was typed per person for the current split type. */
  values: Record<number, string>
  /** The expense was saved before split values were kept, so it is shown as exact amounts. */
  convertedToExact: boolean
}

export function newDraft(group: Group, me: number): ExpenseDraft {
  return {
    description: '',
    amount: '',
    date: today(),
    paidById: me,
    category: 'GENERAL',
    notes: '',
    splitType: 'EQUAL',
    included: Object.fromEntries(group.members.map((m) => [m.id, true])),
    values: {},
    convertedToExact: false,
  }
}

/**
 * The draft for editing an expense, in the terms it was entered (2 : 1 : 1 shares, 50 %...). If the stored
 * values do not reproduce the stored amounts (an expense from before values were kept), the split is shown
 * as exact amounts instead, so editing never silently changes what anyone owes.
 */
export function draftFromExpense(expense: Expense): ExpenseDraft {
  const included = Object.fromEntries(expense.shares.map((s) => [s.userId, true]))
  const base = {
    description: expense.description,
    amount: expense.amount.toFixed(2),
    date: expense.date,
    paidById: expense.paidBy.id,
    category: expense.category,
    notes: expense.notes ?? '',
    included,
  }
  const asEntered: ExpenseDraft = {
    ...base,
    splitType: expense.splitType,
    values: Object.fromEntries(expense.shares.map((s) => [s.userId, s.value === null ? '' : String(s.value)])),
    convertedToExact: false,
  }
  const check = computeSplit(asEntered.splitType, asEntered.amount, participants(asEntered, Object.keys(included).map(Number)))
  const reproduces =
    check.ok && expense.shares.every((s) => paiseToString(check.owed.get(s.userId) ?? -1n) === s.amount.toFixed(2))
  if (reproduces) return asEntered
  return {
    ...base,
    splitType: 'EXACT',
    values: Object.fromEntries(expense.shares.map((s) => [s.userId, s.amount.toFixed(2)])),
    convertedToExact: expense.splitType !== 'EXACT',
  }
}

/** The people in the split, with what was typed for them (EQUAL ignores values). */
export function participants(draft: ExpenseDraft, people: number[]) {
  return people
    .filter((id) => draft.included[id])
    .map((id) => ({ userId: id, value: draft.splitType === 'EQUAL' ? null : (draft.values[id] ?? '') }))
}

export function previewSplit(draft: ExpenseDraft, people: User[]): SplitResult {
  const names = new Map(people.map((p) => [p.id, p.name.split(/\s+/)[0] ?? p.name]))
  return computeSplit(
    draft.splitType,
    draft.amount,
    participants(draft, people.map((p) => p.id)),
    (id) => names.get(id) ?? `user ${id}`,
  )
}

/**
 * The API request. Numbers are sent as JSON numbers; every value here has at most 12 significant digits, which
 * a double carries exactly, so the API's BigDecimal receives what was typed.
 */
export function toCreateRequest(draft: ExpenseDraft, people: number[], groupId: number): CreateExpenseRequest {
  return { ...requestBody(draft, people), groupId }
}

/** version: the one the form was loaded with, so a concurrent edit is refused instead of overwritten. */
export function toUpdateRequest(draft: ExpenseDraft, people: number[], version: number): UpdateExpenseRequest {
  return { ...requestBody(draft, people), version }
}

function requestBody(draft: ExpenseDraft, people: number[]) {
  const shares = participants(draft, people).map(({ userId, value }) =>
    value === null || value.trim() === '' ? { userId } : { userId, value: Number(value) },
  )
  return {
    description: draft.description.trim(),
    amount: Number(draft.amount),
    paidById: draft.paidById,
    date: draft.date,
    splitType: draft.splitType,
    category: draft.category,
    notes: draft.notes.trim() || undefined,
    shares,
  }
}
