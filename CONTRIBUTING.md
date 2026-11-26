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

| Process               | Port  | What it is                                        |
| --------------------- | ----- | ------------------------------------------------- |
| UI                    | 3000  | React dev server; proxies `/api/*` to the backends |
| xhandler-service      | 8080  | Quarkus app: the document API                     |
| searchpe              | 8081  | Quarkus app: RUC lookup (optional)                |
| xhandler engine       | 8090  | Rust sidecar: builds, signs, sends documents      |
| SUNAT fake            | 8099  | Stands in for SUNAT; every new company starts here |
| PostgreSQL            | 5432  | One database per service                          |
| Keycloak              | 8180  | Shared dev realm `xhandler` (admin console: `admin` / `admin`) |
| Grafana (LGTM)        | 3001  | Traces, metrics and logs (optional)               |

The infrastructure (PostgreSQL, Keycloak, Grafana) comes from [hack/compose.yaml](hack/compose.yaml).

**Dev users** (password = username): `alice`, `bob`, `carol` (platform support), `dave` (email
not verified) and `olga` (platform admin, every searchpe permission). Use `olga` to see everything.

## Option 1: the whole stack in one command

Best to try the platform or to work on the UI only. It builds every backend and starts them from
the built artifacts:

```bash
hack/ui-e2e-stack.sh build      # Maven packages of xhandler and searchpe, cargo build of the engine
hack/ui-e2e-stack.sh up         # compose (PostgreSQL, Keycloak) + fake, engine, xhandler, searchpe

cd ui
cp .env.example .env
npm ci
npm run start:dev               # http://localhost:3000
```

Logs go to `/tmp/xhandler-ui-e2e/*.log`. Stop with `hack/ui-e2e-stack.sh down` (add `--compose`
to stop PostgreSQL and Keycloak too). After changing a backend, run `build` and `up` again.

## Option 2: run each service yourself

Best to work on a backend: `quarkus:dev` reloads code on every request. Use one terminal per
service.

### 1. Infrastructure

```bash
podman compose -f hack/compose.yaml up -d postgres keycloak
podman compose -f hack/compose.yaml up -d lgtm          # optional: Grafana on :3001
```

### 2. xhandler-service

The engine and the SUNAT fake first (from `xhandler/engine`):

```bash
cd xhandler/engine && cargo run                     # engine on 127.0.0.1:8090
cd xhandler/engine && cargo run --bin sunat-fake    # SUNAT fake on 127.0.0.1:8099
```

Then the app on 8080, against the compose database and Keycloak:

```bash
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/xhandler \
QUARKUS_DATASOURCE_USERNAME=xhandler_login QUARKUS_DATASOURCE_PASSWORD=xhandler_login \
QUARKUS_FLYWAY_USERNAME=xhandler_owner QUARKUS_FLYWAY_PASSWORD=xhandler_owner \
QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/xhandler \
XHANDLER_BILLING_DEFAULT_PLAN=enterprise \
  ./mvnw -f xhandler quarkus:dev -pl app
```

- Swagger UI: http://localhost:8080/q/swagger-ui · Dev UI: http://localhost:8080/q/dev-ui
- Without the variables, `quarkus:dev` starts its own PostgreSQL and Keycloak with Dev Services
  (Keycloak on a random port). That is enough for the API alone, but the UI needs Keycloak
  on 8180.
- Traces: start the engine with `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` and open
  Grafana.

### 3. searchpe (optional)

On 8081, with login and permissions on (its defaults turn both off):

```bash
QUARKUS_HTTP_PORT=8081 \
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/searchpe \
QUARKUS_DATASOURCE_USERNAME=searchpe QUARKUS_DATASOURCE_PASSWORD=searchpe \
QUARKUS_OIDC_TENANT_ENABLED=true SEARCHPE_DISABLE_AUTHORIZATION=false \
SEARCHPE_ALLOW_ADVANCEDSEARCH=true \
QUARKUS_OIDC_AUTH_SERVER_URL=http://localhost:8180/realms/xhandler \
QUARKUS_OIDC_CLIENT_ID=searchpe QUARKUS_OIDC_CREDENTIALS_SECRET=secret \
  ./mvnw quarkus:dev -pl searchpe
```

Skip it and remove `SEARCHPE_API_URL` from `ui/.env`: the UI then hides the RUC section.

### 4. UI

```bash
cd ui
cp .env.example .env            # points at :8080, :8081 and Keycloak :8180
npm ci
npm run start:dev               # http://localhost:3000, log in as olga/olga
```

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

- **Port in use**: Keycloak (8180) or PostgreSQL (5432) from another project. Stop it or remap
  the port in `hack/compose.yaml`.
- **Start from a clean database**: `podman compose -f hack/compose.yaml down -v` drops the
  volumes; the next `up` recreates the roles and databases.
- **The UI says a backend is unreachable**: check `ui/.env` and that the backend answers on
  `/q/health/ready`.
