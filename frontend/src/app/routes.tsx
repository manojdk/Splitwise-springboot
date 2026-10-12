import type { RouteObject } from 'react-router'
import { LoginPage } from '../auth/LoginPage'
import { RegisterPage } from '../auth/RegisterPage'
import { RequireAuth } from '../auth/RequireAuth'
import { AppShell } from '../layout/AppShell'
import { DashboardPage } from '../pages/DashboardPage'
import { GroupPage } from '../pages/GroupPage'
import { GroupsPage } from '../pages/GroupsPage'
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
          { path: 'groups', element: <GroupsPage /> },
          { path: 'groups/:groupId', element: <GroupPage /> },
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
