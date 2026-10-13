import { useState } from 'react'
import { ApiError, downloadFile } from '../../api/client'
import { useGroupReport } from '../../api/queries'
import type { Group } from '../../api/types'
import { Money } from '../../components/Money'
import { ErrorState, Loading } from '../../components/States'
import { categoryOf } from '../../lib/categories'
import { formatMoney } from '../../lib/money'

/** Slice colours, from the theme so every theme has its own palette. */
const SLICES = ['var(--primary)', 'var(--accent)', 'var(--positive)', 'var(--warn)', 'var(--negative)', 'var(--muted)']

/** Where the money went in a group over a date range: by category and by member; CSV export of the same range. */
export function GroupReport({ group }: { group: Group }) {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const report = useGroupReport(group.id, from || undefined, to || undefined)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  async function exportCsv() {
    setExporting(true)
    setExportError(null)
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    try {
      await downloadFile(`/groups/${group.id}/expenses.csv${params.size ? `?${params}` : ''}`, `${group.name}-expenses.csv`)
    } catch (error) {
      setExportError(error instanceof ApiError ? error.message : 'Could not export. Please try again.')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm text-muted">
          From
          <input type="date" className="input py-1.5" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1 text-sm text-muted">
          To
          <input type="date" className="input py-1.5" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button type="button" className="btn btn-ghost ml-auto" onClick={exportCsv} disabled={exporting}>
          {exporting ? 'Exporting...' : 'Export CSV'}
        </button>
      </div>
      {exportError && (
        <p role="alert" className="alert alert-error m-0">
          {exportError}
        </p>
      )}

      {report.isPending ? (
        <Loading label="Loading report" />
      ) : report.isError ? (
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      ) : report.data.expenseCount === 0 ? (
        <p className="m-0 text-muted">No expenses {from || to ? 'in these dates' : 'yet'}.</p>
      ) : (
        <>
          <section aria-label="By category" className="flex flex-col items-center gap-6 sm:flex-row">
            <Donut
              slices={report.data.byCategory.map((c) => c.total)}
              label={formatMoney(report.data.totalSpent, report.data.currency)}
              count={report.data.expenseCount}
            />
            <ul className="m-0 w-full flex-1 list-none p-0">
              {report.data.byCategory.map((c, i) => (
                <li key={c.category} className="flex items-center gap-3 border-b border-line py-2 last:border-0">
                  <span className="h-3 w-3 rounded" style={{ background: SLICES[i % SLICES.length] }} aria-hidden="true" />
                  <span className="flex-1">
                    {categoryOf(c.category).label}{' '}
                    <span className="text-xs text-muted">
                      ({c.count} {c.count === 1 ? 'expense' : 'expenses'}, {Math.round((c.total / report.data.totalSpent) * 100)}%)
                    </span>
                  </span>
                  <span className="font-head font-bold tabular-nums">{formatMoney(c.total, report.data.currency)}</span>
                </li>
              ))}
            </ul>
          </section>

          <section aria-label="By member">
            <h3 className="mt-0 mb-2 text-sm">By member</h3>
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="text-left text-xs text-muted uppercase">
                    <th className="py-2 font-semibold">Member</th>
                    <th className="py-2 text-right font-semibold">Paid</th>
                    <th className="py-2 text-right font-semibold">Their share</th>
                    <th className="py-2 text-right font-semibold">Net</th>
                  </tr>
                </thead>
                <tbody>
                  {report.data.byMember.map((m) => (
                    <tr key={m.userId} className="border-t border-line">
                      <td className="py-2 font-semibold">{m.name}</td>
                      <td className="py-2 text-right tabular-nums">{formatMoney(m.paid, report.data.currency)}</td>
                      <td className="py-2 text-right tabular-nums">{formatMoney(m.share, report.data.currency)}</td>
                      <td className="py-2 text-right">
                        <Money amount={m.net} currency={report.data.currency} signed />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mb-0 text-xs text-muted">Net is what they paid minus their share: positive means they paid for others.</p>
          </section>
        </>
      )}
    </div>
  )
}

/** A CSS donut (conic gradient), no chart library; the legend next to it carries the numbers. */
function Donut({ slices, label, count }: { slices: number[]; label: string; count: number }) {
  const total = slices.reduce((a, b) => a + b, 0)
  // Each slice starts where the ones before it end
  const stops = slices.map((value, i) => {
    const start = (slices.slice(0, i).reduce((a, b) => a + b, 0) / total) * 100
    const end = start + (value / total) * 100
    return `${SLICES[i % SLICES.length]} ${start}% ${end}%`
  })
  return (
    <div
      className="relative grid h-44 w-44 flex-none place-items-center rounded-full"
      style={{ background: `conic-gradient(${stops.join(', ')})` }}
      role="img"
      aria-label={`Total spent ${label} across ${count} ${count === 1 ? 'expense' : 'expenses'}`}
    >
      <div className="grid h-28 w-28 place-items-center rounded-full bg-[var(--surface-solid)] text-center">
        <div>
          <p className="m-0 text-[11px] text-muted uppercase">Total</p>
          <p className="m-0 font-head text-base font-bold tabular-nums">{label}</p>
        </div>
      </div>
    </div>
  )
}
