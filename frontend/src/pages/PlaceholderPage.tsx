/** Stand-in for screens that are not built yet; keeps the navigation complete. */
export function PlaceholderPage({ title, description }: { title: string; description: string }) {
  return (
    <div className="grid gap-4">
      <h1 className="m-0 text-2xl">{title}</h1>
      <section className="card p-6">
        <p className="m-0 text-muted">{description}</p>
      </section>
    </div>
  )
}
