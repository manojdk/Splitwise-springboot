import { describe, expect, it } from 'vitest'
import { clearSession, readSession, writeSession } from './session'

const KEY = 'splitwise-session'

describe('session storage', () => {
  it('reads back a session that has not expired', () => {
    writeSession({ token: 't', expiresAt: 2_000 })
    expect(readSession(1_000)).toEqual({ token: 't', expiresAt: 2_000 })
  })

  it('drops an expired session from storage', () => {
    writeSession({ token: 't', expiresAt: 2_000 })
    expect(readSession(2_000)).toBeNull()
    expect(sessionStorage.getItem(KEY)).toBeNull()
  })

  it.each([
    ['not JSON', '{oops'],
    ['no token', JSON.stringify({ expiresAt: 9e15 })],
    ['a text expiry', JSON.stringify({ token: 't', expiresAt: 'tomorrow' })],
  ])('ignores a stored value that is %s', (_, raw) => {
    sessionStorage.setItem(KEY, raw)
    expect(readSession(1_000)).toBeNull()
  })

  it('clears the session', () => {
    writeSession({ token: 't', expiresAt: 2_000 })
    clearSession()
    expect(readSession(1_000)).toBeNull()
  })
})
