import { describe, expect, it } from 'vitest'
import cases from './split-golden.json'
import { computeSplit, paiseToString, type SplitType } from './split'

type GoldenCase = {
  type: SplitType
  total: string
  participants: { userId: number; value: string | null }[]
  result: Record<string, string> | null
}

/**
 * Replays split cases computed by the backend's own split strategies (SplitGoldenCasesTest writes the file).
 * Every case must give the same paise for every person, or be refused when the API refuses it.
 */
describe('split calculator matches the API', () => {
  it.each((cases as GoldenCase[]).map((c, i) => [i, c] as const))('case %i', (_, c) => {
    const result = computeSplit(c.type, c.total, c.participants)

    if (c.result === null) {
      expect(result.ok, `the API refuses this ${c.type} split`).toBe(false)
      return
    }
    expect(result.ok, result.ok ? '' : `refused: ${result.error}`).toBe(true)
    if (!result.ok) return
    const owed = Object.fromEntries([...result.owed].map(([id, paise]) => [String(id), paiseToString(paise)]))
    expect(owed).toEqual(c.result)
  })

  it('covers every split type, both accepted and refused', () => {
    const seen = new Set((cases as GoldenCase[]).map((c) => `${c.type}:${c.result === null ? 'refused' : 'ok'}`))
    for (const type of ['EQUAL', 'EXACT', 'PERCENTAGE', 'SHARES', 'ADJUSTMENT']) {
      expect(seen).toContain(`${type}:ok`)
      expect(seen).toContain(`${type}:refused`)
    }
  })
})
