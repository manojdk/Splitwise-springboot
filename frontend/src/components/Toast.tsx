import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

type ToastAction = { label: string; onClick: () => void }
type ToastContextValue = { show: (message: string, action?: ToastAction) => void }

const ToastContext = createContext<ToastContextValue | null>(null)

/** Short confirmations ("Group created"), announced politely to screen readers. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; action?: ToastAction } | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  /** An action (such as Undo) keeps the toast up longer, so there is time to use it. */
  const show = useCallback((message: string, action?: ToastAction) => {
    clearTimeout(timer.current)
    setToast({ message, action })
    timer.current = setTimeout(() => setToast(null), action ? 8000 : 4000)
  }, [])

  return (
    <ToastContext value={{ show }}>
      {children}
      <div role="status" aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-24 z-[60] flex justify-center px-4 md:bottom-6">
        {toast && (
          <div className="card pointer-events-auto flex items-center gap-4 px-5 py-3 font-semibold">
            {toast.message}
            {toast.action && (
              <button
                type="button"
                className="btn btn-ghost px-3 py-1.5 text-sm"
                onClick={() => {
                  clearTimeout(timer.current)
                  setToast(null)
                  toast.action?.onClick()
                }}
              >
                {toast.action.label}
              </button>
            )}
          </div>
        )}
      </div>
    </ToastContext>
  )
}

export function useToast(): ToastContextValue {
  const value = useContext(ToastContext)
  if (!value) throw new Error('useToast must be used inside <ToastProvider>')
  return value
}
