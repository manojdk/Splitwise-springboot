/** Today as YYYY-MM-DD in the user's own time zone (toISOString would give the UTC date). */
export function today(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** "2026-09-12" -> "12 Sep 2026", without time-zone shifts. */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year!, month! - 1, day!).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

/** "just now", "5 min ago", "3 h ago", "yesterday", then the date. */
export function relativeTime(iso: string, now: Date = new Date()): string {
  const then = new Date(iso)
  const minutes = Math.floor((now.getTime() - then.getTime()) / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  if (minutes < 24 * 60 && then.getDate() === now.getDate()) return `${Math.floor(minutes / 60)} h ago`
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (then.toDateString() === yesterday.toDateString()) return 'yesterday'
  return then.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}
