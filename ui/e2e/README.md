# E2E tests (Playwright)

Specs mirror `client/src/app/pages/` down to the surface folder
(`tests/searchpe/versions/version-list/version-list.spec.ts`); `auth.setup.ts` logs in once
through Keycloak, `tests/public/**` runs with no session (project `public`). Everything runs
against a real stack: nothing is mocked.

## 1. The stack

From the repository root (one level above `ui/`), `hack/ui-e2e-stack.sh` runs everything the
suite needs from built artifacts, the same way CI does (`.github/workflows/ui-e2e.yml`):

```bash
hack/ui-e2e-stack.sh build          # xhandler app and searchpe jars, engine and SUNAT fake binaries
hack/ui-e2e-stack.sh up             # PostgreSQL + Keycloak (compose), then the processes below
hack/ui-e2e-stack.sh down           # stops the processes; `down --compose` also removes the
                                    # compose stack (and with it the database)
```

| Service          | Port | Notes                                                                                        |
| ---------------- | ---- | -------------------------------------------------------------------------------------------- |
| PostgreSQL       | 5432 | `hack/compose.yaml`: one database per service                                                |
| Keycloak         | 8180 | the shared dev realm, with the UI's public client `xhandler-ui`                              |
| SUNAT fake       | 8099 | `sunat-fake`: every company starts on it (environment `fake`)                                |
| engine           | 8090 | `xhandler-engine`, signs and sends                                                           |
| xhandler-service | 8080 | production mode with dev conveniences (software vault, file storage, mailer mock)            |
| searchpe         | 8081 | OIDC and authorization on, advanced search on                                                |

Logs go to `/tmp/xhandler-ui-e2e/*.log` (`STACK_DIR` to change). `COMPOSE=docker compose` if you
use Docker. The database outlives `down`: the suite passes against it run after run.

## 2. The backends in dev mode

To work on a backend, run it with `quarkus:dev` instead of the script (no compose stack: Dev
Services start PostgreSQL and the shared Keycloak on 8180). xhandler-service needs the engine and
the SUNAT fake to issue; see [CONTRIBUTING.md](../../CONTRIBUTING.md#start-the-app):

```bash
(cd xhandler/engine && cargo run)
(cd xhandler/engine && cargo run --bin sunat-fake)
(cd xhandler && ../mvnw quarkus:dev)      # :8080, the enterprise plan by default in dev mode
(cd searchpe && ../mvnw quarkus:dev)      # :8081, OIDC and authorization on in dev mode
```

Without searchpe, run with `E2E_SEARCHPE=false`: its specs skip and the UI hides its section.

## 3. Run

```bash
cd ui
cp .env.example .env            # XHANDLER_API_URL, SEARCHPE_API_URL, OIDC_* for the dev server
npx playwright install chromium # once, unless E2E_BROWSER_CHANNEL=chrome uses Google Chrome
npm run test:e2e                # starts `npm run start:dev` on :3000 unless one is running
npm run test:ui -w e2e          # Playwright UI mode
```

| Variable                    | Default                 | Meaning                                                       |
| --------------------------- | ----------------------- | ------------------------------------------------------------- |
| `E2E_BASE_URL`              | `http://localhost:3000` | A UI already running (then no dev server is started)          |
| `E2E_USERNAME`              | `olga`                  | Dev-realm user; olga holds every searchpe permission          |
| `E2E_PASSWORD`              | `olga`                  |                                                               |
| `E2E_SEARCHPE`              | `true`                  | `false` when the stack runs without searchpe                  |
| `E2E_SEARCHPE_IMPORT`       | `false`                 | `true` to create a padrón version (downloads SUNAT's padrón)  |
| `E2E_BROWSER_CHANNEL`       | (Playwright's Chromium) | `chrome` to use the installed Google Chrome                   |
| `E2E_PUBLIC_DOCUMENT_TOKEN` | (the setup's document)  | A buyer link's token (`access_url`) for the buyer-portal spec |

`auth.setup.ts` logs in once and prepares, through the xhandler API, what the organization specs
read, so they run instead of skipping on a fresh stack (`ensureFixtures` in
`support/helpers/tenants.ts`): an organization (it signs the e2e user up), a company on the SUNAT
fake, a factura the company issues to itself and receives back (the documents and receptions
lists, and the buyer link for `tests/public/`), a webhook and a partner (the dev realm's olga is a
platform admin). It writes their ids to `.auth/fixtures.json`; the `public` project depends on
the setup for the buyer link, while its browser still has no session.

On this stack the whole suite runs; the only skip is the padrón import (`E2E_SEARCHPE_IMPORT`).
