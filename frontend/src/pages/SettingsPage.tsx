import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { useChangePassword, useSignOutEverywhere, useUpdateProfile } from '../api/queries'
import { useAuth } from '../auth/AuthProvider'
import { newPassword } from '../auth/passwordRules'
import { TextField } from '../components/TextField'
import { useToast } from '../components/Toast'

/** The signed-in user's own account: name, password, and signing out every device. */
export function SettingsPage() {
  return (
    <div className="grid max-w-2xl gap-5">
      <h1 className="m-0 text-2xl">Settings</h1>
      <ProfileSection />
      <PasswordSection />
      <DevicesSection />
    </div>
  )
}

const profileSchema = z.object({
  name: z.string().trim().min(1, 'Enter your name').max(100, 'Use at most 100 characters'),
})

function ProfileSection() {
  const { user } = useAuth()
  const toast = useToast()
  const update = useUpdateProfile()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<z.infer<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    defaultValues: { name: user?.name ?? '' },
  })

  const onSubmit = handleSubmit(async ({ name }) => {
    try {
      const saved = await update.mutateAsync({ name })
      reset({ name: saved.name })
      toast.show('Name saved')
    } catch {
      // Shown below from update.error
    }
  })

  return (
    <section className="card p-5" aria-labelledby="profile-heading">
      <h2 id="profile-heading" className="mt-0 mb-3 text-base">
        Profile
      </h2>
      <form noValidate onSubmit={onSubmit} className="grid gap-4">
        <TextField label="Name" autoComplete="name" error={errors.name?.message} {...register('name')} />
        <div className="flex flex-col gap-1.5">
          <span className="label">Email</span>
          <span>{user?.email}</span>
          <span className="text-xs text-muted">The email you sign in with cannot be changed yet.</span>
        </div>
        {update.error && (
          <p role="alert" className="alert alert-error m-0">
            {update.error.message}
          </p>
        )}
        <div>
          <button type="submit" className="btn" disabled={update.isPending || !isDirty}>
            {update.isPending ? 'Saving...' : 'Save name'}
          </button>
        </div>
      </form>
    </section>
  )
}

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword,
    confirmPassword: z.string(),
  })
  .refine((form) => form.newPassword === form.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  })
  .refine((form) => form.newPassword !== form.currentPassword, {
    path: ['newPassword'],
    message: 'Choose a password different from the current one',
  })

function PasswordSection() {
  const { renew } = useAuth()
  const toast = useToast()
  const change = useChangePassword()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    setFormError(null)
    try {
      // The change ends every session, this one too; carry on with the token that comes back
      renew(await change.mutateAsync({ currentPassword, newPassword }))
      reset()
      toast.show('Password changed. Your other devices were signed out.')
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) {
        setError('currentPassword', { message: 'That is not your current password' })
      } else {
        setFormError(error instanceof Error ? error.message : 'Could not change the password. Please try again.')
      }
    }
  })

  return (
    <section className="card p-5" aria-labelledby="password-heading">
      <h2 id="password-heading" className="mt-0 mb-1 text-base">
        Password
      </h2>
      <p className="mt-0 mb-3 text-sm text-muted">Changing it signs you out on every other device.</p>
      <form noValidate onSubmit={onSubmit} className="grid gap-4">
        <TextField
          label="Current password"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <TextField
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        {formError && (
          <p role="alert" className="alert alert-error m-0">
            {formError}
          </p>
        )}
        <div>
          <button type="submit" className="btn" disabled={change.isPending}>
            {change.isPending ? 'Changing...' : 'Change password'}
          </button>
        </div>
      </form>
    </section>
  )
}

function DevicesSection() {
  const { logout } = useAuth()
  const signOut = useSignOutEverywhere()
  const [confirming, setConfirming] = useState(false)

  async function onConfirm() {
    try {
      await signOut.mutateAsync()
      logout('You signed out of all devices.')
    } catch {
      // Shown below from signOut.error
    }
  }

  return (
    <section className="card p-5" aria-labelledby="devices-heading">
      <h2 id="devices-heading" className="mt-0 mb-1 text-base">
        Devices
      </h2>
      <p className="mt-0 mb-3 text-sm text-muted">
        Signed in on a computer you no longer use, or lost a phone? This signs out every device, this one too.
      </p>
      {confirming ? (
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-semibold">Sign out everywhere?</span>
          <button type="button" className="btn btn-ghost" onClick={() => setConfirming(false)} disabled={signOut.isPending}>
            Cancel
          </button>
          <button type="button" className="btn btn-accent" onClick={onConfirm} disabled={signOut.isPending}>
            {signOut.isPending ? 'Signing out...' : 'Yes, sign out everywhere'}
          </button>
        </div>
      ) : (
        <button type="button" className="btn btn-ghost" onClick={() => setConfirming(true)}>
          Sign out of all devices
        </button>
      )}
      {signOut.error && (
        <p role="alert" className="alert alert-error mt-3 mb-0">
          {signOut.error.message}
        </p>
      )}
    </section>
  )
}
