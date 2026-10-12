import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './AuthProvider'

/** Gate for signed-in pages: sends guests to sign in and brings them back afterwards. */
export function RequireAuth() {
  const { status, retry, logout } = useAuth()
  const location = useLocation()

  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location }} />

  if (status === 'loading') {
    return (
      <div className="grid min-h-screen place-items-center" role="status">
        <span className="brand text-xl">Loading...</span>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="grid min-h-screen place-items-center p-6">
        <div className="card grid max-w-sm gap-4 p-7 text-center" role="alert">
          <h2 className="m-0 text-lg">Can&apos;t load your account</h2>
          <p className="m-0 text-muted">The server did not answer. Check your connection and try again.</p>
          <div className="flex justify-center gap-3">
            <button type="button" className="btn" onClick={retry}>
              Try again
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => logout()}>
              Sign out
            </button>
          </div>
        </div>
      </div>
    )
  }

  return <Outlet />
}
