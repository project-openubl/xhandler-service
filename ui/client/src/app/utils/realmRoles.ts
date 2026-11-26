/**
 * The realm roles in a Keycloak access token (`realm_access.roles`), read without verifying
 * it: only to show or hide platform screens (ADR 0013); the APIs verify the token and decide.
 */
export const realmRolesOf = (accessToken: string | undefined): string[] => {
  const payload = accessToken?.split(".")[1];
  if (!payload) return [];
  try {
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const json = new TextDecoder().decode(
      Uint8Array.from(atob(base64), (char) => char.charCodeAt(0)),
    );
    const roles = (JSON.parse(json) as { realm_access?: { roles?: unknown } })
      .realm_access?.roles;
    return Array.isArray(roles)
      ? roles.filter((role): role is string => typeof role === "string")
      : [];
  } catch {
    return [];
  }
};
