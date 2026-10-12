import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { useAddMember, useGroup, useRemoveMember } from '../api/queries'
import type { Group, User } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { Modal } from '../components/Modal'
import { Avatar, ErrorState, Loading } from '../components/States'
import { useToast } from '../components/Toast'
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

  return (
    <div className="grid gap-5">
      <div>
        <Link to="/groups" className="text-sm font-bold text-primary">
          {'←'} All groups
        </Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <h1 className="m-0 text-2xl">{group.data.name}</h1>
          <span className="rounded-full border border-line px-2 py-0.5 text-xs font-bold text-muted">{group.data.currency}</span>
        </div>
      </div>
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <section className="card p-5" aria-labelledby="expenses-heading">
          <h2 id="expenses-heading" className="mt-0 mb-2 text-base">
            Expenses
          </h2>
          <p className="m-0 text-muted">Adding and listing expenses comes next. For now, set up who is in the group.</p>
        </section>
        <Members group={group.data} />
      </div>
    </div>
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
