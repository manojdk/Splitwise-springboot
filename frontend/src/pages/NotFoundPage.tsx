import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="grid min-h-screen place-items-center p-6">
      <div className="card grid max-w-sm gap-3 p-7 text-center">
        <p className="brand m-0 text-5xl">404</p>
        <h1 className="m-0 text-lg">This page does not exist</h1>
        <Link to="/" className="btn">
          Go to the dashboard
        </Link>
      </div>
    </div>
  )
}
