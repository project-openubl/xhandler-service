![CI](https://github.com/project-openubl/xhandler-service/workflows/CI/badge.svg)
[![License](https://img.shields.io/badge/Apache-2.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Project Chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://projectopenubl.zulipchat.com/)

# xhandler-service

Plataforma de comprobantes electrónicos SUNAT: emitir, firmar, enviar, entregar y reportar, como
software propio del emisor, PSE u OSE.

| Directorio | Qué es |
|---|---|
| [xhandler](./xhandler) | Servicio multi-tenant de comprobantes (Quarkus + motor en Rust) |
| [searchpe](./searchpe) | Consulta RUC sobre el padrón reducido de la SUNAT |
| [ui](./ui) | La interfaz web de ambos servicios (React + PatternFly) |
| [operator](./operator) | Operador de Kubernetes: tipos `XHandler` y `Searchpe` |

El comportamiento está definido en [DESIGN.md](./xhandler/docs/DESIGN.md).

## Empezar

Requisitos: Java 25, Rust, Node 22, Go y Podman o Docker.

```bash
podman compose -f hack/compose.yaml up -d                        # PostgreSQL, Keycloak, Grafana
hack/ui-e2e-stack.sh build && hack/ui-e2e-stack.sh up            # todos los servicios
(cd ui && cp .env.example .env && npm ci && npm run start:dev)   # http://localhost:3000
```

Usuarios de prueba: `alice/alice`, `bob/bob`; administradora `olga/olga`.

## Desplegar

Instala el operador desde OperatorHub y crea un `Searchpe` y un `XHandler`
([ejemplos](./operator/config/samples)). Imágenes en `ghcr.io/project-openubl/xhandler-service/`.

## Contribuir

Comandos y convenciones: [AGENTS.md](./AGENTS.md). Los releases se hacen solo con
[release-tools](https://github.com/project-openubl/release-tools#releasing-xhandler-service).

[Documentación](https://project-openubl.github.io) ·
[Discusiones](https://github.com/project-openubl/xhandler-service/discussions) ·
Licencia [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
