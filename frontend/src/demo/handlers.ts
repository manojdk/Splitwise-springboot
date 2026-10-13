import { http, HttpResponse, type HttpResponseResolver } from 'msw'
import type {
  CreateExpenseRequest,
  CreateGroupRequest,
  CreateRecurringExpenseRequest,
  CreateSettlementRequest,
  RegisterRequest,
  UpdateExpenseRequest,
  User,
} from '../api/types'
import { DemoBackend, DemoError, type DemoState } from './backend'
import { seedState } from './seed'

/** Where the demo keeps its data: this browser only. */
export const STORAGE_KEY = 'splitwise-demo'

export function loadState(): DemoState {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as DemoState | null
    if (saved?.version === 1) return saved
  } catch {
    // Unreadable or blocked storage: start from the sample data
  }
  return seedState()
}

function saveState(state: DemoState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // Storage blocked or full: the demo still works until the page is closed
  }
}

export function resetDemo(): void {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing stored
  }
}

type Context = { me: User; params: Record<string, string>; url: URL; body: () => Promise<unknown> }

/**
 * MSW handlers that answer the app's API calls from the in-browser backend, with the same status codes and error
 * bodies as the real API. State is saved after every change.
 */
export function demoHandlers(backend: DemoBackend = new DemoBackend(loadState())) {
  /** Wraps one endpoint: signs in the caller (unless public), turns DemoError into the API's error body, saves. */
  const route =
    (handler: (ctx: Context) => unknown, options: { status?: number; isPublic?: boolean } = {}): HttpResponseResolver =>
    async ({ request, params }) => {
      const url = new URL(request.url)
      try {
        const me = options.isPublic ? (undefined as unknown as User) : backend.caller(request.headers.get('Authorization'))
        const result = await handler({
          me,
          url,
          params: params as Record<string, string>,
          body: () => request.json(),
        })
        if (request.method !== 'GET') saveState(backend.state)
        if (result instanceof Response) return result
        if (result === undefined) return new HttpResponse(null, { status: 204 })
        return HttpResponse.json(result as object, { status: options.status ?? 200 })
      } catch (error) {
        if (!(error instanceof DemoError)) throw error
        return HttpResponse.json(
          {
            timestamp: new Date().toISOString(),
            status: error.status,
            error: error.status === 401 ? 'Unauthorized' : 'Error',
            message: error.message,
            path: url.pathname,
          },
          { status: error.status },
        )
      }
    }

  const id = (ctx: Context, name: string) => Number(ctx.params[name])
  const query = (ctx: Context, name: string) => ctx.url.searchParams.get(name) ?? undefined
  const pageOf = (ctx: Context) => ({ page: Number(query(ctx, 'page') ?? 0), size: Number(query(ctx, 'size') ?? 20) })

  return [
    http.post(
      '*/api/auth/login',
      route(
        async (ctx) => {
          const { email, password } = (await ctx.body()) as { email: string; password: string }
          return backend.login(email, password)
        },
        { isPublic: true },
      ),
    ),
    http.post(
      '*/api/auth/register',
      route(async (ctx) => backend.register((await ctx.body()) as RegisterRequest), { isPublic: true, status: 201 }),
    ),

    http.get('*/api/users/me', route(({ me }) => me)),
    http.get('*/api/users/me/friends', route(({ me }) => backend.friends(me))),
    http.post(
      '*/api/users/me/friends/:friendId/settle-up',
      route((ctx) => backend.settleUp(ctx.me, id(ctx, 'friendId')), { status: 201 }),
    ),
    http.get('*/api/users/me/activity', route((ctx) => {
      const { page, size } = pageOf(ctx)
      return backend.activity(ctx.me, page, size)
    })),

    http.get('*/api/groups', route(({ me }) => backend.groupsOf(me))),
    http.post('*/api/groups', route(async (ctx) => backend.createGroup(ctx.me, (await ctx.body()) as CreateGroupRequest), { status: 201 })),
    http.get('*/api/groups/:groupId', route((ctx) => backend.group(ctx.me, id(ctx, 'groupId')))),
    http.post(
      '*/api/groups/:groupId/members',
      route(async (ctx) => backend.addMember(ctx.me, id(ctx, 'groupId'), ((await ctx.body()) as { email: string }).email)),
    ),
    http.delete(
      '*/api/groups/:groupId/members/:userId',
      route((ctx) => backend.removeMember(ctx.me, id(ctx, 'groupId'), id(ctx, 'userId'))),
    ),
    http.get('*/api/groups/:groupId/expenses', route((ctx) => {
      const paidBy = query(ctx, 'paidBy')
      return backend.expenses(ctx.me, id(ctx, 'groupId'), {
        ...pageOf(ctx),
        q: query(ctx, 'q'),
        category: query(ctx, 'category'),
        paidBy: paidBy === undefined ? undefined : Number(paidBy),
        from: query(ctx, 'from'),
        to: query(ctx, 'to'),
      })
    })),
    http.get('*/api/groups/:groupId/balances', route((ctx) => backend.balances(ctx.me, id(ctx, 'groupId')))),
    http.get('*/api/groups/:groupId/debts/simplified', route((ctx) => backend.simplifiedDebts(ctx.me, id(ctx, 'groupId')))),
    http.get('*/api/groups/:groupId/activity', route((ctx) => {
      const { page, size } = pageOf(ctx)
      return backend.activity(ctx.me, page, size, id(ctx, 'groupId'))
    })),
    http.get(
      '*/api/groups/:groupId/report',
      route((ctx) => backend.report(ctx.me, id(ctx, 'groupId'), query(ctx, 'from'), query(ctx, 'to'))),
    ),
    http.get('*/api/groups/:groupId/expenses.csv', route((ctx) => {
      const file = backend.csv(ctx.me, id(ctx, 'groupId'), query(ctx, 'from'), query(ctx, 'to'))
      return new HttpResponse(file.body, {
        headers: { 'Content-Type': 'text/csv', 'Content-Disposition': `attachment; filename="${file.name}"` },
      })
    })),

    http.post('*/api/expenses', route(async (ctx) => backend.addExpense(ctx.me, (await ctx.body()) as CreateExpenseRequest), { status: 201 })),
    http.get('*/api/expenses/:id', route((ctx) => backend.expense(ctx.me, id(ctx, 'id')))),
    http.put(
      '*/api/expenses/:id',
      route(async (ctx) => backend.updateExpense(ctx.me, id(ctx, 'id'), (await ctx.body()) as UpdateExpenseRequest)),
    ),
    http.delete('*/api/expenses/:id', route((ctx) => backend.deleteExpense(ctx.me, id(ctx, 'id')))),
    http.post('*/api/expenses/:id/restore', route((ctx) => backend.restoreExpense(ctx.me, id(ctx, 'id')))),

    http.post(
      '*/api/settlements',
      route(async (ctx) => backend.addSettlement(ctx.me, (await ctx.body()) as CreateSettlementRequest), { status: 201 }),
    ),

    http.get('*/api/recurring-expenses', route(({ me }) => {
      // The backend's job runs hourly; here, catch up whenever the list is opened
      backend.runRecurring(me)
      saveState(backend.state)
      return backend.recurringOf(me)
    })),
    http.post(
      '*/api/recurring-expenses',
      route(async (ctx) => backend.addRecurring(ctx.me, (await ctx.body()) as CreateRecurringExpenseRequest), { status: 201 }),
    ),
    http.delete('*/api/recurring-expenses/:id', route((ctx) => backend.stopRecurring(ctx.me, id(ctx, 'id')))),
  ]
}
