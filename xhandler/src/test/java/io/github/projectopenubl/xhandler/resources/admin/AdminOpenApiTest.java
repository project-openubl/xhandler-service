package io.github.projectopenubl.xhandler.resources.admin;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The admin API has its own OpenAPI document (AdminOpenApi): served to platform admins only, it
 * holds every admin operation and nothing else, and the public document holds none of them.
 */
@QuarkusTest
class AdminOpenApiTest {

    /** Every resource of the admin API; a new one must be added here and carry the admin profile. */
    private static final List<Class<?>> ADMIN_RESOURCES = List.of(AdminResource.class, OseAdminResource.class, PseAdminResource.class,
            SireCalendarAdminResource.class, TenantLifecycleAdminResource.class);

    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete");

    @Test
    void only_platform_admins_read_the_admin_document() {
        given().get("/q/openapi-admin?format=json").then().statusCode(401);
        as("alice").get("/q/openapi-admin?format=json").then().statusCode(403);
        as("olga").get("/q/openapi-admin?format=json").then().statusCode(200);
    }

    @Test
    void the_admin_document_has_every_admin_operation_and_the_public_one_none() throws Exception {
        JsonNode admin = load(as("olga").accept("application/json").get("/q/openapi-admin?format=json").then().statusCode(200).extract().asString());
        JsonNode published = load(given().accept("application/json").get("/q/openapi?format=json").then().statusCode(200).extract().asString());

        Set<String> documented = new TreeSet<>();
        Set<String> ids = new HashSet<>();
        admin.get("paths").properties().forEach(path -> {
            assertTrue(path.getKey().startsWith("/api/admin/v1/"), "not an admin path: " + path.getKey());
            path.getValue().properties().forEach(op -> {
                if (METHODS.contains(op.getKey())) {
                    documented.add(op.getKey().toUpperCase() + " " + path.getKey());
                    JsonNode id = op.getValue().get("operationId");
                    assertNotNull(id, op.getKey() + " " + path.getKey());
                    assertTrue(id.asString().startsWith("admin"), "admin operationIds start with admin: " + id.asString());
                    assertTrue(ids.add(id.asString()), "duplicate operationId " + id.asString());
                    assertEquals("[{\"bearer\":[]}]", String.valueOf(op.getValue().get("security")), path.getKey());
                    assertEquals("#/components/schemas/ApiError",
                            op.getValue().at("/responses/4XX/content/application~1json/schema/$ref").asString(), path.getKey());
                }
            });
        });
        assertEquals(declared(), documented);

        published.get("paths").propertyNames().iterator().forEachRemaining(
                path -> assertFalse(path.startsWith("/api/admin/"), "the admin API stays out of the public document: " + path));
        assertFalse(published.at("/components/schemas").has("AdminTenant"), "admin schemas stay out of the public document");
    }

    @Test
    void every_admin_resource_carries_the_admin_profile() {
        for (Class<?> resource : ADMIN_RESOURCES) {
            Extension extension = resource.getAnnotation(Extension.class);
            assertNotNull(extension, resource.getSimpleName());
            assertEquals(AdminOpenApi.PROFILE_EXTENSION, extension.name(), resource.getSimpleName());
        }
    }

    /** METHOD path of every JAX-RS method of the admin resources, as the OpenAPI document writes them. */
    private static Set<String> declared() {
        Set<String> declared = new TreeSet<>();
        for (Class<?> resource : ADMIN_RESOURCES) {
            String base = resource.getAnnotation(Path.class).value();
            for (Method method : resource.getDeclaredMethods()) {
                String verb = method.isAnnotationPresent(GET.class) ? "GET"
                        : method.isAnnotationPresent(POST.class) ? "POST"
                        : method.isAnnotationPresent(PUT.class) ? "PUT"
                        : method.isAnnotationPresent(DELETE.class) ? "DELETE" : null;
                if (verb == null) {
                    continue;
                }
                Path sub = method.getAnnotation(Path.class);
                String path = sub == null ? base : base + (sub.value().startsWith("/") ? "" : "/") + sub.value();
                declared.add(verb + " " + path);
            }
        }
        return declared;
    }

    private static JsonNode load(String body) throws Exception {
        return new JsonMapper().readTree(body);
    }
}
