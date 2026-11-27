import { expect, test } from "@support/fixtures";

test("an unknown draft is a 404 page", async ({ page, tenantsPage }) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(
    `/organizaciones/${tenantId}/borradores/00000000-0000-0000-0000-000000000000`,
  );
  await expect(
    page.getByRole("heading", { name: "404: La página no existe" }),
  ).toBeVisible();
});

test("defaults a dollar draft to SUNAT's rate and looks up the buyer", async ({
  page,
  tenantsPage,
}) => {
  const tenantId = await tenantsPage.firstTenantId();
  test.skip(!tenantId, "the e2e user belongs to no organization");

  await page.goto(`/organizaciones/${tenantId}/borradores`);
  await page.getByRole("button", { name: "Nuevo borrador" }).click();
  const dialog = page.getByRole("dialog", { name: "Nuevo borrador" });
  const company = dialog.getByRole("combobox", { name: "Empresa" });
  await expect(company).toBeEnabled();
  test.skip(
    (await company.getByRole("option").first().textContent()) ===
      "No hay empresas",
    "the organization has no company",
  );
  await dialog
    .getByRole("combobox", { name: "Tipo de comprobante" })
    .selectOption({ label: "Factura" });
  await dialog.getByRole("button", { name: "Crear borrador" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Factura: borrador" }),
  ).toBeVisible();

  // Soles have no exchange rate; dollars show SUNAT's of the day, or ask for one.
  const rate = page.getByRole("textbox", { name: "Tipo de cambio" });
  await expect(rate).toHaveCount(0);
  await page.getByRole("combobox", { name: "Moneda" }).selectOption("USD");
  await expect(rate).toBeVisible();
  await expect(
    page
      .getByText(/Vacío: la venta SUNAT del/)
      .or(page.getByText(/SUNAT no publicó un tipo de cambio/)),
  ).toBeVisible();

  // The lookup takes a RUC or a DNI; without a provider configured the API says so in the form.
  const lookup = page.getByRole("button", { name: "Consultar RUC/DNI" });
  await page
    .getByRole("combobox", { name: "Tipo de documento" })
    .selectOption({ label: "Pasaporte" });
  await expect(lookup).toHaveAttribute("aria-disabled", "true");
  await page
    .getByRole("combobox", { name: "Tipo de documento" })
    .selectOption({ label: "RUC" });
  await page
    .getByRole("textbox", { name: "Número de documento", exact: true })
    .fill("20123456789");
  await lookup.click();
  const name = page.getByRole("textbox", {
    name: "Nombre o razón social",
    exact: true,
  });
  const lookupError = page.getByText("No se pudo consultar el documento");
  await expect
    .poll(
      async () =>
        (await lookupError.count()) > 0 || (await name.inputValue()) !== "",
    )
    .toBe(true);

  await page.getByRole("button", { name: "Eliminar borrador" }).click();
  await page
    .getByRole("dialog", { name: "¿Eliminar el borrador?" })
    .getByRole("button", { name: "Eliminar" })
    .click();
  await expect(page.getByText("Se eliminó el borrador")).toBeVisible();
});
