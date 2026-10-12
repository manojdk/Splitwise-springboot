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

const schema = z.object({
  email: z.string().trim().min(1, 'Enter your email').pipe(z.email('Enter a valid email address')),
  password: z.string().min(1, 'Enter your password'),
})
type LoginForm = z.infer<typeof schema>

export function LoginPage() {
  const { status, notice, login } = useAuth()
  const location = useLocation()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(schema) })

  if (status === 'authenticated') return <Navigate to={redirectTarget(location.state)} replace />

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await login(values)
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Sign-in failed. Please try again.')
    }
  })

  return (
    <AuthLayout title="Sign in">
      {notice && !formError && (
        <p role="status" className="alert alert-info">
          {notice}
        </p>
      )}
      {formError && (
        <p role="alert" className="alert alert-error">
          {formError}
        </p>
      )}
      <form noValidate onSubmit={onSubmit} className="grid gap-4">
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <button type="submit" className="btn" disabled={isSubmitting}>
          {isSubmitting ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
      <p className="text-center text-sm text-muted">
        New here?{' '}
        <Link to="/register" state={location.state} className="font-bold text-primary">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  )
}
