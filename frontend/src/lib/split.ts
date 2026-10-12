/**
 * The expense split rules, mirrored from the API (com.splitwise.split) so the expense form can show each
 * person's share while it is typed. Everything is done in whole paise with BigInt, never with floating point,
 * so the preview always matches what the API will save. split.golden.test.ts replays 400 cases produced by the
 * real backend to prove it.
 *
 * Inputs are the raw text from the form; an empty value means "not entered".
 */

export type SplitType = 'EQUAL' | 'EXACT' | 'PERCENTAGE' | 'SHARES' | 'ADJUSTMENT'

export type SplitInput = { userId: number; value: string | null }

export type SplitResult =
  | { ok: true; owed: Map<number, bigint> }
  | { ok: false; error: string; userId?: number }

/** A non-negative or negative decimal as an integer and a number of decimal places: 12.50 -> 1250n, 2. */
type Decimal = { units: bigint; scale: number }

const DECIMAL = /^-?(\d+(\.\d*)?|\.\d+)$/
const MAX_SHARES: Decimal = { units: 1_000_000n, scale: 0 }
const HUNDRED: Decimal = { units: 100n, scale: 0 }

function parse(text: string | null): Decimal | null {
  const trimmed = text?.trim() ?? ''
  if (!DECIMAL.test(trimmed)) return null
  const negative = trimmed.startsWith('-')
  const [whole = '', fraction = ''] = trimmed.replace('-', '').split('.')
  const units = BigInt((whole || '0') + fraction)
  return strip({ units: negative ? -units : units, scale: fraction.length })
}

/** 12.500 -> 12.5; the scale that matters is the one after trailing zeros are removed. */
function strip(d: Decimal): Decimal {
  let { units, scale } = d
  while (scale > 0 && units % 10n === 0n) {
    units /= 10n
    scale--
  }
  return { units, scale }
}

function rescale(d: Decimal, scale: number): bigint {
  return d.units * 10n ** BigInt(scale - d.scale)
}

function compare(a: Decimal, b: Decimal): number {
  const scale = Math.max(a.scale, b.scale)
  const x = rescale(a, scale)
  const y = rescale(b, scale)
  return x < y ? -1 : x > y ? 1 : 0
}

function add(a: Decimal, b: Decimal): Decimal {
  const scale = Math.max(a.scale, b.scale)
  return strip({ units: rescale(a, scale) + rescale(b, scale), scale })
}

/** Only for values already known to have at most 2 decimals. */
function toPaise(d: Decimal): bigint {
  return rescale(d, 2)
}

export function paiseToString(paise: bigint): string {
  const negative = paise < 0n
  const abs = negative ? -paise : paise
  const text = `${abs / 100n}.${(abs % 100n).toString().padStart(2, '0')}`
  return negative ? `-${text}` : text
}

function decimalToString(d: Decimal): string {
  if (d.scale === 0) return d.units.toString()
  const negative = d.units < 0n
  const digits = (negative ? -d.units : d.units).toString().padStart(d.scale + 1, '0')
  const text = `${digits.slice(0, -d.scale)}.${digits.slice(-d.scale)}`
  return negative ? `-${text}` : text
}

/** Equal parts; leftover paise go one each to the first ids in order (100.00 / 3 = 33.34, 33.33, 33.33). */
function equal(total: bigint, ids: number[]): Map<number, bigint> {
  const n = BigInt(ids.length)
  const base = total / n
  const remainder = Number(total % n)
  return new Map(ids.map((id, i) => [id, base + (i < remainder ? 1n : 0n)]))
}

/**
 * Parts proportional to the weights by the largest-remainder method: round each part down to whole paise, then
 * hand the lost paise one each to the largest remainders (ties: first id). A zero weight never gets a paisa.
 */
function proportional(total: bigint, ids: number[], weights: Decimal[]): Map<number, bigint> {
  const scale = Math.max(0, ...weights.map((w) => w.scale))
  const intWeights = weights.map((w) => rescale(w, scale))
  const weightSum = intWeights.reduce((a, b) => a + b, 0n)
  const parts = intWeights.map((w, index) => ({ index, floor: (total * w) / weightSum, remainder: (total * w) % weightSum }))
  let leftover = total - parts.reduce((a, p) => a + p.floor, 0n)
  const byRemainder = [...parts].sort((a, b) =>
    a.remainder === b.remainder ? a.index - b.index : a.remainder > b.remainder ? -1 : 1,
  )
  const result = parts.map((p) => p.floor)
  for (const part of byRemainder) {
    if (leftover === 0n) break
    result[part.index]! += 1n
    leftover--
  }
  return new Map(ids.map((id, i) => [id, result[i]!]))
}

type Named = (userId: number) => string

const defaultName: Named = (id) => `user ${id}`

