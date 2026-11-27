import { expect, type Page } from "@playwright/test";

/** The dev realm's olga holds every searchpe permission (hack/keycloak). */
export const E2E_USERNAME = process.env.E2E_USERNAME ?? "olga";
export const E2E_PASSWORD = process.env.E2E_PASSWORD ?? "olga";

/**
 * Logs in through Keycloak's form, as a user would. The Keycloak session cookie is what the
 * saved storage state keeps; the app's tokens live in sessionStorage and are renewed from it.
 */
export const authenticate = async (page: Page) =>
  authenticateAs(page, { username: E2E_USERNAME, password: E2E_PASSWORD });

/** Logs another dev-realm user in, in a context of their own (e.g. carol, who isn't an admin). */
export const authenticateAs = async (
  page: Page,
  { username, password }: { username: string; password: string },
) => {
  await page.goto("/");
  // By role and name: Keycloak 26's password field (with its "Show password" toggle) isn't
  // reachable through its <label>.
  await page.getByRole("textbox", { name: /username|usuario/i }).fill(username);
  await page
    .getByRole("textbox", { name: /^password$|^contraseña$/i })
    .fill(password);
  await page.getByRole("button", { name: /sign in|iniciar sesión/i }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: /^Hola,/ }),
  ).toBeVisible();
};
