/**
 * Thin fetch wrapper for the Splitwise API: adds the bearer token, turns error bodies into ApiError, and reports
 * an expired or revoked token so the app can sign the user out.
 */

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

type ApiConfig = {
  getToken: () => string | null
  /** Called when a request that carried a token gets 401: the token expired or was revoked. */
  onUnauthorized: () => void
}

let config: ApiConfig = { getToken: () => null, onUnauthorized: () => {} }

export function configureApi(next: ApiConfig): void {
  config = next
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = config.getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(new URL(`/api${path}`, window.location.origin), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.')
  }

  if (response.status === 401 && token) config.onUnauthorized()
  if (!response.ok) throw new ApiError(response.status, await errorMessage(response))
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/** The API answers errors with {status, error, message, path}; fall back to a generic text otherwise. */
async function errorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: unknown }
    if (typeof body.message === 'string' && body.message.trim()) return body.message
  } catch {
    // Not JSON (a proxy error page, for example)
  }
  return response.status >= 500
    ? 'Something went wrong on our side. Please try again.'
    : `Request failed (${response.status})`
}
