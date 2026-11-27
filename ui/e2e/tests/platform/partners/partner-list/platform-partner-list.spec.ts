import { expect, test } from "@support/fixtures";
import { PlatformPartnersPage } from "@support/pom/platformPages";

test("creates a partner and opens it", async ({ page }) => {
  const partners = new PlatformPartnersPage(page);
  await partners.goto();
  await partners.expectLoaded();

  await page.getByRole("button", { name: "Nuevo socio" }).click();
  const dialog = page.getByRole("dialog", { name: "Nuevo socio" });
  await dialog.getByRole("button", { name: "Crear" }).click();
  await expect(dialog.getByText("Ingresa el nombre del socio")).toBeVisible();

  const name = `Socio E2E ${Date.now()}`;
  await dialog.getByLabel("Nombre del socio").fill(name);
  await dialog.getByRole("button", { name: "Crear" }).click();

  await expect(page.getByRole("heading", { level: 1, name })).toBeVisible();
  await expect(page).toHaveURL(/\/plataforma\/socios\/[0-9a-f-]{36}$/);
  await partners.goto();
  await expect(partners.table.getByRole("link", { name })).toBeVisible();
});
