import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { useAddMember, useDeleteGroup, useGroup, useRemoveMember, useRenameGroup } from '../api/queries'
import type { Expense, Group, User } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { Modal } from '../components/Modal'
import { Avatar, ErrorState, Loading } from '../components/States'
import { useToast } from '../components/Toast'
import { ActivityFeed } from '../features/activity/ActivityFeed'
import { ExpenseDetail } from '../features/expenses/ExpenseDetail'
import { ExpenseForm } from '../features/expenses/ExpenseForm'
import { ExpenseList } from '../features/expenses/ExpenseList'
import { GroupBalances, SettleUpPanel } from '../features/groups/GroupBalances'
import { GroupReport } from '../features/reports/GroupReport'
import { NotFoundPage } from './NotFoundPage'

export function GroupPage() {
  const { groupId } = useParams()
  const id = Number(groupId)
  if (!Number.isSafeInteger(id) || id <= 0) return <NotFoundPage />
  return <GroupView id={id} />
}

function GroupView({ id }: { id: number }) {
  const group = useGroup(id)

  if (group.isPending) return <Loading label="Loading group" />
  if (group.isError) {
    return (
      <div className="grid gap-4">
        <Link to="/groups" className="text-sm font-bold text-primary">
          {'←'} All groups
        </Link>
        <ErrorState error={group.error} onRetry={() => void group.refetch()} />
      </div>
    )
  }

  return <GroupContent group={group.data} />
}

type Tab = 'expenses' | 'balances' | 'settle' | 'activity' | 'report'
const TABS: { id: Tab; label: string }[] = [
  { id: 'expenses', label: 'Expenses' },
  { id: 'balances', label: 'Balances' },
  { id: 'settle', label: 'Settle up' },
  { id: 'activity', label: 'Activity' },
  { id: 'report', label: 'Report' },
]

function GroupContent({ group }: { group: Group }) {
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find((t) => t.id === params.get('tab'))?.id ?? 'expenses') as Tab
  const [adding, setAdding] = useState(false)
  const [settings, setSettings] = useState(false)
  const [editing, setEditing] = useState<Expense | null>(null)
  // The open expense is in the address (?expense=12), so it can be linked to, from Activity for example
  const viewingId = Number(params.get('expense')) || null
  const [listCopy, setListCopy] = useState<Expense | null>(null)

  function selectTab(next: Tab) {
    // Filters belong to the expenses tab; switching tabs starts clean
    setParams(next === 'expenses' ? {} : { tab: next }, { replace: true })
  }

  function openExpense(expense: Expense) {
    setListCopy(expense)
    setParams((current) => {
      const next = new URLSearchParams(current)
      next.set('expense', String(expense.id))
      return next
    })
  }

  function closeExpense() {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.delete('expense')
        return next
      },
      { replace: true },
    )
  }

  return (
    <div className="grid gap-5">
      <div>
        <Link to="/groups" className="text-sm font-bold text-primary">
          {'←'} All groups
        </Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <h1 className="m-0 text-2xl">{group.name}</h1>
          <span className="rounded-full border border-line px-2 py-0.5 text-xs font-bold text-muted">{group.currency}</span>
          <span className="ml-auto flex gap-2">
            <button type="button" className="btn btn-ghost" onClick={() => setSettings(true)}>
              Settings
            </button>
            <button type="button" className="btn btn-accent" onClick={() => setAdding(true)}>
              + Add expense
            </button>
          </span>
        </div>
      </div>
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <section className="card p-5" aria-label={TABS.find((t) => t.id === tab)!.label}>
          <div role="tablist" aria-label="Group sections" className="mb-4 flex flex-wrap gap-1.5">
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                role="tab"
                aria-selected={tab === t.id}
                onClick={() => selectTab(t.id)}
                className={
                  'cursor-pointer rounded-full border px-3.5 py-1.5 text-sm font-semibold transition ' +
                  (tab === t.id ? 'border-transparent bg-primary text-primary-ink' : 'border-line bg-surface-2 text-muted hover:text-text')
                }
              >
                {t.label}
              </button>
            ))}
          </div>
          <div role="tabpanel">
            {tab === 'expenses' && <ExpenseList group={group} onOpen={openExpense} />}
            {tab === 'balances' && <GroupBalances group={group} />}
            {tab === 'settle' && <SettleUpPanel group={group} />}
            {tab === 'activity' && <ActivityFeed groupId={group.id} />}
            {tab === 'report' && <GroupReport group={group} />}
          </div>
        </section>
        <Members group={group} />
      </div>

      {adding && <ExpenseForm group={group} onClose={() => setAdding(false)} />}
      {viewingId !== null && (
        <ExpenseDetail
          expenseId={viewingId}
          initial={listCopy?.id === viewingId ? listCopy : undefined}
          groupId={group.id}
          onClose={closeExpense}
          onEdit={(expense) => {
            setEditing(expense)
            closeExpense()
          }}
        />
      )}
      {editing && <ExpenseForm group={group} expense={editing} onClose={() => setEditing(null)} />}
      {settings && <GroupSettingsDialog group={group} onClose={() => setSettings(false)} />}
    </div>
  )
}

