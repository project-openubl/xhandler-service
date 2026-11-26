# Contributing

How to run the platform on your machine. Commands run from the repository root unless a step
says otherwise. For conventions, tests and CI, read [AGENTS.md](AGENTS.md).

## Requirements

- Java 25 (Maven comes with `./mvnw`)
- Rust: `rustup` installs the pinned toolchain. The engine also needs `libxml2-dev`,
  `libclang-dev` and `pkg-config`
- Node 22
- Podman (or Docker) with compose. With Docker, use `docker compose` and set
  `COMPOSE="docker compose"` for the scripts in `hack/`

## What runs where

| Process          | Port | What it is                                                     |
| ---------------- | ---- | -------------------------------------------------------------- |
| UI               | 3000 | React dev server; proxies `/api/*` to the backends             |
| xhandler-service | 8080 | Quarkus app: the document API                                  |
| searchpe         | 8081 | Quarkus app: RUC lookup (optional)                             |
| xhandler engine  | 8090 | Rust sidecar: builds, signs, sends documents                   |
| SUNAT fake       | 8099 | Stands in for SUNAT; every new company starts here             |
| Keycloak         | 8180 | Shared dev realm `xhandler` (admin console: `admin` / `admin`) |
| Grafana (LGTM)   | 3001 | Traces, metrics and logs (optional)                            |

**Dev users** (password = username): `alice`, `bob`, `carol` (platform support), `dave` (email
not verified) and `olga` (platform admin, every searchpe permission). Use `olga` to see everything.

## Option 1: develop with `quarkus:dev` (recommended)

Quarkus [Dev Services](https://quarkus.io/guides/dev-services) start the containers each app
needs, so there is nothing to configure: code reloads on every request. One terminal per process.

```bash
(cd xhandler/engine && cargo run)                    # 1. engine on 127.0.0.1:8090
(cd xhandler/engine && cargo run --bin sunat-fake)   # 2. SUNAT fake on 127.0.0.1:8099
./mvnw -f xhandler quarkus:dev -pl app               # 3. xhandler-service on :8080
./mvnw quarkus:dev -pl searchpe                      # 4. searchpe on :8081 (optional)
(cd ui && cp .env.example .env && npm ci && npm run start:dev)   # 5. http://localhost:3000
```

Log in to the UI as `olga` / `olga`. What Dev Services start:

| Container  | Started by                      | Notes                                                              |
| ---------- | ------------------------------- | ------------------------------------------------------------------ |
| PostgreSQL | each app, its own               | Random port; kept across live reloads                              |
| Keycloak   | the first app, the other joins  | Port 8180, the realm in [hack/keycloak/](hack/keycloak/)           |
| Mailpit    | xhandler-service                | Catches every email: http://localhost:8080/q/mailpit/              |
| LGTM       | xhandler-service, when asked    | Add `-Dquarkus.observability.enabled=true`; Grafana on :3001       |

- Keycloak belongs to the app that started it: stopping that app stops Keycloak, and the other
  one loses its logins until you restart it. Start xhandler-service first and stop it last.
- Dev UI: http://localhost:8080/q/dev-ui (configuration, Dev Services, Mailpit, Keycloak) ·
  Swagger UI: http://localhost:8080/q/swagger-ui.
- Traces: with LGTM on, start the engine with `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318`
  to see app → engine → SUNAT in one trace.
- Without searchpe, remove `SEARCHPE_API_URL` from `ui/.env`: the UI hides the RUC section.

## Option 2: the whole stack in one command

Runs every backend from its built artifacts in production mode, against
[hack/compose.yaml](hack/compose.yaml) (PostgreSQL, Keycloak on 8180). It is the stack of the UI's
Playwright suite and suits trying the platform or working on the UI only:

```bash
hack/ui-e2e-stack.sh build      # Maven packages of xhandler and searchpe, cargo build of the engine
hack/ui-e2e-stack.sh up         # compose + fake, engine, xhandler, searchpe

cd ui
cp .env.example .env
npm ci
npm run start:dev               # http://localhost:3000
```

Logs go to `/tmp/xhandler-ui-e2e/*.log`. Stop with `hack/ui-e2e-stack.sh down` (add `--compose`
to stop PostgreSQL and Keycloak too). After changing a backend, run `build` and `up` again.

Don't mix the two options: both use port 8180 for Keycloak.

## First steps in the app

1. Log in as `olga` and create an organization, then a company. It starts on the SUNAT fake.
2. Issue a factura: the fake accepts it, and the document moves to accepted.
3. To try SUNAT beta, switch the company's environment (never production in development).

[`hack/xhandler-e2e.sh`](hack/xhandler-e2e.sh) runs the same flow through the API against SUNAT
beta.

## Before opening a pull request

- Run the checks of what you changed ([AGENTS.md § Commands](AGENTS.md#commands)).
- Use Conventional Commits (`feat(ui): …`, `fix: …`).
- A behavior change updates [docs/DESIGN.md](docs/DESIGN.md) or an ADR in the same commit.

## Troubleshooting

- **Port 8180 in use**: the compose Keycloak of Option 2 (`podman compose -f hack/compose.yaml
  down`) or another project.
- **Start from a clean database**: Dev Services start a fresh PostgreSQL with each app start,
  unless Testcontainers reuse is on (`testcontainers.reuse.enable=true` in
  `~/.testcontainers.properties`); then remove the container (`podman rm -f <id>`). For Option 2,
  `podman compose -f hack/compose.yaml down -v` drops the volumes.
- **Dev Services with Podman**: Testcontainers needs the Podman socket
  (`systemctl --user enable --now podman.socket` and
  `DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock`).
- **The UI says a backend is unreachable**: check `ui/.env` and that the backend answers on
  `/q/health/ready`.
