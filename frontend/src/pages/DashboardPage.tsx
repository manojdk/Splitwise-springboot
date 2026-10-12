import { useAuth } from '../auth/AuthProvider'

export function DashboardPage() {
  const { user } = useAuth()
  const firstName = user?.name.trim().split(/\s+/)[0] ?? ''

  return (
    <div className="grid gap-4">
      <h1 className="m-0 text-2xl">Welcome, {firstName}</h1>
      <section className="card hero-panel border-0 p-6">
        <p className="m-0 text-xs font-semibold tracking-[0.14em] uppercase opacity-80">Your balances</p>
        <p className="mt-2 mb-0 font-head text-2xl font-bold">All settled up</p>
        <p className="mt-2 mb-0 opacity-85">Add a group or an expense, and who owes whom shows up here.</p>
      </section>
    </div>
  )
}
