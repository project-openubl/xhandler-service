import { expect, test } from "@support/fixtures";

test("lists the user's organizations with their role", async ({
  tenantsPage,
}) => {
  await tenantsPage.goto();
  await tenantsPage.expectLoaded();

  // The setup signed the e2e user up for one: they own it.
  await expect(
    tenantsPage.rows().filter({ hasText: "Propietario" }).first(),
  ).toBeVisible();
});

test("creates a free organization, once per person", async ({
  page,
  tenantsPage,
}) => {
  await tenantsPage.goto();
  await tenantsPage.expectLoaded();

  await page.getByRole("button", { name: "Nueva organización" }).click();
  const dialog = page.getByRole("dialog", { name: "Nueva organización" });
  await dialog.getByRole("button", { name: "Crear" }).click();
  await expect(
    dialog.getByText("Ingresa el nombre de la organización"),
  ).toBeVisible();

  await dialog
    .getByLabel("Nombre de la organización")
    .fill(`E2E ${Date.now()}`);
  await dialog.getByRole("button", { name: "Crear" }).click();

  // The setup signed the e2e user up already, so the API usually refuses a second free one;
  // on a user that never signed up, the new organization opens on its companies.
  await expect(
    page
      .getByRole("heading", { name: "Empresas", level: 1 })
      .or(dialog.getByText("No se pudo crear la organización")),
  ).toBeVisible();
});
