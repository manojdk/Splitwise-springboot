import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { api, ApiError } from '../../api/client'
import { keys, useCreateGroup } from '../../api/queries'
import type { Group } from '../../api/types'
import { Modal } from '../../components/Modal'
import { TextField } from '../../components/TextField'
import { useToast } from '../../components/Toast'
import { CURRENCIES } from '../../lib/currencies'

const anEmail = z.email()

/** "a@x.com, b@y.com" -> unique, lower-cased addresses; the error names the first bad one. */
const emailList = z.string().transform((text, ctx) => {
  const emails = [...new Set(text.split(/[\s,;]+/).map((e) => e.trim().toLowerCase()).filter(Boolean))]
  const invalid = emails.find((email) => !anEmail.safeParse(email).success)
  if (invalid) {
    ctx.addIssue({ code: 'custom', message: `"${invalid}" is not a valid email address` })
    return z.NEVER
  }
  if (emails.length > 20) {
    ctx.addIssue({ code: 'custom', message: 'Add at most 20 people at once' })
    return z.NEVER
  }
  return emails
})

const schema = z.object({
  name: z.string().trim().min(1, 'Give the group a name').max(100, 'Use at most 100 characters'),
  currency: z.enum(CURRENCIES),
  members: emailList,
})
type FormInput = z.input<typeof schema>
type FormOutput = z.output<typeof schema>

type Failure = { email: string; reason: string }

/**
 * Creates a group, then adds the invited people one by one (the API adds members by email). People who
 * cannot be added (no account yet, for example) are listed afterwards; the group itself is kept.
 */
export function NewGroupDialog({ onClose }: { onClose: () => void }) {
  const createGroup = useCreateGroup()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [partial, setPartial] = useState<{ group: Group; failures: Failure[] } | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormInput, unknown, FormOutput>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', currency: 'INR', members: '' },
  })

  const onSubmit = handleSubmit(async ({ name, currency, members }) => {
    setFormError(null)
    let group: Group
    try {
      group = await createGroup.mutateAsync({ name, currency })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Could not create the group. Please try again.')
      return
    }
    const failures: Failure[] = []
    for (const email of members) {
      try {
        group = await api<Group>(`/groups/${group.id}/members`, { method: 'POST', body: { email } })
      } catch (error) {
        const reason =
          error instanceof ApiError
            ? error.status === 404
              ? 'no account with this email'
              : error.message
            : 'could not be added'
        failures.push({ email, reason })
      }
    }
    if (members.length > 0) {
      queryClient.setQueryData(keys.group(group.id), group)
      await queryClient.invalidateQueries({ queryKey: keys.groups, exact: true })
    }
    if (failures.length > 0) {
      setPartial({ group, failures })
      return
    }
    toast.show(`Group "${group.name}" created`)
    navigate(`/groups/${group.id}`)
  })

  if (partial) {
    return (
      <Modal title="Group created" onClose={() => navigate(`/groups/${partial.group.id}`)}>
        <p className="mt-0">
          <b>{partial.group.name}</b> is ready, but {partial.failures.length === 1 ? 'one person' : 'some people'} could
          not be added:
        </p>
        <ul className="mb-4 grid gap-1 pl-5">
          {partial.failures.map((f) => (
            <li key={f.email}>
              {f.email} <span className="text-muted">({f.reason})</span>
            </li>
          ))}
        </ul>
        <p className="text-sm text-muted">They need an account first. You can add them from the group page later.</p>
        <div className="flex justify-end">
          <button type="button" className="btn" onClick={() => navigate(`/groups/${partial.group.id}`)}>
            Open the group
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title="New group" onClose={onClose} busy={isSubmitting}>
      <form noValidate onSubmit={onSubmit} className="grid gap-4">
        {formError && (
          <p role="alert" className="alert alert-error m-0">
            {formError}
          </p>
        )}
        <TextField label="Name" placeholder="Goa trip" error={errors.name?.message} {...register('name')} />
        <div className="flex flex-col gap-1.5">
          <label htmlFor="group-currency" className="label">
            Currency
          </label>
          <select id="group-currency" className="input" {...register('currency')}>
            {CURRENCIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <p className="m-0 text-xs text-muted">Every expense in the group uses this currency. It cannot be changed later.</p>
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="group-members" className="label">
            Add people (optional)
          </label>
          <textarea
            id="group-members"
            className="input min-h-20"
            placeholder="bob@example.com, carol@example.com"
            aria-invalid={errors.members ? true : undefined}
            aria-describedby={errors.members ? 'group-members-error' : 'group-members-hint'}
            {...register('members')}
          />
          {errors.members ? (
            <p id="group-members-error" role="alert" className="m-0 text-sm font-semibold text-negative">
              {errors.members.message}
            </p>
          ) : (
            <p id="group-members-hint" className="m-0 text-xs text-muted">
              Their email addresses, separated by commas or new lines. They need an account.
            </p>
          )}
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </button>
          <button type="submit" className="btn" disabled={isSubmitting}>
            {isSubmitting ? 'Creating...' : 'Create group'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
