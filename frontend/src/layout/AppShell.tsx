import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthProvider'
import { initials } from '../lib/initials'
import { ThemeSwitch } from '../theme/ThemeSwitch'

type NavEntry = { to: string; label: string; icon: string; end?: boolean }

const NAV: NavEntry[] = [
  { to: '/', label: 'Dashboard', icon: '◇', end: true },
  { to: '/groups', label: 'Groups', icon: '▦' },
  { to: '/activity', label: 'Activity', icon: '↻' },
  { to: '/recurring', label: 'Recurring', icon: '∞' },
]

/** Signed-in layout: sidebar on wide screens, bottom navigation on phones. */
export function AppShell() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen md:grid md:grid-cols-[240px_1fr]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-lg focus:bg-surface focus:p-2"
      >
        Skip to content
      </a>

      <aside className="sticky top-0 hidden h-screen flex-col gap-1.5 border-r border-line bg-surface p-4 backdrop-blur-md md:flex">
        <div className="brand mx-2 mb-6 text-xl">SPLITWISE</div>
        <nav aria-label="Main" className="flex flex-col gap-1.5">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className="nav-item">
              <span aria-hidden="true" className="w-5 text-center">
                {item.icon}
              </span>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="min-w-0">
        <header className="flex flex-wrap items-center justify-end gap-3 px-4 pt-4 md:px-7 md:pt-5">
          <div className="brand mr-auto text-lg md:hidden">SPLITWISE</div>
          <ThemeSwitch />
          {user && (
            <div className="flex items-center gap-3">
              <div className="avatar" aria-hidden="true">
                {initials(user.name)}
              </div>
              <span className="hidden font-semibold lg:inline">{user.name}</span>
              <button type="button" className="btn btn-ghost px-3 py-2 text-sm" onClick={() => logout()}>
                Sign out
              </button>
            </div>
          )}
        </header>

        <main id="main" className="mx-auto w-full max-w-6xl px-4 pt-6 pb-28 md:px-7 md:pb-10">
          <Outlet />
        </main>
      </div>

      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-40 flex justify-around border-t border-line bg-surface px-2 pt-2 pb-3 backdrop-blur-md md:hidden"
      >
        {NAV.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end} className="nav-item flex-col gap-0.5 px-3 py-1.5 text-[11px]">
            <span aria-hidden="true" className="text-base">
              {item.icon}
            </span>
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
