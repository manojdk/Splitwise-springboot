/** Today as YYYY-MM-DD in the user's own time zone (toISOString would give the UTC date). */
export function today(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** The first date the API accepts for an expense or payment (PlausibleDate on the server). */
export const EARLIEST_DATE = '2000-01-01'

/** How far after the start a recurring expense may end (the API's limit). */
export const MAX_RECURRING_YEARS = 50

/** The date `years` later, keeping the day; 29 Feb becomes 28 Feb, as on the server (LocalDate.plusYears). */
export function addYears(isoDate: string, years: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const lastDay = new Date(year! + years, month!, 0).getDate()
  return `${year! + years}-${String(month).padStart(2, '0')}-${String(Math.min(day!, lastDay)).padStart(2, '0')}`
}

/** The last date the API accepts for an expense or payment: a year from today. */
export function latestDate(now: Date = new Date()): string {
  return addYears(today(now), 1)
}

/** Why the API would refuse this expense or payment date, or null; catches typos like 2099 or 1925. */
export function dateWindowError(isoDate: string, now: Date = new Date()): string | null {
  if (isoDate < EARLIEST_DATE) return 'Use a date on or after 1 Jan 2000'
  if (isoDate > latestDate(now)) return 'Use a date at most a year from today'
  return null
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
