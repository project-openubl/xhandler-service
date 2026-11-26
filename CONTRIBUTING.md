# Contributing

How to run the platform on your machine. For conventions, tests and CI, read
[AGENTS.md](AGENTS.md).

## Requirements

- Java 25 (Maven comes with `./mvnw`)
- Rust: `rustup` installs the pinned toolchain. The engine also needs `libxml2-dev`,
  `libclang-dev` and `pkg-config`
- Node 22
- Podman or Docker. With Podman, enable its socket for Testcontainers:
  `systemctl --user enable --now podman.socket` and
  `export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock`

## Start the app

Once, to set up the UI:

```bash
(cd ui && cp .env.example .env && npm ci)
```

Then from the repository root, one terminal per line:

```bash
(cd xhandler/engine && cargo run)                   # engine            :8090
(cd xhandler/engine && cargo run --bin sunat-fake)  # SUNAT fake        :8099
(cd xhandler && ../mvnw quarkus:dev)                # xhandler-service  :8080
(cd searchpe && ../mvnw quarkus:dev)                # searchpe          :8081
(cd ui && npm run start:dev)                        # UI                :3000
```

Open http://localhost:3000 and log in as `olga` / `olga`.

Nothing else to install or configure: Quarkus [Dev Services](https://quarkus.io/guides/dev-services)
start PostgreSQL, Keycloak (on 8180, shared by both apps) and Mailpit in containers. Java code
reloads on every request; the UI reloads on save.

Start xhandler-service before searchpe and stop it last: the Keycloak container belongs to the
app that started it.

## Useful addresses

| What          | Where                                  |
| ------------- | -------------------------------------- |
| UI            | http://localhost:3000                  |
| Swagger UI    | http://localhost:8080/q/swagger-ui     |
| Dev UI        | http://localhost:8080/q/dev-ui         |
| Emails sent   | http://localhost:8080/q/mailpit/       |
| Keycloak      | http://localhost:8180 (`admin`/`admin`) |

**Dev users** (password = username): `olga` (platform admin, sees everything), `alice`, `bob`,
`carol` (platform support) and `dave` (email not verified).

## First steps in the app

1. Create an organization, then a company. It starts on the SUNAT fake.
2. Issue a factura: the fake accepts it.
3. To try SUNAT beta, switch the company's environment. Never production in development.

## Before opening a pull request

- Run the checks of what you changed ([AGENTS.md § Commands](AGENTS.md#commands)).
- Use Conventional Commits (`feat(ui): …`, `fix: …`).
- A behavior change updates [docs/DESIGN.md](docs/DESIGN.md) or an ADR in the same commit.

## Troubleshooting

- **Port 8180 in use**: another Keycloak is running (for example `hack/compose.yaml`); stop it.
- **The UI says a backend is unreachable**: check that it answers on `/q/health/ready`.
- **Traces**: add `-Dquarkus.observability.enabled=true` to xhandler-service to get Grafana on
  http://localhost:3001, and start the engine with
  `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318`.
