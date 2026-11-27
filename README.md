![CI](https://github.com/project-openubl/xhandler-service/workflows/CI/badge.svg)
[![License](https://img.shields.io/badge/Apache-2.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Project Chat](https://img.shields.io/badge/zulip-join_chat-brightgreen.svg)](https://projectopenubl.zulipchat.com/)

# xhandler-service

Plataforma de comprobantes electrónicos SUNAT: emitir, firmar, enviar, entregar y reportar, como
software propio del emisor o como PSE.

| Directorio | Qué es |
|---|---|
| [xhandler](./xhandler) | Servicio multi-tenant de comprobantes (Quarkus + motor en Rust) |
| [searchpe](./searchpe) | Consulta RUC sobre el padrón reducido de la SUNAT |
| [ui](./ui) | La interfaz web de ambos servicios (React + PatternFly) |
| [operator](./operator) | Operador de Kubernetes: tipos `XHandler` y `Searchpe` |

El comportamiento está definido en [DESIGN.md](./docs/DESIGN.md).

## Empezar

Requisitos: Java 25, Rust, Node 22 y Podman o Docker. Desde la raíz, una terminal por línea (antes,
una vez: `(cd ui && cp .env.example .env && npm ci)`):

```bash
(cd xhandler/engine && cargo run)                   # motor             :8090
(cd xhandler/engine && cargo run --bin sunat-fake)  # SUNAT simulada    :8099
(cd xhandler && ../mvnw quarkus:dev)                # xhandler-service  :8080
(cd searchpe && ../mvnw quarkus:dev)                # searchpe          :8081
(cd ui && npm run start:dev)                        # UI                :3000
```

Abre http://localhost:3000 e ingresa como `olga` / `olga`. Detalles en
[CONTRIBUTING.md](./CONTRIBUTING.md).

## Desplegar

Instala el operador desde OperatorHub y crea un `Searchpe` y un `XHandler`
([ejemplos](./operator/config/samples)). Imágenes en `ghcr.io/project-openubl/xhandler-service/`.

## Contribuir

Cómo levantar el entorno: [CONTRIBUTING.md](./CONTRIBUTING.md). Comandos y convenciones:
[AGENTS.md](./AGENTS.md). Los releases se hacen solo con
[release-tools](https://github.com/project-openubl/release-tools#releasing-xhandler-service).

[Documentación](https://project-openubl.github.io) ·
[Discusiones](https://github.com/project-openubl/xhandler-service/discussions) ·
Licencia [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
