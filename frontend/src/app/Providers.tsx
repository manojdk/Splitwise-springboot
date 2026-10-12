import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { AuthProvider } from '../auth/AuthProvider'
import { ToastProvider } from '../components/Toast'
import { ThemeProvider } from '../theme/ThemeProvider'
import { createQueryClient } from './queryClient'

export function Providers({ children, queryClient }: { children: ReactNode; queryClient?: QueryClient }) {
  const [client] = useState(() => queryClient ?? createQueryClient())

  return (
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <AuthProvider>
          <ToastProvider>{children}</ToastProvider>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
