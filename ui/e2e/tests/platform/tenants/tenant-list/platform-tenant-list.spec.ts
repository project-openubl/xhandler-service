import { authenticateAs } from "@support/helpers/auth";
import { expect, test } from "@support/fixtures";
import { PlatformTenantsPage } from "@support/pom/platformPages";

test("platform admins list every organization and search them by name", async ({
  page,
}) => {
  const tenants = new PlatformTenantsPage(page);
  await page.goto("/plataforma");
  await expect(page).toHaveURL(/\/plataforma\/organizaciones/);
  await tenants.expectLoaded();
  await expect(
    page.getByRole("link", { name: "Organizaciones" }).last(),
  ).toBeVisible();

  // The setup signed the e2e user up: at least that organization exists.
  await expect(tenants.rows().first()).toBeVisible();

  await page.getByPlaceholder("Filtrar por nombre").fill("Nadie se llama así");
  await expect(page).toHaveURL(/nombre=Nadie/);
  await expect(
    page.getByRole("heading", { name: "No se encontraron resultados" }),
  ).toBeVisible();
});

test("a user without platform-admin sees no Plataforma", async ({
  browser,
}) => {
  // carol is platform support, not a platform admin (hack/keycloak).
  const context = await browser.newContext({ storageState: undefined });
  const page = await context.newPage();
  await authenticateAs(page, { username: "carol", password: "carol" });

  await expect(page.getByRole("button", { name: "Plataforma" })).toHaveCount(0);
  await page.goto("/plataforma/organizaciones");
  await expect(
    page.getByRole("heading", { name: "403: Acceso denegado" }),
  ).toBeVisible();
  await context.close();
});
