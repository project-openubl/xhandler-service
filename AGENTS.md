# AGENTS.md

Guide for AI coding agents (and humans) in this monorepo: the xhandler services for SUNAT (Peru)
electronic documents, built, tested and deployed together. This is the only agent guide.

**Behavior is defined in [docs/DESIGN.md](docs/DESIGN.md)** (UI: the ADRs in
[docs/adr/](docs/adr/)). Read the relevant section before changing behavior; a behavior
change updates DESIGN.md or an ADR in the same commit. How to run the stack locally:
[CONTRIBUTING.md](CONTRIBUTING.md).

## Layout

```
xhandler/   multi-tenant document service
  src/        Quarkus app (Java 25, pom.xml beside it): everything but the SUNAT documents
  engine/     Rust sidecar on xhandler-rust: build, sign, send; also the SUNAT fake
  sdk/        clients generated from the OpenAPI document
  deploy/     Kustomize base: one pod = app + engine
searchpe/   RUC lookup over SUNAT's padrón reducido (Quarkus)
ui/         the one UI of both services (React 19, PatternFly 6, pulp-ui-2 blueprint)
operator/   Kubernetes operator (Go, OLM): XHandler and Searchpe kinds
docs/       DESIGN.md, adr/ (repo-wide decisions), research, runbooks, quickstarts, ui/
hack/       dev and CI tooling only
  compose.yaml       PostgreSQL, Keycloak :8180, Grafana :3001, SUNAT fake
  postgres/          database roles of each service
  keycloak/          the shared dev realm (never for a real environment)
  ui-e2e-stack.sh    every service, for the UI's Playwright suite
  xhandler-e2e.sh    full flow against SUNAT beta
  load/              k6 load tests
  minikube/          manifests of the CI minikube e2e
.rulesync/  agent rules and skills (source); generates .claude/, .cursor/, .mcp.json
```

## Commands

```bash
# xhandler
(cd xhandler && ../mvnw quarkus:dev)                 # :8080; Dev Services: PostgreSQL, Keycloak :8180, Mailpit
(cd xhandler && ../mvnw verify)                      # tests (need Docker or Podman)
(cd xhandler/engine && cargo run)                    # engine :8090
(cd xhandler/engine && cargo run --bin sunat-fake)   # SUNAT fake :8099
(cd xhandler/engine && cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test)
hack/xhandler-e2e.sh                                 # full flow against SUNAT beta

# searchpe
(cd searchpe && ../mvnw quarkus:dev)                 # :8081, joins xhandler's Keycloak
(cd searchpe && ../mvnw verify)                      # tests (need Docker or Podman)

# operator
(cd operator && make lint verify-generated test bundle bundle-validate)

# ui (build before lint)
(cd ui && npm ci && npm run build && npm run lint && npm run format && npm test)
hack/ui-e2e-stack.sh build && hack/ui-e2e-stack.sh up && (cd ui && npm run test:e2e)
```

Toolchains: Java 25, Rust pinned in `xhandler/engine/rust-toolchain.toml` (needs `libxml2-dev`,
`libclang-dev`, `pkg-config`), Node 22, Go. searchpe runs on 8081 (dev mode and `ui-e2e-stack.sh`);
in dev mode both apps share one Keycloak from Dev Services (CONTRIBUTING.md).

## Conventions (all services)

- **Language**: user-facing text (UI, API errors, emails, PDFs) is Spanish; code, logs and
  comments English. SUNAT and domain concepts keep their Spanish names (`Detalle`,
  `tipoComprobante`); infrastructure is English (`Job`, `…Manager`, `…Provider`).
- **Acronyms are words**: `Ruc`, `Sunat`, `Cdr`, `Igv`, `Xml`.
- **SUNAT**: tests and scripts use beta or the fake, never production.
- **Services share nothing but their APIs**: each keeps its database, migrations and conventions.
- **Migrations**: never edit an applied one, add a new one. "Applied" includes your local test
  database (Dev Services reuse it).
- **Commits**: Conventional Commits, scoped when useful (`feat(ui):`, `fix(operator):`, `ci:`).
- Java in `searchpe/` carries the `tools/copyright.txt` header (`mvn license:format`).

## xhandler

Code in `xhandler/src` lives under `io.github.projectopenubl.xhandler`, layered as DESIGN.md §4:
`resources/` (JAX-RS) → `managers/` (business logic) → `models/` (`*Model`, `*Provider`) →
`models/jpa/` (`Jpa*Provider`, entities, repositories). API types are
`representations/idm/*Representation` records.

- All SQL goes into a `Jpa*Provider` (`@Transactional(MANDATORY)`), never into resources,
  managers or jobs. Providers return models, `Optional`s or records and never throw `ApiException`.
- Representations keep a stable `@Schema(name)`: the SDKs and the UI are generated from the OpenAPI.
- API errors are `{ kind, message (Spanish), detail }`; internals are logged, not returned.
  JSON is snake_case.
- Java: Google Java Style, records for data between components, stateless CDI beans; entities
  and sessions never cross threads. Concurrency rules (§10.8) apply to jobs, documents, numbering.
- **Tenancy (§6)**: every `@Transactional` method on tenant data calls
  `TenantAccess.enter(tenantId, minimumRole)` first. PostgreSQL row-level security isolates
  tenants; the app runs as `xhandler_app`, never as owner or superuser. Cross-tenant lookups
  are `SECURITY DEFINER` functions in migrations. Foreign and unknown tenants are both 404.
- **Secrets**: private keys only through `KeyVault`, secrets only through
  `CredentialManager`/`SecretBox`; never log or return them; reads are audited.
- **Engine**: stateless. Errors are `{ kind, message }` (`invalid_document | invalid_certificate
  | wrong_password | invalid_request` → 422, `other` → 500). Send outcome `unknown` means SUNAT
  may have the document (ask before resending); `not_delivered` means it never arrived. The
  app → engine timeout is longer than engine → SUNAT.
- **Tests** use real Keycloak tokens (`Api.as("alice")`) and must pass against a database that
  outlives the run (random RUCs, no fixed ids).

## ui

Run commands from `ui/`. Four npm workspaces: `common/`, `client/` (Vite SPA, `@app` →
`client/src/app/`), `server/` (Express: proxies, env injection) and `e2e/` (Playwright, specs
mirror `client/src/app/pages/`). The browser calls `/api/xhandler/*` and `/api/searchpe/*` on its
own origin; searchpe is optional (no `SEARCHPE_API_URL`, no section).

- Coding rules and skills live in `.rulesync/` at the repository root; `npm run rules` (from
  `ui/`) generates the root `.claude/`, `.cursor/` and `.mcp.json`. Never edit the generated copies. Check a change against the `## Checklist` of
  every rule matching the changed files.
- Never edit generated code: `client/src/app/client/`, `routeTree.gen.ts`. Regenerate the API
  clients with `npm run openapi:refresh` after an xhandler or searchpe API change.
- Iterate with `npm run lint:fast` / `format:fast`; finish with
  `npm run format:fix && npm run lint && npm run format` (ESLint, tsc and Prettier are the gate).
- Text is Spanish (es-PE), hard-coded, no i18n. API errors show "No se pudo …" plus the server's
  `message` and `detail`. Public routes never ask for a login.
- More: [docs/ui/](docs/ui/README.md) (internals, scripts, tech stack, environment variables).

## CI and releases

Every workflow runs on each push and PR and must stay green on `main`. `snapshots.yml` publishes
`:nightly` images to `ghcr.io/project-openubl/xhandler-service/`. Releases run only through
release-tools' `release-xhandler-service` workflow, which triggers `release.yml`.
