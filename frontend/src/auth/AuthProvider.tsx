import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api, configureApi } from '../api/client'
import type { LoginRequest, RegisterRequest, TokenResponse, User } from '../api/types'
import { clearSession, readSession, writeSession, type Session } from './session'

/** error: signed in, but the profile could not be loaded (server down, network); retry() tries again. */
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'error'

type AuthContextValue = {
  status: AuthStatus
  user: User | null
  /** Why the user was signed out (for example an expired session), shown on the sign-in page. */
  notice: string | null
  login: (credentials: LoginRequest) => Promise<void>
  register: (details: RegisterRequest) => Promise<void>
  logout: (notice?: string) => void
  retry: () => void
}

const SESSION_EXPIRED = 'Your session has expired. Please sign in again.'
/** setTimeout fires immediately for delays above 2^31-1 ms (about 24.8 days). */
const MAX_TIMEOUT = 2_147_483_647

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => readSession())
  const [notice, setNotice] = useState<string | null>(null)
  const tokenRef = useRef<string | null>(session?.token ?? null)

  const logout = useCallback(
    (reason?: string) => {
      clearSession()
      tokenRef.current = null
      setSession(null)
      setNotice(reason ?? null)
      queryClient.clear()
    },
    [queryClient],
  )

  useEffect(() => {
    configureApi({ getToken: () => tokenRef.current, onUnauthorized: () => logout(SESSION_EXPIRED) })
  }, [logout])

  // Sign out when the token runs out, instead of waiting for the next request to fail
  useEffect(() => {
    if (!session) return
    const timer = setTimeout(() => logout(SESSION_EXPIRED), Math.min(session.expiresAt - Date.now(), MAX_TIMEOUT))
    return () => clearTimeout(timer)
  }, [session, logout])

  const me = useQuery({
    queryKey: ['me', session?.token],
    queryFn: ({ signal }) => api<User>('/users/me', { signal }),
    enabled: session !== null,
    staleTime: Infinity,
    retry: false,
  })

  const login = useCallback(
    async (credentials: LoginRequest) => {
      const token = await api<TokenResponse>('/auth/login', { method: 'POST', body: credentials })
      const next = { token: token.accessToken, expiresAt: Date.now() + token.expiresInSeconds * 1000 }
      queryClient.clear()
      writeSession(next)
      tokenRef.current = next.token
      setNotice(null)
      setSession(next)
    },
    [queryClient],
  )

  const register = useCallback(
    async (details: RegisterRequest) => {
      await api<User>('/auth/register', { method: 'POST', body: details })
      await login({ email: details.email, password: details.password })
    },
    [login],
  )

  const status: AuthStatus = !session ? 'anonymous' : me.data ? 'authenticated' : me.isError ? 'error' : 'loading'

  const { refetch } = me
  const retry = useCallback(() => void refetch(), [refetch])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user: me.data ?? null, notice, login, register, logout, retry }),
    [status, me.data, notice, login, register, logout, retry],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>')
  return value
}
