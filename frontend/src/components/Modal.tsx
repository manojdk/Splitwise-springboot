import { useEffect, useId, useRef, type ReactNode } from 'react'

type ModalProps = {
  title: string
  onClose: () => void
  children: ReactNode
  /** Blocks closing while a request is in flight. */
  busy?: boolean
  size?: 'md' | 'lg'
}

/**
 * Accessible dialog: labelled by its title, focus moves inside on open and back to the opener on close,
 * Tab stays inside, Escape and a click on the backdrop close it.
 */
export function Modal({ title, onClose, children, busy = false, size = 'md' }: ModalProps) {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)
  const onCloseRef = useRef(onClose)
  const busyRef = useRef(busy)

  useEffect(() => {
    onCloseRef.current = onClose
    busyRef.current = busy
  })

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    // The first form field; a dialog without fields focuses itself, so the title is read and no action is
    // pre-selected (a stray Enter must not confirm a payment)
    const field = panel.current?.querySelector<HTMLElement>('[autofocus], input, select, textarea')
    ;(field ?? panel.current)?.focus()

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busyRef.current) onCloseRef.current()
      if (event.key !== 'Tab' || !panel.current) return
      const focusable = [...panel.current.querySelectorAll<HTMLElement>('a[href], button, input, select, textarea')].filter(
        (el) => !el.hasAttribute('disabled'),
      )
      const firstEl = focusable[0]
      const lastEl = focusable[focusable.length - 1]
      if (!firstEl || !lastEl) return
      if (event.shiftKey && document.activeElement === firstEl) {
        event.preventDefault()
        lastEl.focus()
      } else if (!event.shiftKey && document.activeElement === lastEl) {
        event.preventDefault()
        firstEl.focus()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      opener?.focus()
    }
  }, [])

  return (
    <div
      role="presentation"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onClose()
      }}
    >
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`card max-h-[92vh] w-full overflow-auto bg-[var(--surface-solid)] p-6 outline-none ${size === 'lg' ? 'max-w-2xl' : 'max-w-lg'}`}
      >
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 id={titleId} className="m-0 text-lg">
            {title}
          </h2>
          <button
            type="button"
            className="btn btn-ghost px-3 py-1.5"
            aria-label="Close"
            onClick={onClose}
            disabled={busy}
          >
            {'✕'}
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
