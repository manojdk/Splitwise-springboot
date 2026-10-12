import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { applyTheme, loadTheme, type Theme } from './theme'

type ThemeContextValue = { theme: Theme; setTheme: (theme: Theme) => void }

const ThemeContext = createContext<ThemeContextValue | null>(null)

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(loadTheme)

  useEffect(() => applyTheme(theme), [theme])

  return <ThemeContext value={{ theme, setTheme }}>{children}</ThemeContext>
}

export function useTheme(): ThemeContextValue {
  const value = useContext(ThemeContext)
  if (!value) throw new Error('useTheme must be used inside <ThemeProvider>')
  return value
}