const nameSchema = z.object({
  name: z.string().trim().min(1, 'Give the group a name').max(100, 'Use at most 100 characters'),
})

/** Rename the group (any member); delete it (the creator, and only while it has no expenses or payments). */
function GroupSettingsDialog({ group, onClose }: { group: Group; onClose: () => void }) {
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const rename = useRenameGroup(group.id)
  const remove = useDeleteGroup(group.id)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const isCreator = user?.id === group.createdById
  const creator = group.members.find((m) => m.id === group.createdById)
  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<z.infer<typeof nameSchema>>({ resolver: zodResolver(nameSchema), defaultValues: { name: group.name } })

  const onRename = handleSubmit(async ({ name }) => {
    try {
      await rename.mutateAsync(name)
      toast.show(`Renamed to ${name}`)
      onClose()
    } catch {
      // Shown below from rename.error
    }
  })

  async function onDelete() {
    try {
      await remove.mutateAsync()
      toast.show(`${group.name} deleted`)
      navigate('/groups')
    } catch {
      // Shown below from remove.error
    }
  }

  const busy = rename.isPending || remove.isPending
  const error = rename.error ?? remove.error

  return (
    <Modal title="Group settings" onClose={onClose} busy={busy}>
      <form noValidate onSubmit={onRename} className="grid gap-2">
        <label htmlFor="group-name" className="label">
          Name
        </label>
        <div className="flex gap-2">
          <input
            id="group-name"
            className="input"
            aria-invalid={errors.name ? true : undefined}
            aria-describedby={errors.name ? 'group-name-error' : undefined}
            {...register('name')}
          />
          <button type="submit" className="btn shrink-0" disabled={busy || !isDirty}>
            {rename.isPending ? 'Saving...' : 'Save'}
          </button>
        </div>
        {errors.name && (
          <p id="group-name-error" role="alert" className="m-0 text-sm font-semibold text-negative">
            {errors.name.message}
          </p>
        )}
      </form>
      <p className="mt-2 mb-0 text-xs text-muted">The currency ({group.currency}) cannot be changed once the group exists.</p>

      <h3 className="mt-6 mb-1 border-t border-line pt-4 text-sm">Delete the group</h3>
      {isCreator ? (
        <>
          <p className="mt-0 text-sm text-muted">
            Only a group with no expenses or payments can be deleted (deleted ones count too, since they can still be
            restored).
          </p>
          {confirmingDelete ? (
            <div className="flex flex-wrap items-center gap-3">
              <span className="text-sm font-semibold">Delete {group.name} for everyone?</span>
              <button type="button" className="btn btn-ghost" onClick={() => setConfirmingDelete(false)} disabled={busy}>
                Keep it
              </button>
              <button type="button" className="btn btn-accent" onClick={onDelete} disabled={busy}>
                {remove.isPending ? 'Deleting...' : 'Yes, delete'}
              </button>
            </div>
          ) : (
            <button type="button" className="btn btn-ghost" onClick={() => setConfirmingDelete(true)} disabled={busy}>
              Delete group
            </button>
          )}
        </>
      ) : (
        <p className="mt-0 text-sm text-muted">Only {creator?.name ?? 'the person who created it'} can delete this group.</p>
      )}
      {error && (
        <p role="alert" className="alert alert-error">
          {error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'}
        </p>
      )}
    </Modal>
  )
}

const addSchema = z.object({
  email: z.string().trim().min(1, 'Enter an email address').pipe(z.email('Enter a valid email address')),
})

function Members({ group }: { group: Group }) {
  const { user } = useAuth()
  const toast = useToast()
  const addMember = useAddMember(group.id)
  const [removing, setRemoving] = useState<User | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof addSchema>>({ resolver: zodResolver(addSchema), defaultValues: { email: '' } })

  const isCreator = user?.id === group.createdById

  const onAdd = handleSubmit(async ({ email }) => {
    try {
      const updated = await addMember.mutateAsync(email)
      const added = updated.members.find((m) => m.email.toLowerCase() === email.toLowerCase())
      toast.show(`${added?.name ?? email} added to ${group.name}`)
      reset({ email: '' })
    } catch (error) {
      setError('email', { message: error instanceof ApiError ? error.message : 'Could not add this person' })
    }
  })

  return (
    <section className="card p-5" aria-labelledby="members-heading">
      <h2 id="members-heading" className="mt-0 mb-3 text-base">
        Members ({group.members.length})
      </h2>
      <ul className="m-0 mb-4 list-none p-0">
        {group.members.map((member) => {
          const isMe = member.id === user?.id
          const canRemove = isCreator ? !isMe : isMe
          return (
            <li key={member.id} className="flex items-center gap-3 border-b border-line py-2.5 last:border-0">
              <Avatar name={member.name} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="m-0 font-semibold">
                  {member.name}
                  {isMe && <span className="font-normal text-muted"> (you)</span>}
                </p>
                <p className="m-0 truncate text-xs text-muted">
                  {member.id === group.createdById ? 'Created the group' : member.email}
                </p>
              </div>
              {canRemove && (
                <button
                  type="button"
                  className="btn btn-ghost px-3 py-1.5 text-xs"
                  onClick={() => setRemoving(member)}
                  aria-label={isMe ? 'Leave the group' : `Remove ${member.name}`}
                >
                  {isMe ? 'Leave' : 'Remove'}
                </button>
              )}
            </li>
          )
        })}
      </ul>

      <form noValidate onSubmit={onAdd} className="grid gap-2">
        <label htmlFor="add-member" className="label">
          Add someone by email
        </label>
        <div className="flex gap-2">
          <input
            id="add-member"
            type="email"
            className="input"
            placeholder="dan@example.com"
            aria-invalid={errors.email ? true : undefined}
            aria-describedby={errors.email ? 'add-member-error' : undefined}
            {...register('email')}
          />
          <button type="submit" className="btn shrink-0" disabled={isSubmitting}>
            {isSubmitting ? 'Adding...' : 'Add'}
          </button>
        </div>
        {errors.email && (
          <p id="add-member-error" role="alert" className="m-0 text-sm font-semibold text-negative">
            {errors.email.message}
          </p>
        )}
      </form>

      {removing && <RemoveMemberDialog group={group} member={removing} onClose={() => setRemoving(null)} />}
    </section>
  )
}

function RemoveMemberDialog({ group, member, onClose }: { group: Group; member: User; onClose: () => void }) {
  const { user } = useAuth()
  const removeMember = useRemoveMember(group.id)
  const navigate = useNavigate()
  const toast = useToast()
  const leaving = member.id === user?.id

  async function confirm() {
    try {
      await removeMember.mutateAsync(member.id)
      if (leaving) {
        toast.show(`You left ${group.name}`)
        navigate('/groups')
      } else {
        toast.show(`${member.name} removed from ${group.name}`)
        onClose()
      }
    } catch {
      // Shown below from removeMember.error
    }
  }

  return (
    <Modal title={leaving ? `Leave ${group.name}?` : `Remove ${member.name}?`} onClose={onClose} busy={removeMember.isPending}>
      <p className="mt-0 text-muted">
        {leaving
          ? 'You can only leave once your balance in this group is zero.'
          : `${member.name} can only be removed once their balance in this group is zero.`}
      </p>
      {removeMember.error && (
        <p role="alert" className="alert alert-error">
          {removeMember.error instanceof ApiError ? removeMember.error.message : 'Something went wrong. Please try again.'}
        </p>
      )}
      <div className="flex justify-end gap-3">
        <button type="button" className="btn btn-ghost" onClick={onClose} disabled={removeMember.isPending}>
          Cancel
        </button>
        <button type="button" className="btn btn-accent" onClick={confirm} disabled={removeMember.isPending}>
          {removeMember.isPending ? 'Working...' : leaving ? 'Leave group' : 'Remove'}
        </button>
      </div>
    </Modal>
  )
}
