package io.github.projectopenubl.xhandler.resources.admin;

/**
 * The admin API has its own OpenAPI document (DESIGN.md §15, §26.2): every admin resource carries
 * {@code @Extension(name = PROFILE_EXTENSION, value = "")}, the public document excludes the
 * {@value #PROFILE} profile ({@code quarkus.smallrye-openapi.scan-exclude-profiles}) and the
 * {@code admin} document includes only it. The admin document is stored at build time in
 * {@code target/openapi-admin/} (the UI's admin client is generated from it) and served at
 * {@code /q/openapi-admin} to platform admins only; it never reaches the public SDKs.
 */
public final class AdminOpenApi {

    public static final String PROFILE = "admin";

    /** SmallRye OpenAPI's operation profile marker: {@code x-smallrye-profile-<profile>}. */
    public static final String PROFILE_EXTENSION = "x-smallrye-profile-" + PROFILE;

    private AdminOpenApi() {}
}
