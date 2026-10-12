# Splitwise frontend

React 19 + TypeScript (strict) + Vite, talking to the Spring Boot API in the parent folder.

| Concern | Choice |
|---|---|
| Server data | TanStack Query |
| Routing | React Router |
| Forms | React Hook Form + Zod (same limits as the API's request validation) |
| Styling | Tailwind CSS on CSS variables; three themes: Aurora (default), Neon, Solar |
| API types | Generated from the backend's OpenAPI spec |
| Tests | Vitest + Testing Library + MSW (a fake API with the real paths and error bodies) |

## Run it

Start the API on port 8080 (see the main README), then:

```bash
npm ci
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to the API, so the browser stays on one origin and the
backend needs no CORS setup.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with hot reload |
| `npm test` | Unit and component tests, once |
| `npm run test:watch` | Tests in watch mode |
| `npm run lint` | oxlint (React hooks, accessibility); warnings fail the build |
| `npm run typecheck` | `tsc` in strict mode |
| `npm run build` | Type check, then the production bundle in `dist/` |
| `npm run api:types` | Regenerates `src/api/schema.d.ts` from `openapi.json` |

## API types

`openapi.json` is the backend's spec, committed. The backend test `OpenApiSpecTest` fails when the API changes
and this file does not, and CI fails when `schema.d.ts` is not regenerated from it. So an API change cannot
reach `main` without the frontend's types following it. After changing the API:

```bash
# in the repository root
./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true
# in frontend/
npm run api:types
```

## Sign-in and the session

- The API issues a JWT access token (1 hour, no refresh token). It is kept in `sessionStorage`: it survives a
  reload but not closing the tab.
- The app signs the user out when the token expires, or as soon as the API rejects it (401), and says why on
  the sign-in page.
- A guest who opens a page is sent to sign in, then brought back to that page. Only paths on this site are
  accepted as the return address.
- If the profile cannot be loaded (server down), the user gets a retry screen instead of being signed out.
- Trade-off: script injected into the page could read `sessionStorage`. The app never renders raw HTML, which
  is the guard against that.

## Layout

```
src/
  api/        fetch client (token, errors, 401), generated schema, type aliases
  app/        routes, providers, query client
  auth/       session storage, AuthProvider, sign-in / sign-up pages, route guard
  layout/     app shell: sidebar on wide screens, bottom navigation on phones
  pages/      screens
  theme/      themes, provider and switch
  test/       MSW fake API, test setup, renderApp helper
```

## Accessibility

Labelled inputs with linked, announced error messages; visible keyboard focus; a skip-to-content link; the
theme switch is a radio group; all animation stops under `prefers-reduced-motion`.
