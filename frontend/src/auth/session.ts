/**
 * The access token lives in sessionStorage: it survives a page reload but not closing the tab. The API has no
 * refresh tokens, so when the token expires the user signs in again.
 *
 * Trade-off: script running on the page could read it (XSS). The app never renders raw HTML, which is the guard.
 */

export type Session = { token: string; expiresAt: number }

const STORAGE_KEY = 'splitwise-session'

export function readSession(now: number = Date.now()): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<Session>
    if (typeof parsed.token !== 'string' || typeof parsed.expiresAt !== 'number' || parsed.expiresAt <= now) {
      sessionStorage.removeItem(STORAGE_KEY)
      return null
    }
    return { token: parsed.token, expiresAt: parsed.expiresAt }
  } catch {
    return null
  }
}

export function writeSession(session: Session): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // Storage blocked: the session is kept in memory only and ends on reload.
  }
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing stored
  }
}
