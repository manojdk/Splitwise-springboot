import { ActivityFeed } from '../features/activity/ActivityFeed'

export function ActivityPage() {
  return (
    <div className="grid gap-5">
      <h1 className="m-0 text-2xl">Activity</h1>
      <section className="card p-5" aria-label="Activity">
        <ActivityFeed />
      </section>
    </div>
  )
}
