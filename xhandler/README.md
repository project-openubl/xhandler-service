# xhandler-service

Multi-tenant SaaS for SUNAT (Peru) electronic documents: create, sign, validate, send,
track, deliver and report them. It works as the issuer's own software and as a PSE.
Built with Quarkus on top of
[xhandler-rust](https://github.com/project-openubl/xhandler-rust).

- Design: [docs/DESIGN.md](../docs/DESIGN.md)
- Regulatory research: [docs/research/](../docs/research/README.md)
- Contributor and agent guide: [AGENTS.md](../AGENTS.md)

## Run it locally

See [CONTRIBUTING.md](../CONTRIBUTING.md): the engine and the SUNAT fake with `cargo run`, then
`(cd xhandler && ../mvnw quarkus:dev)` from the root. Dev Services start PostgreSQL, Keycloak on
8180 and Mailpit. To call the API directly:

```bash
TOKEN=$(curl -s http://localhost:8180/realms/xhandler/protocol/openid-connect/token \
  -d grant_type=password -d client_id=xhandler-service -d client_secret=secret \
  -d username=alice -d password=alice | jq -r .access_token)
# POST /api/v1/tenants → POST /api/v1/tenants/{t}/companies → POST …/companies/{c}/documents
```

- Swagger UI: http://localhost:8080/q/swagger-ui · Health: http://localhost:8080/q/health ·
  Metrics: http://localhost:8080/q/metrics
- End-to-end check against SUNAT beta (packaged app, engine, trace): `../hack/xhandler-e2e.sh`

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
