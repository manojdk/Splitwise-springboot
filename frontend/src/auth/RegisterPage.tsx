import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { TextField } from '../components/TextField'
import { AuthLayout } from './AuthLayout'
import { useAuth } from './AuthProvider'
import { redirectTarget } from './redirect'

// Same limits as the API's RegisterRequest, so most mistakes are caught before a round trip
const schema = z
  .object({
    name: z.string().trim().min(1, 'Enter your name').max(100, 'Use at most 100 characters'),
    email: z
      .string()
      .trim()
      .min(1, 'Enter your email')
      .max(255, 'Use at most 255 characters')
      .pipe(z.email('Enter a valid email address')),
    password: z.string().min(8, 'Use at least 8 characters').max(72, 'Use at most 72 characters'),
    confirmPassword: z.string(),
  })
  .refine((form) => form.password === form.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  })
type RegisterForm = z.infer<typeof schema>

export function RegisterPage() {
  const { status, register: createAccount } = useAuth()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({ resolver: zodResolver(schema) })

  if (status === 'authenticated') return <Navigate to={redirectTarget(location.state)} replace />

  const onSubmit = handleSubmit(async ({ name, email, password }) => {
    setFormError(null)
    try {
      await createAccount({ name, email, password })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setError('email', { message: 'This email is already registered. Sign in instead?' })
      } else {
        setFormError(error instanceof ApiError ? error.message : 'Could not create the account. Please try again.')
      }
    }
  })

  return (
    <AuthLayout title="Create account">
      {formError && (
        <p role="alert" className="alert alert-error">
          {formError}
        </p>
      )}
      <form noValidate onSubmit={onSubmit} className="grid gap-4">
        <TextField label="Name" autoComplete="name" error={errors.name?.message} {...register('name')} />
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <button type="submit" className="btn" disabled={isSubmitting}>
          {isSubmitting ? 'Creating account...' : 'Create account'}
        </button>
      </form>
      <p className="text-center text-sm text-muted">
        Already have an account?{' '}
        <Link to="/login" state={location.state} className="font-bold text-primary">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