/** Computes what each person owes, or why the input would be refused by the API. */
export function computeSplit(
  type: SplitType,
  totalText: string,
  participants: SplitInput[],
  nameOf: Named = defaultName,
): SplitResult {
  const total = parse(totalText)
  if (!total) return { ok: false, error: 'Enter the amount, like 1200 or 99.50' }
  if (total.units <= 0n) return { ok: false, error: 'The amount must be more than zero' }
  if (total.scale > 2) return { ok: false, error: 'The amount can have at most 2 decimal places' }
  if (participants.length === 0) return { ok: false, error: 'Pick at least one person to split with' }
  const seen = new Set<number>()
  for (const p of participants) {
    if (seen.has(p.userId)) return { ok: false, error: `${nameOf(p.userId)} appears twice`, userId: p.userId }
    seen.add(p.userId)
  }

  const sorted = [...participants].sort((a, b) => a.userId - b.userId)
  const ids = sorted.map((p) => p.userId)
  const totalPaise = toPaise(total)

  /** A value that must be present and not negative; otherwise the error to show. */
  function required(p: SplitInput, what: string): Decimal | SplitResult {
    const text = p.value?.trim() ?? ''
    if (text === '') return { ok: false, error: `Enter ${what} for ${nameOf(p.userId)}`, userId: p.userId }
    const value = parse(text)
    if (!value) return { ok: false, error: `${capital(what)} for ${nameOf(p.userId)} is not a number`, userId: p.userId }
    if (value.units < 0n) {
      return { ok: false, error: `${capital(what)} for ${nameOf(p.userId)} cannot be negative`, userId: p.userId }
    }
    return value
  }

  switch (type) {
    case 'EQUAL':
      return { ok: true, owed: equal(totalPaise, ids) }

    case 'EXACT': {
      const owed = new Map<number, bigint>()
      let sum = 0n
      for (const p of sorted) {
        const value = required(p, 'an amount')
        if (!isDecimal(value)) return value
        if (value.scale > 2) {
          return { ok: false, error: `The amount for ${nameOf(p.userId)} can have at most 2 decimal places`, userId: p.userId }
        }
        owed.set(p.userId, toPaise(value))
        sum += toPaise(value)
      }
      if (sum !== totalPaise) {
        const gap = totalPaise - sum
        return {
          ok: false,
          error: gap > 0n
            ? `${paiseToString(gap)} still to assign (the amounts add up to ${paiseToString(sum)})`
            : `${paiseToString(-gap)} too much (the amounts add up to ${paiseToString(sum)})`,
        }
      }
      return { ok: true, owed }
    }

    case 'PERCENTAGE': {
      let sum: Decimal = { units: 0n, scale: 0 }
      const weights: Decimal[] = []
      for (const p of sorted) {
        const value = required(p, 'a percentage')
        if (!isDecimal(value)) return value
        if (compare(value, HUNDRED) > 0) {
          return { ok: false, error: `The percentage for ${nameOf(p.userId)} cannot be more than 100`, userId: p.userId }
        }
        if (value.scale > 4) {
          return { ok: false, error: `The percentage for ${nameOf(p.userId)} can have at most 4 decimal places`, userId: p.userId }
        }
        sum = add(sum, value)
        weights.push(value)
      }
      if (compare(sum, HUNDRED) !== 0) {
        return { ok: false, error: `The percentages add up to ${decimalToString(sum)}%, they must add up to 100%` }
      }
      return { ok: true, owed: proportional(totalPaise, ids, weights) }
    }

    case 'SHARES': {
      let sum: Decimal = { units: 0n, scale: 0 }
      const weights: Decimal[] = []
      for (const p of sorted) {
        const value = required(p, 'a number of shares')
        if (!isDecimal(value)) return value
        if (value.scale > 2) {
          return { ok: false, error: `Shares for ${nameOf(p.userId)} can have at most 2 decimal places`, userId: p.userId }
        }
        if (compare(value, MAX_SHARES) > 0) {
          return { ok: false, error: `Shares for ${nameOf(p.userId)} cannot be more than 1,000,000`, userId: p.userId }
        }
        sum = add(sum, value)
        weights.push(value)
      }
      if (sum.units === 0n) return { ok: false, error: 'Give at least one person more than 0 shares' }
      return { ok: true, owed: proportional(totalPaise, ids, weights) }
    }

    case 'ADJUSTMENT': {
      const adjustments = new Map<number, bigint>()
      let sum = 0n
      for (const p of sorted) {
        let paise = 0n
        if ((p.value?.trim() ?? '') !== '') {
          const value = required(p, 'an adjustment')
          if (!isDecimal(value)) return value
          if (value.scale > 2) {
            return { ok: false, error: `The adjustment for ${nameOf(p.userId)} can have at most 2 decimal places`, userId: p.userId }
          }
          paise = toPaise(value)
        }
        adjustments.set(p.userId, paise)
        sum += paise
      }
      if (sum > totalPaise) {
        return {
          ok: false,
          error: `The adjustments add up to ${paiseToString(sum)}, more than the amount (${paiseToString(totalPaise)})`,
        }
      }
      const rest = totalPaise - sum
      if (rest === 0n) return { ok: true, owed: adjustments }
      const parts = equal(rest, ids)
      return { ok: true, owed: new Map(ids.map((id) => [id, parts.get(id)! + adjustments.get(id)!])) }
    }
  }
}

function isDecimal(value: Decimal | SplitResult): value is Decimal {
  return 'units' in value
}

function capital(text: string): string {
  return text.replace(/^(a |an )/, '').replace(/^./, (c) => c.toUpperCase())
}
