package io.github.projectopenubl.xhandler.resources.admin;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.createCompany;
import static io.github.projectopenubl.xhandler.Api.randomRuc;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The admin reads behind the platform console (DESIGN.md §15): tenants, partners, plans, settings. */
@QuarkusTest
class PlatformConsoleTest {

    @Test
    void only_platform_admins_read_the_platform() {
        for (String path : new String[] {"/api/admin/v1/tenants", "/api/admin/v1/partners", "/api/admin/v1/plans", "/api/admin/v1/billing/issuer"}) {
            as("alice").get(path).then().statusCode(403);
            given().get(path).then().statusCode(401);
        }
    }

    @Test
    void lists_and_finds_tenants_of_any_partner() {
        String name = "Consola plataforma " + UUID.randomUUID();
        UUID partner = newPartner("Socio " + UUID.randomUUID());
        UUID tenant = UUID.fromString(as("alice").body(Map.of("name", name)).post("/api/v1/partners/" + partner + "/tenants")
                .then().statusCode(201).extract().path("id"));
        String ruc = randomRuc();
        UUID company = createCompany("alice", tenant, ruc);

        // By a piece of its name, any case; and by its id
        as("olga").queryParam("q", name.substring(10).toUpperCase()).get("/api/admin/v1/tenants").then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(tenant.toString()))
                .body("items[0].partner_id", equalTo(partner.toString()))
                .body("items[0].plan", equalTo("free"))
                .body("items[0].plan_source", equalTo("default"))
                .body("items[0].status", equalTo("active"))
                .body("items[0].companies", equalTo(1))
                .body("limit", equalTo(50));
        as("olga").queryParam("q", tenant.toString()).get("/api/admin/v1/tenants").then().statusCode(200)
                .body("items.id", equalTo(java.util.List.of(tenant.toString())));
        as("olga").queryParam("limit", 2).get("/api/admin/v1/tenants").then().statusCode(200)
                .body("items.size()", lessThanOrEqualTo(2));

        as("olga").get("/api/admin/v1/tenants/" + tenant).then().statusCode(200)
                .body("name", equalTo(name))
                .body("partner_name", notNullValue())
                .body("sending_paused", equalTo(false))
                .body("audit_hold", equalTo(false));
        as("olga").get("/api/admin/v1/tenants/" + UUID.randomUUID()).then().statusCode(404);

        // Its companies, to act on them; the tenant sees the platform looked
        as("olga").get("/api/admin/v1/tenants/" + tenant + "/companies").then().statusCode(200)
                .body("id", equalTo(java.util.List.of(company.toString())))
                .body("[0].ruc", equalTo(ruc))
                .body("[0].ose_obligated", equalTo(false))
                .body("[0].sending_paused", equalTo(false));
        as("alice").get("/api/v1/tenants/" + tenant + "/audit?limit=200").then().statusCode(200)
                .body("items.action", hasItem("tenant.companies_listed"));

        // A plan set by hand shows as the admin's
        as("olga").body(Map.of("plan", "business")).put("/api/admin/v1/tenants/" + tenant + "/plan").then().statusCode(200);
        as("olga").get("/api/admin/v1/tenants/" + tenant).then().statusCode(200)
                .body("plan", equalTo("business"))
                .body("plan_source", equalTo("admin"));
    }

    @Test
    void lists_partners_with_their_members_and_tenants() {
        String name = "Socio consola " + UUID.randomUUID();
        UUID partner = newPartner(name);
        as("alice").body(Map.of("name", "Cliente de " + name)).post("/api/v1/partners/" + partner + "/tenants").then().statusCode(201);

        as("olga").get("/api/admin/v1/partners").then().statusCode(200)
                .body("find { it.id == '" + partner + "' }.name", equalTo(name))
                .body("find { it.id == '" + partner + "' }.status", equalTo("active"))
                .body("find { it.id == '" + partner + "' }.members", equalTo(1))
                .body("find { it.id == '" + partner + "' }.tenants", equalTo(1));

        as("olga").get("/api/admin/v1/partners/" + partner + "/members").then().statusCode(200)
                .body("subject", equalTo(java.util.List.of(subject("alice"))))
                .body("[0].kind", equalTo("user"))
                .body("[0].role", equalTo("admin"))
                .body("[0].created_at", notNullValue());
        as("olga").get("/api/admin/v1/partners/" + UUID.randomUUID() + "/members").then().statusCode(404);

        // Defaults: a person, as admin
        as("olga").body(Map.of("subject", "someone-else")).put("/api/admin/v1/partners/" + partner + "/members").then().statusCode(200)
                .body("kind", equalTo("user"))
                .body("role", equalTo("admin"));
        as("olga").delete("/api/admin/v1/partners/" + partner + "/members/user/someone-else").then().statusCode(204);
    }

    @Test
    void reads_the_plans_and_platform_settings() {
        as("olga").get("/api/admin/v1/plans").then().statusCode(200)
                .body("code", hasItem("free"))
                .body("code", hasItem("enterprise"))
                .body("[0].code", equalTo("free"));
        // Unset, or whatever another test set: always both or neither
        Map<String, String> issuer = as("olga").get("/api/admin/v1/billing/issuer").then().statusCode(200).extract().jsonPath().getMap("$");
        org.junit.jupiter.api.Assertions.assertEquals(issuer.get("tenant_id") == null, issuer.get("company_id") == null, issuer.toString());
        // The platform certificate: 404 until imported, never the key
        int pse = as("olga").get("/api/admin/v1/pse/certificate").then().extract().statusCode();
        org.junit.jupiter.api.Assertions.assertTrue(pse == 200 || pse == 404, "pse certificate: " + pse);
    }

    private static UUID newPartner(String name) {
        String partner = as("olga").body(Map.of("name", name)).post("/api/admin/v1/partners").then().statusCode(200)
                .body("name", equalTo(name)).extract().path("id");
        as("olga").body(Map.of("kind", "user", "subject", subject("alice"), "role", "admin"))
                .put("/api/admin/v1/partners/" + partner + "/members").then().statusCode(200);
        return UUID.fromString(partner);
    }

    private static String subject(String user) {
        String token = new KeycloakTestClient().getAccessToken(user);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return new JsonMapper().readTree(payload).get("sub").asString();
    }
}
