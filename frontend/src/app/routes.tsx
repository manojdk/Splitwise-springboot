import type { RouteObject } from 'react-router'
import { RequireAuth } from '../auth/RequireAuth'
import { AppShell } from '../layout/AppShell'

// Each page is its own chunk, fetched the first time it is opened: the sign-in page does not
// download the expense form, and a signed-in user does not download the sign-in page.
export const routes: RouteObject[] = [
  {
    // Shown on a fresh page load while the first page's chunk arrives (usually a blink)
    hydrateFallbackElement: <div role="status" aria-label="Loading" className="page-loading" />,
    children: [
      { path: '/login', lazy: async () => ({ Component: (await import('../auth/LoginPage')).LoginPage }) },
      { path: '/register', lazy: async () => ({ Component: (await import('../auth/RegisterPage')).RegisterPage }) },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, lazy: async () => ({ Component: (await import('../pages/DashboardPage')).DashboardPage }) },
              { path: 'groups', lazy: async () => ({ Component: (await import('../pages/GroupsPage')).GroupsPage }) },
              { path: 'groups/:groupId', lazy: async () => ({ Component: (await import('../pages/GroupPage')).GroupPage }) },
              { path: 'expenses', lazy: async () => ({ Component: (await import('../pages/ExpensesPage')).ExpensesPage }) },
              { path: 'friends/:friendId', lazy: async () => ({ Component: (await import('../pages/FriendPage')).FriendPage }) },
              { path: 'activity', lazy: async () => ({ Component: (await import('../pages/ActivityPage')).ActivityPage }) },
              { path: 'recurring', lazy: async () => ({ Component: (await import('../pages/RecurringPage')).RecurringPage }) },
            ],
          },
        ],
      },
      { path: '*', lazy: async () => ({ Component: (await import('../pages/NotFoundPage')).NotFoundPage }) },
    ],
  },
]
