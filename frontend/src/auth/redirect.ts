/**
 * Where the user was going before being sent to sign in. Only same-site paths are accepted, so a crafted link
 * cannot bounce the user to another site after sign-in.
 */
export function redirectTarget(state: unknown): string {
  const from = (state as { from?: { pathname?: unknown } } | null)?.from?.pathname
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}
