import { expect, test } from "@support/fixtures";
import { PlatformPartnersPage } from "@support/pom/platformPages";

test("adds a member to a partner, changes its role and removes it", async ({
  page,
}) => {
  // A partner of its own, so the members table starts empty.
  const partners = new PlatformPartnersPage(page);
  await partners.goto();
  await page.getByRole("button", { name: "Nuevo socio" }).click();
  const name = `Socio E2E ${Date.now()}`;
  await page.getByRole("dialog").getByLabel("Nombre del socio").fill(name);
  await page.getByRole("dialog").getByRole("button", { name: "Crear" }).click();
  await expect(page.getByRole("heading", { level: 1, name })).toBeVisible();

  const members = page.getByRole("grid", {
    name: "Tabla de miembros del socio",
  });
  await expect(
    page.getByRole("heading", { name: "El socio aún no tiene miembros" }),
  ).toBeVisible();

  await page.getByRole("button", { name: "Agregar miembro" }).click();
  const add = page.getByRole("dialog", { name: "Agregar miembro" });
  await add.getByLabel("Tipo").selectOption("client");
  await add.getByLabel("Identificador").fill("acme-erp");
  await add.getByLabel("Rol").selectOption("viewer");
  await add.getByRole("button", { name: "Agregar" }).click();
  const row = members.getByRole("row", { name: /acme-erp/ });
  await expect(row.getByText("Lector")).toBeVisible();

  await row.getByRole("button", { name: "Kebab toggle" }).click();
  await page.getByRole("menuitem", { name: "Cambiar rol" }).click();
  const change = page.getByRole("dialog", { name: "Cambiar rol" });
  await change.getByLabel("Rol").selectOption("admin");
  await change.getByRole("button", { name: "Cambiar rol" }).click();
  await expect(row.getByText("Administrador")).toBeVisible();

  await row.getByRole("button", { name: "Kebab toggle" }).click();
  await page.getByRole("menuitem", { name: "Quitar" }).click();
  await page
    .getByRole("dialog", { name: "¿Quitar al miembro?" })
    .getByRole("button", { name: "Quitar" })
    .click();
  await expect(
    page.getByRole("heading", { name: "El socio aún no tiene miembros" }),
  ).toBeVisible();
});

test("an unknown partner is a 404 page", async ({ page }) => {
  await page.goto("/plataforma/socios/00000000-0000-0000-0000-000000000000");
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});
