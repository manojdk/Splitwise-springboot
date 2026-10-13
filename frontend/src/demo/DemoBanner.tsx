import { clearSession } from '../auth/session'
import { DEMO_EMAIL, DEMO_PASSWORD } from './seed'
import { resetDemo } from './handlers'

/** Says this is a demo, how to sign in, and lets the visitor start over with the sample data. */
export function DemoBanner() {
  function reset() {
    resetDemo()
    clearSession()
    window.location.reload()
  }

  return (
    <div role="note" aria-label="Demo" className="demo-banner">
      <span>
        <strong>Live demo.</strong> It runs in your browser: nothing is sent to a server, and your changes stay on this
        device. Sign in as <code>{DEMO_EMAIL}</code> / <code>{DEMO_PASSWORD}</code> or register any email.
      </span>
      <button type="button" className="demo-banner-reset" onClick={reset}>
        Reset demo data
      </button>
    </div>
  )
}
