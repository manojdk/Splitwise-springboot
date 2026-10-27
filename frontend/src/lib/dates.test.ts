import { describe, expect, it } from 'vitest'
import { addYears, dateWindowError, latestDate } from './dates'

describe('the date window the API accepts', () => {
  const now = new Date(2026, 9, 27, 10, 0)

  it('runs from 2000 to a year from today', () => {
    expect(latestDate(now)).toBe('2027-10-27')
    expect(dateWindowError('2000-01-01', now)).toBeNull()
    expect(dateWindowError('2027-10-27', now)).toBeNull()
    expect(dateWindowError('1999-12-31', now)).toBe('Use a date on or after 1 Jan 2000')
    expect(dateWindowError('2027-10-28', now)).toBe('Use a date at most a year from today')
    expect(dateWindowError('2099-01-01', now)).toBe('Use a date at most a year from today')
  })

  it('moves 29 February to the 28th, like the server', () => {
    expect(addYears('2028-02-29', 1)).toBe('2029-02-28')
    expect(addYears('2028-02-29', 4)).toBe('2032-02-29')
    expect(addYears('2026-12-31', 50)).toBe('2076-12-31')
  })
})
