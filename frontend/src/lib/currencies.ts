/** Currencies offered for a new group; the API accepts any ISO 4217 code. */
export const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'CAD', 'AUD', 'SGD', 'JPY', 'CHF'] as const

/**
 * Currencies with no smaller unit, so amounts in them are whole numbers (100 yen among three is 34, 33, 33).
 * The same list as Java's (Currency.getDefaultFractionDigits() == 0), retired ones included; the API's
 * WholeUnitCurrencyIntegrationTest fails if the two drift apart.
 */
export const NO_DECIMALS: readonly string[] = [
  'ADP', 'BEF', 'BIF', 'BYB', 'BYR', 'CLP', 'DJF', 'ESP', 'GNF', 'GRD', 'ISK', 'ITL', 'JPY', 'KMF', 'KRW',
  'LUF', 'MGF', 'PTE', 'PYG', 'ROL', 'RWF', 'TPE', 'TRL', 'UGX', 'UYI', 'VND', 'VUV', 'XAF', 'XOF', 'XPF',
]

/** Decimal places amounts may have in the API: 0 for yen and the like, 2 for every other currency. */
export function currencyDecimals(currency: string): 0 | 2 {
  return NO_DECIMALS.includes(currency) ? 0 : 2
}
