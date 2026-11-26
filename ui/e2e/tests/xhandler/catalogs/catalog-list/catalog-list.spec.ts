import { expect, test } from "@support/fixtures";
import { readFixtures } from "@support/helpers/tenants";

test("adds, finds, edits and deletes a vehicle in a company's catalog", async ({
  page,
}) => {
  const fixtures = readFixtures();
  test.skip(!fixtures, "the setup prepared no company");
  const { tenantId, companyId } = fixtures!;

  // The company's pages link the catalogs from the sidebar.
  await page.goto(`/organizaciones/${tenantId}/empresas/${companyId}`);
  await page.getByRole("link", { name: "Catálogos" }).click();
  await expect(
    page.getByRole("heading", { name: "Catálogos", level: 1 }),
  ).toBeVisible();

  await page.getByRole("tab", { name: "Vehículos" }).click();
  await expect(page).toHaveURL(/catalogo=vehiculos/);

  // Random plates: the database outlives the run.
  const plate = `E2E${Date.now().toString().slice(-6)}`;
  await page.getByRole("button", { name: "Nuevo vehículo" }).click();
  let dialog = page.getByRole("dialog", { name: "Nuevo vehículo" });
  await dialog.getByRole("button", { name: "Agregar" }).click();
  await expect(dialog.getByText("Ingresa placa")).toBeVisible();
  await dialog
    .getByRole("textbox", { name: "Placa" })
    .fill(plate.toLowerCase());
  await dialog.getByRole("button", { name: "Agregar" }).click();
  await expect(dialog).toBeHidden();

  await page
    .getByPlaceholder("Buscar por código, nombre, documento o placa")
    .fill(plate);
  const table = page.getByRole("grid", { name: "Tabla de vehículos" });
  const row = table.getByRole("row").filter({ hasText: plate });
  await expect(row).toHaveCount(1);

  // The same plate again is the same key: the service refuses it.
  await page.getByRole("button", { name: "Nuevo vehículo" }).click();
  dialog = page.getByRole("dialog", { name: "Nuevo vehículo" });
  await dialog.getByRole("textbox", { name: "Placa" }).fill(plate);
  await dialog.getByRole("button", { name: "Agregar" }).click();
  await expect(
    dialog.getByText("No se pudo guardar el vehículo"),
  ).toBeVisible();
  await dialog.getByRole("button", { name: "Cancelar" }).click();

  await row.getByRole("button").last().click();
  await page.getByRole("menuitem", { name: "Editar" }).click();
  dialog = page.getByRole("dialog", { name: "Editar vehículo" });
  await dialog.getByRole("textbox", { name: "Placa" }).fill(`${plate}X`);
  await dialog.getByRole("button", { name: "Guardar" }).click();
  await expect(dialog).toBeHidden();
  const edited = table.getByRole("row").filter({ hasText: `${plate}X` });
  await expect(edited).toHaveCount(1);

  await edited.getByRole("button").last().click();
  await page.getByRole("menuitem", { name: "Eliminar" }).click();
  await page
    .getByRole("dialog", { name: "¿Eliminar el vehículo?" })
    .getByRole("button", { name: "Eliminar" })
    .click();
  await expect(edited).toHaveCount(0);
});
