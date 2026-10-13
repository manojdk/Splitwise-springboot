import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { Providers } from './app/Providers'
import { routes } from './app/routes'
import './index.css'

async function start() {
  // The GitHub Pages demo (vite --mode demo) answers the API in the browser; other builds leave this code out
  const isDemo = import.meta.env.MODE === 'demo'
  if (isDemo) await (await import('./demo/start')).startDemo()
  const DemoBanner = isDemo ? (await import('./demo/DemoBanner')).DemoBanner : null

  // The demo lives under /<repo>/ on GitHub Pages; everywhere else this is /
  const router = createBrowserRouter(routes, { basename: import.meta.env.BASE_URL.replace(/\/$/, '') || '/' })

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Providers>
        {DemoBanner && <DemoBanner />}
        <RouterProvider router={router} />
      </Providers>
    </StrictMode>,
  )
}

void start()
