# Searchpe

Consulta RUC sobre el `padrón reducido` de la SUNAT. Sirve solo su API; la interfaz es la UI única
del repositorio ([ui/](../ui)).

```bash
./mvnw quarkus:dev -pl searchpe     # desde la raíz: http://localhost:8081
```

Dev Services levantan PostgreSQL y comparten el Keycloak de xhandler-service (puerto 8180, con
login y permisos activos). Ver [CONTRIBUTING.md](../CONTRIBUTING.md).

Despliegue: con el operador, tipo `Searchpe` ([operator/](../operator)).

Licencia: [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
