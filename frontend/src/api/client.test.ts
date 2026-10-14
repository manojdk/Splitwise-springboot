import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from '../test/server'
import { api, ApiError, configureApi } from './client'

afterEach(() => configureApi({ getToken: () => null, onUnauthorized: () => {} }))

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  const error = await promise.then(
    () => null,
    (e: unknown) => e,
  )
  expect(error).toBeInstanceOf(ApiError)
  return error as ApiError
}

describe('api client', () => {
  it('sends the token and JSON body, and parses the reply', async () => {
    let seen: { auth: string | null; type: string | null; body: unknown } | undefined
    server.use(
      http.post('*/api/things', async ({ request }) => {
        seen = { auth: request.headers.get('Authorization'), type: request.headers.get('Content-Type'), body: await request.json() }
        return HttpResponse.json({ id: 7 }, { status: 201 })
      }),
    )
    configureApi({ getToken: () => 'abc', onUnauthorized: () => {} })

    await expect(api('/things', { method: 'POST', body: { name: 'x' } })).resolves.toEqual({ id: 7 })
    expect(seen).toEqual({ auth: 'Bearer abc', type: 'application/json', body: { name: 'x' } })
  })

  it("tells the API the user's time zone, so its \"today\" is theirs", async () => {
    let zone: string | null = null
    server.use(
      http.get('*/api/things', ({ request }) => {
        zone = request.headers.get('X-Time-Zone')
        return HttpResponse.json([])
      }),
    )
    await api('/things')
    expect(zone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone)
  })

  it('returns nothing for 204', async () => {
    server.use(http.delete('*/api/things/1', () => new HttpResponse(null, { status: 204 })))
    await expect(api('/things/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it("uses the API's error message", async () => {
    server.use(http.get('*/api/things', () => HttpResponse.json({ message: 'Group not found' }, { status: 404 })))
    const error = await failure(api('/things'))
    expect(error.status).toBe(404)
    expect(error.message).toBe('Group not found')
  })

  it('falls back to a friendly message when the error body is not JSON', async () => {
    server.use(http.get('*/api/things', () => new HttpResponse('<html>Bad gateway</html>', { status: 502 })))
    expect((await failure(api('/things'))).message).toBe('Something went wrong on our side. Please try again.')
  })

  it('reports a rejected token, but not a failed sign-in', async () => {
    const onUnauthorized = vi.fn()
    server.use(http.get('*/api/things', () => HttpResponse.json({ message: 'Missing or invalid access token' }, { status: 401 })))

    configureApi({ getToken: () => null, onUnauthorized })
    await failure(api('/things'))
    expect(onUnauthorized).not.toHaveBeenCalled()

    configureApi({ getToken: () => 'expired', onUnauthorized })
    await failure(api('/things'))
    expect(onUnauthorized).toHaveBeenCalledExactlyOnceWith('Missing or invalid access token')
  })
})
