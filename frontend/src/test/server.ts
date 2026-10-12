import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

/** A fake API with the same paths, shapes and error bodies as the Spring Boot backend. */
export const ALICE = { id: 1, name: 'Alice Smith', email: 'alice@example.com' }
export const ALICE_PASSWORD = 'password123'
export const VALID_TOKEN = 'token-alice'

export function errorBody(status: number, message: string, path: string) {
  return HttpResponse.json(
    { timestamp: new Date().toISOString(), status, error: 'Error', message, path },
    { status },
  )
}

export const handlers = [
  http.post('*/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string }
    if (body.email === ALICE.email && body.password === ALICE_PASSWORD) {
      return HttpResponse.json({ accessToken: VALID_TOKEN, tokenType: 'Bearer', expiresInSeconds: 3600 })
    }
    return errorBody(401, 'Invalid email or password', '/api/auth/login')
  }),

  http.post('*/api/auth/register', async ({ request }) => {
    const body = (await request.json()) as { name: string; email: string; password: string }
    if (body.email === 'taken@example.com') return errorBody(409, 'Email is already registered', '/api/auth/register')
    return HttpResponse.json({ id: 2, name: body.name, email: body.email }, { status: 201 })
  }),

  http.get('*/api/users/me', ({ request }) => {
    if (request.headers.get('Authorization') === `Bearer ${VALID_TOKEN}`) return HttpResponse.json(ALICE)
    return errorBody(401, 'Missing or invalid access token', '/api/users/me')
  }),
]

export const server = setupServer(...handlers)
