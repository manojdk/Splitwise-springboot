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
| `npm run dev:demo` / `npm run build:demo` | The GitHub Pages demo (see below), served locally or built into `dist-demo/` |
| `npm run api:types` | Regenerates `src/api/schema.d.ts` from `openapi.json` |

## Screens

| Screen | What it does |
|---|---|
| Sign in / Create account | Validation as strict as the API's; server errors shown where they belong |
| Dashboard | What you are owed and what you owe, **per currency** (never added together); friends with each balance and where it comes from; settle up with a friend after confirming the payments it records; your balance in each group |
| Groups | Your groups with members and your balance; new group with a currency and people to add by email (people without an account are listed, the group is kept) |
| Group | Expenses with search, category, payer and date filters (kept in the address) and paging; balances; the fewest payments to settle, recordable by the people involved; members (add, remove, leave) |
| Activity | Everything that happened, newest first, with exactly what changed in each edit; "Show more" pages back; also per group |
| Recurring | Rent, wifi, subscriptions: next date, how often, stopped ones with the reason; set one up in a group with the same live split form; stop with a confirmation |
| Report (group tab) | Spending by category (a CSS donut, no chart library) and paid / share / net per member, for any date range; CSV export of the same range |
| Add / edit expense | All five split types with **each person's share shown live, to the paisa**; edits open in the terms they were entered (2 : 1 : 1 shares, 50 %); a concurrent edit is caught (409) and the other version offered; delete with Undo |

## The demo on GitHub Pages

The live demo is this app with its API running in the browser, so it needs no server:

- `src/demo/backend.ts` is the API in TypeScript, with the backend's rules: membership checks, the five split
  types (through the same `split.ts` the form uses), balances from each group's simplified debts, version checks
  on edits (409), soft delete and restore, recurring expenses that catch up, reports and CSV export, and an
  activity entry for every change, worded like the backend's.
- `src/demo/seed.ts` builds the sample data (four people, three groups in INR and EUR, a non-group expense in
  USD, a running and a stopped recurring expense) through those same operations, dated relative to today, so the
  balances and the activity feed are what the app would really show.
- `src/demo/handlers.ts` answers the app's `/api` calls through a Service Worker (MSW) with the real status codes
  and error bodies, and saves the data in `localStorage`. A banner says it is a demo and offers a reset.

`vite build --mode demo` puts the app under `/Splitwise-springboot/` and copies `index.html` to `404.html`, so a
shared link such as `/Splitwise-springboot/groups/12` opens the app too. None of this is in the normal build or
the jar. The `Demo on GitHub Pages` workflow publishes it on every frontend change to `main`.

## The split calculator matches the API

`src/lib/split.ts` mirrors the backend's split rules with whole-paise `BigInt` arithmetic. The backend test
`SplitGoldenCasesTest` runs 420 inputs (random ones plus boundaries such as a 0.01 split three ways or a
percentage with 5 decimals) through the real split strategies and writes the results to
`src/lib/split-golden.json`; `split.golden.test.ts` replays every case. So the preview in the form is proven to
show what the API will save. When a split rule changes, the backend test fails until the file is regenerated:

```bash
./mvnw test -Dtest=SplitGoldenCasesTest -Dsplit.update=true
```

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

## Tests

The fake API in `src/test/server.ts` has the backend's paths, response shapes and error messages, over a small
in-memory state that every test starts fresh. A request the fake API does not know fails the test that made it,
so a test cannot pass while silently missing data.

## Layout

```
src/
  api/        fetch client (token, errors, 401), generated schema, type aliases
  app/        routes, providers, query client
  auth/       session storage, AuthProvider, sign-in / sign-up pages, route guard
  components/ dialog, toast, money, loading and error states
  features/   pieces of a screen with their own logic (settle-up, new group)
  layout/     app shell: sidebar on wide screens, bottom navigation on phones
  lib/        money formatting, per-group balances, currencies
  pages/      screens
  theme/      themes, provider and switch
  test/       MSW fake API, test setup, renderApp helper
```

## Accessibility

Labelled inputs with linked, announced error messages; visible keyboard focus; a skip-to-content link; the
theme switch is a radio group; all animation stops under `prefers-reduced-motion`.
