import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

type ToastContextValue = { show: (message: string) => void }

const ToastContext = createContext<ToastContextValue | null>(null)

/** Short confirmations ("Group created"), announced politely to screen readers. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const show = useCallback((text: string) => {
    clearTimeout(timer.current)
    setMessage(text)
    timer.current = setTimeout(() => setMessage(null), 4000)
  }, [])

  return (
    <ToastContext value={{ show }}>
      {children}
      <div role="status" aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-24 z-[60] flex justify-center px-4 md:bottom-6">
        {message && <div className="card px-5 py-3 font-semibold">{message}</div>}
      </div>
    </ToastContext>
  )
}

export function useToast(): ToastContextValue {
  const value = useContext(ToastContext)
  if (!value) throw new Error('useToast must be used inside <ToastProvider>')
  return value
}
