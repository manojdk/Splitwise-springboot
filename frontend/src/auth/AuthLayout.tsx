import type { ReactNode } from 'react'
import { ThemeSwitch } from '../theme/ThemeSwitch'

/** Split screen for sign-in and sign-up: a brand panel and the form. */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen md:grid-cols-[1.1fr_1fr]">
      <section className="hero-panel relative flex min-h-64 flex-col justify-between overflow-hidden p-8 md:p-14">
        <div className="font-head text-xl font-extrabold tracking-wider">SPLITWISE</div>
        <div className="my-8">
          <h1 className="text-4xl leading-tight font-extrabold md:text-5xl">
            Split bills.
            <br />
            Not friendships.
          </h1>
          <p className="mt-4 max-w-md text-lg opacity-90">
            Groups, friends and trips in any currency. Every rupee accounted for, to the last paisa.
          </p>
        </div>
        <ul className="flex flex-wrap gap-2 text-xs font-semibold">
          {['5 split types', 'Multi-currency', 'Recurring bills'].map((tag) => (
            <li key={tag} className="rounded-full bg-white/20 px-3 py-1">
              {tag}
            </li>
          ))}
        </ul>
      </section>

      <main className="grid place-items-center p-6 md:p-8">
        <div className="card grid w-full max-w-md gap-4 p-7">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="m-0 text-xl">{title}</h2>
            <ThemeSwitch />
          </div>
          {children}
        </div>
      </main>
    </div>
  )
}
