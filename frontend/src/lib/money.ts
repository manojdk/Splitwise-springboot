/**
 * Money display. Amounts arrive from the API as JSON numbers with at most two decimals; they are only shown,
 * never used for arithmetic beyond adding a handful of them (see sumByCurrency), so floating point is safe
 * here as long as results are rounded to cents.
 */

const formatters = new Map<string, Intl.NumberFormat>()

function formatter(currency: string): Intl.NumberFormat {
  let format = formatters.get(currency)
  if (!format) {
    format = new Intl.NumberFormat(undefined, { style: 'currency', currency, currencyDisplay: 'narrowSymbol' })
    // Yen shows no decimals, but one saved before amounts had to be whole (100.50) must not show as 101
    if (format.resolvedOptions().maximumFractionDigits === 0) {
      format = new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency,
        currencyDisplay: 'narrowSymbol',
        maximumFractionDigits: 2,
      })
    }
    formatters.set(currency, format)
  }
  return format
}

/** "₹1,200.00"; with signed, "+₹1,200.00" / "-₹1,200.00" (zero has no sign). */
export function formatMoney(amount: number, currency: string, options: { signed?: boolean } = {}): string {
  const text = formatter(currency).format(Math.abs(amount))
  if (!options.signed || isZero(amount)) return amount < 0 && !isZero(amount) ? `-${text}` : text
  return amount > 0 ? `+${text}` : `-${text}`
}

/** True below half a cent, so float noise never shows as "-₹0.00". */
export function isZero(amount: number): boolean {
  return Math.abs(amount) < 0.005
}

export function roundCents(amount: number): number {
  return Math.round(amount * 100) / 100
}
