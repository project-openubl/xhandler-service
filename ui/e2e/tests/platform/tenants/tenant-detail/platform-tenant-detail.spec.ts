import { readFixtures } from "@support/helpers/tenants";
import { expect, test } from "@support/fixtures";

test("shows an organization, lists its companies on demand and confirms wide actions", async ({
  page,
}) => {
  const tenantId = readFixtures()?.tenantId;
  test.skip(!tenantId, "the setup prepared no organization");

  await page.goto(`/plataforma/organizaciones/${tenantId}`);
  await expect(
    page.getByRole("heading", { name: "Datos", exact: true }),
  ).toBeVisible();
  await expect(page.getByText(tenantId!, { exact: true })).toBeVisible();

  // Listing companies is audited: only on demand.
  const companies = page.getByRole("grid", {
    name: "Tabla de empresas de la organización",
  });
  await expect(companies).toHaveCount(0);
  await page.getByRole("button", { name: "Ver empresas" }).click();
  await expect(
    companies.locator("tbody").getByRole("row").first(),
  ).toBeVisible();

  // Holding every send names the organization; going back changes nothing (other specs send).
  await page.getByRole("button", { name: "Pausar envíos" }).click();
  const confirm = page.getByRole("dialog", {
    name: /Pausar todos los envíos de la organización/,
  });
  await expect(confirm.getByText(/Ningún comprobante de/)).toBeVisible();
  await confirm.getByRole("button", { name: "Volver" }).click();
  await expect(confirm).toHaveCount(0);

  // A bulk retry with no filter answers how many documents it made due.
  await page.getByRole("button", { name: "Reintentar envíos" }).click();
  const retry = page.getByRole("dialog", { name: "Reintentar envíos" });
  await retry.getByRole("button", { name: "Reintentar" }).click();
  await expect(
    page.getByText(/Se reintentar(á|án) \d+ comprobantes?/),
  ).toBeVisible();

  // An active organization has no exit to cancel.
  await expect(
    page.getByRole("button", { name: "Cancelar salida" }),
  ).toHaveAttribute("aria-disabled", "true");
});

test("an unknown organization is a 404 page", async ({ page }) => {
  await page.goto(
    "/plataforma/organizaciones/00000000-0000-0000-0000-000000000000",
  );
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});
