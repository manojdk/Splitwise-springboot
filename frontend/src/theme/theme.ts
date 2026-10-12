export const THEMES = ['aurora', 'neon', 'solar'] as const
export type Theme = (typeof THEMES)[number]

export const DEFAULT_THEME: Theme = 'aurora'
export const THEME_LABELS: Record<Theme, string> = { aurora: 'Aurora', neon: 'Neon', solar: 'Solar' }

/** Also read by the inline script in index.html, which applies the theme before the first paint. */
const STORAGE_KEY = 'splitwise-theme'

export function isTheme(value: unknown): value is Theme {
  return typeof value === 'string' && (THEMES as readonly string[]).includes(value)
}

export function loadTheme(): Theme {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return isTheme(saved) ? saved : DEFAULT_THEME
  } catch {
    return DEFAULT_THEME
  }
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Storage blocked (private mode, policy): the theme still applies until the page reloads.
  }
}
