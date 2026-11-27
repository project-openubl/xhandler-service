package io.github.projectopenubl.xhandler.resources.exceptions;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

/**
 * Completes the generated OpenAPI document (DESIGN.md §12) for SDK generators:
 *
 * <ul>
 *   <li>every operation under {@code /api/} except the public ones requires the bearer scheme;
 *   <li>{@code ApiError} is a reusable schema, referenced by the {@code 4XX} and {@code 5XX}
 *       responses of every operation under {@code /api/} (an OIDC {@code 401} has no body);
 *   <li>a page schema is named after its items ({@code Page} + the item schema, e.g.
 *       {@code PageDocumentView}), not after Java class names, so the names SDKs see stay stable.
 * </ul>
 *
 * <p>Runs at build time on both documents, the public one and the admin one ({@code
 * AdminOpenApi}), so the stored documents ({@code target/openapi}, {@code target/openapi-admin})
 * and what {@code /q/openapi} and {@code /q/openapi-admin} serve are the same.
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD, documentNames = OpenApiFilter.FILTER_RUN_FOR_ANY_DOCUMENT)
public class OpenApiDocumentFilter implements OASFilter {

    static final String SECURITY_SCHEME = "bearer";
    static final String ERROR_SCHEMA = "ApiError";

    /** Paths without login: keep in step with {@code quarkus.http.auth.permission.public.paths}. */
    static final List<String> PUBLIC_PREFIXES = List.of("/api/v1/status", "/api/v1/public/");

    static boolean isPublic(String path) {
        return !path.startsWith("/api/") || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        Components components = openAPI.getComponents();
        if (components == null) {
            components = OASFactory.createComponents();
            openAPI.setComponents(components);
        }
        components.addSchema(ERROR_SCHEMA, errorSchema());
        renamePageSchemas(openAPI);
        if (openAPI.getPaths() == null) {
            return;
        }
        for (Map.Entry<String, PathItem> path : openAPI.getPaths().getPathItems().entrySet()) {
            if (!path.getKey().startsWith("/api/")) {
                continue;
            }
            boolean secured = !isPublic(path.getKey());
            for (Operation operation : path.getValue().getOperations().values()) {
                if (secured) {
                    operation.setSecurity(List.of(OASFactory.createSecurityRequirement().addScheme(SECURITY_SCHEME)));
                }
                addErrorResponses(operation, secured);
            }
        }
    }

    private static final String SCHEMA_REF = "#/components/schemas/";

    private static void renamePageSchemas(OpenAPI openAPI) {
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        if (schemas == null) {
            return;
        }
        Map<String, String> renames = new LinkedHashMap<>();
        for (Map.Entry<String, Schema> schema : schemas.entrySet()) {
            Map<String, Schema> properties = schema.getValue().getProperties();
            Schema items = properties == null ? null : properties.get("items");
            if (!schema.getKey().startsWith("Page") || items == null || items.getItems() == null || items.getItems().getRef() == null
                    || !items.getItems().getRef().startsWith(SCHEMA_REF)) {
                continue;
            }
            String name = "Page" + items.getItems().getRef().substring(SCHEMA_REF.length());
            if (!name.equals(schema.getKey()) && !schemas.containsKey(name)) {
                renames.put(schema.getKey(), name);
            }
        }
        if (renames.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            Schema schema = schemas.get(rename.getKey());
            openAPI.getComponents().removeSchema(rename.getKey());
            openAPI.getComponents().addSchema(rename.getValue(), schema);
        }
        for (Schema schema : openAPI.getComponents().getSchemas().values()) {
            rewriteRefs(schema, renames);
        }
        if (openAPI.getPaths() == null) {
            return;
        }
        for (PathItem path : openAPI.getPaths().getPathItems().values()) {
            for (Operation operation : path.getOperations().values()) {
                if (operation.getParameters() != null) {
                    operation.getParameters().forEach(p -> rewriteRefs(p.getSchema(), renames));
                }
                if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                    operation.getRequestBody().getContent().getMediaTypes().values().forEach(m -> rewriteRefs(m.getSchema(), renames));
                }
                if (operation.getResponses() != null) {
                    for (APIResponse response : operation.getResponses().getAPIResponses().values()) {
                        if (response.getContent() != null) {
                            response.getContent().getMediaTypes().values().forEach(m -> rewriteRefs(m.getSchema(), renames));
                        }
                    }
                }
            }
        }
    }

    private static void rewriteRefs(Schema schema, Map<String, String> renames) {
        if (schema == null) {
            return;
        }
        String ref = schema.getRef();
        if (ref != null && ref.startsWith(SCHEMA_REF) && renames.containsKey(ref.substring(SCHEMA_REF.length()))) {
            schema.setRef(SCHEMA_REF + renames.get(ref.substring(SCHEMA_REF.length())));
        }
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(s -> rewriteRefs(s, renames));
        }
        rewriteRefs(schema.getItems(), renames);
        rewriteRefs(schema.getAdditionalPropertiesSchema(), renames);
        for (List<Schema> composed : java.util.Arrays.asList(schema.getAllOf(), schema.getAnyOf(), schema.getOneOf())) {
            if (composed != null) {
                composed.forEach(s -> rewriteRefs(s, renames));
            }
        }
    }

    private static void addErrorResponses(Operation operation, boolean secured) {
        APIResponses responses = operation.getResponses();
        if (responses == null) {
            responses = OASFactory.createAPIResponses();
            operation.setResponses(responses);
        }
        if (secured && !responses.hasAPIResponse("401")) {
            responses.addAPIResponse("401", OASFactory.createAPIResponse()
                    .description("Sin token o con un token inválido (sin cuerpo)."));
        }
        if (!responses.hasAPIResponse("4XX")) {
            responses.addAPIResponse("4XX", errorResponse("Error de la solicitud: `kind` estable, `message` en español."));
        }
        if (!responses.hasAPIResponse("5XX")) {
            responses.addAPIResponse("5XX", errorResponse("Error del servicio (p. ej. 502 cuando falla el motor)."));
        }
    }

    private static APIResponse errorResponse(String description) {
        return OASFactory.createAPIResponse()
                .description(description)
                .content(OASFactory.createContent().addMediaType("application/json", OASFactory.createMediaType()
                        .schema(OASFactory.createSchema().ref(ERROR_SCHEMA))));
    }

    private static Schema errorSchema() {
        return OASFactory.createSchema()
                .type(List.of(Schema.SchemaType.OBJECT))
                .description("Error de la API (AGENTS.md: `{ kind, message, detail }`).")
                .addProperty("kind", OASFactory.createSchema()
                        .type(List.of(Schema.SchemaType.STRING))
                        .description("Código estable del error, p. ej. `not_found`, `invalid_request`, `invalid_document`."))
                .addProperty("message", OASFactory.createSchema()
                        .type(List.of(Schema.SchemaType.STRING))
                        .description("Mensaje para personas, en español."))
                .addProperty("detail", OASFactory.createSchema()
                        .type(List.of(Schema.SchemaType.STRING, Schema.SchemaType.NULL))
                        .description("Detalle opcional (p. ej. el campo inválido o el código SUNAT)."))
                .required(List.of("kind", "message", "detail"));
    }
}
