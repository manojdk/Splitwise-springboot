import { ApiError } from '../api/client'
import { initials } from '../lib/initials'

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' }) {
  return (
    <div className={'avatar' + (size === 'sm' ? ' h-8 w-8 text-[11px]' : '')} aria-hidden="true">
      {initials(name)}
    </div>
  )
}

/** Placeholder blocks while data loads. */
export function Loading({ label, rows = 3 }: { label: string; rows?: number }) {
  return (
    <div role="status" aria-label={label} className="grid gap-3">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="h-12 animate-pulse rounded-xl bg-surface-2" />
      ))}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError ? error.message : 'Something went wrong.'
  return (
    <div role="alert" className="grid justify-items-start gap-3">
      <p className="alert alert-error m-0">{message}</p>
      {onRetry && (
        <button type="button" className="btn btn-ghost py-2 text-sm" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}
