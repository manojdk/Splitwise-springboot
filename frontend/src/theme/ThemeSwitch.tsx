import { THEMES, THEME_LABELS } from './theme'
import { useTheme } from './ThemeProvider'

export function ThemeSwitch() {
  const { theme, setTheme } = useTheme()

  return (
    <div role="radiogroup" aria-label="Theme" className="flex gap-1 rounded-full border border-line bg-surface p-1">
      {THEMES.map((t) => (
        <button
          key={t}
          type="button"
          role="radio"
          aria-checked={theme === t}
          onClick={() => setTheme(t)}
          className={
            'cursor-pointer rounded-full px-3 py-1.5 font-head text-[11px] font-bold tracking-wider uppercase transition ' +
            (theme === t ? 'bg-primary text-primary-ink shadow-[var(--glow)]' : 'text-muted hover:text-text')
          }
        >
          {THEME_LABELS[t]}
        </button>
      ))}
    </div>
  )
}
