import { describe, expect, it } from 'vitest'
import { redirectTarget } from './redirect'

describe('redirectTarget', () => {
  it('returns to a page on this site', () => {
    expect(redirectTarget({ from: { pathname: '/groups/4' } })).toBe('/groups/4')
  })

  it.each([
    ['no state', null],
    ['no path', { from: {} }],
    ['another site', { from: { pathname: '//evil.example.com' } }],
    ['a full URL', { from: { pathname: 'https://evil.example.com' } }],
  ])('falls back to the dashboard for %s', (_, state) => {
    expect(redirectTarget(state)).toBe('/')
  })
})
