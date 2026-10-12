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
