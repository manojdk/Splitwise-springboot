import type { RouteObject } from 'react-router'
import { LoginPage } from '../auth/LoginPage'
import { RegisterPage } from '../auth/RegisterPage'
import { RequireAuth } from '../auth/RequireAuth'
import { AppShell } from '../layout/AppShell'
import { DashboardPage } from '../pages/DashboardPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { PlaceholderPage } from '../pages/PlaceholderPage'

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <DashboardPage /> },
          {
            path: 'groups',
            element: <PlaceholderPage title="Groups" description="Your groups and their balances will be listed here." />,
          },
          {
            path: 'activity',
            element: <PlaceholderPage title="Activity" description="Every change to your expenses will show up here." />,
          },
          {
            path: 'recurring',
            element: <PlaceholderPage title="Recurring" description="Bills that repeat every week or month will live here." />,
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]
