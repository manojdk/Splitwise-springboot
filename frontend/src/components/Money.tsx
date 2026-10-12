import { formatMoney, isZero } from '../lib/money'

/**
 * An amount coloured by direction: green when you are owed (positive), red when you owe (negative).
 * absolute shows the size only, for text that already says the direction ("You owe ₹200.00").
 */
export function Money({ amount, currency, signed = false, absolute = false, className = '' }: {
  amount: number
  currency: string
  signed?: boolean
  absolute?: boolean
  className?: string
}) {
  const tone = isZero(amount) ? 'text-muted' : amount > 0 ? 'text-positive' : 'text-negative'
  return (
    <span className={`font-head font-bold tabular-nums whitespace-nowrap ${tone} ${className}`}>
      {formatMoney(absolute ? Math.abs(amount) : amount, currency, { signed: signed && !absolute })}
    </span>
  )
}
