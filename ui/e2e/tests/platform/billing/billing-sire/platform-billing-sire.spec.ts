import { readFixtures } from "@support/helpers/tenants";
import { expect, test } from "@support/fixtures";

test("picks the billing issuer's company from the organization's companies", async ({
  page,
}) => {
  const tenantId = readFixtures()?.tenantId;
  test.skip(!tenantId, "the setup prepared no organization");

  await page.goto("/plataforma/facturacion");
  await expect(
    page.getByRole("heading", { level: 1, name: "Facturación y SIRE" }),
  ).toBeVisible();

  // The issuer is the platform's setting: the dialog is checked, never saved here.
  await page.getByRole("button", { name: "Cambiar empresa emisora" }).click();
  const dialog = page.getByRole("dialog", { name: "Cambiar empresa emisora" });
  await dialog.getByLabel("Organización").fill("no-es-un-id");
  await dialog.getByRole("button", { name: "Guardar" }).click();
  await expect(
    dialog.getByText("El identificador de la organización no es válido"),
  ).toBeVisible();
  await dialog.getByLabel("Organización").fill(tenantId!);
  await expect(
    dialog.getByRole("option", { name: /E2E \d{11} SAC/ }).first(),
  ).toBeAttached();
  await dialog.getByRole("button", { name: "Cancelar" }).click();
});

test("loads SIRE due dates and filters them by period", async ({ page }) => {
  await page.goto("/plataforma/facturacion");

  await page.getByRole("button", { name: "Cargar cronograma" }).click();
  const dialog = page.getByRole("dialog", { name: "Cargar cronograma" });
  await dialog.locator("textarea").fill("209913,0,2100-01-12");
  await dialog.getByRole("button", { name: "Cargar", exact: true }).click();
  await expect(
    dialog.getByText("Línea 1: el periodo debe ser AAAAMM"),
  ).toBeVisible();

  // A period far ahead, so no deadline notice is ever due for it.
  await dialog
    .locator("textarea")
    .fill("209912,0,2100-01-12\n209912,1,2100-01-13");
  await dialog.getByRole("button", { name: "Cargar", exact: true }).click();
  await expect(
    page.getByText("Se cargaron 2 fechas del cronograma"),
  ).toBeVisible();

  await page.getByPlaceholder("Filtrar por periodo (AAAAMM)").fill("209912");
  const calendar = page.getByRole("grid", {
    name: "Tabla del cronograma de SIRE",
  });
  await expect(
    calendar.getByRole("row", { name: /209912\s+1\s+13\/01\/2100/ }),
  ).toBeVisible();
});
